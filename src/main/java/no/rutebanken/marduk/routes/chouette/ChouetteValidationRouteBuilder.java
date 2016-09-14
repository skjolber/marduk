package no.rutebanken.marduk.routes.chouette;

import static no.rutebanken.marduk.Constants.CHOUETTE_REFERENTIAL;
import static no.rutebanken.marduk.Constants.JSON_PART;
import static no.rutebanken.marduk.Constants.PROVIDER_ID;

import java.util.UUID;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.ChouetteInfo;
import no.rutebanken.marduk.routes.chouette.json.ValidationParameters;
import no.rutebanken.marduk.routes.status.Status;
import no.rutebanken.marduk.routes.status.Status.Action;
import no.rutebanken.marduk.routes.status.Status.State;

/**
 * Runs validation in Chouette
 */
@Component
public class ChouetteValidationRouteBuilder extends AbstractChouetteRouteBuilder {

    @Value("${chouette.url}")
    private String chouetteUrl;

    @Override
    public void configure() throws Exception {
        super.configure();

        from("activemq:queue:ChouetteValidationQueue?transacted=true&maxConcurrentConsumers=3").streamCaching()
                .log(LoggingLevel.INFO,correlation()+"Starting Chouette validation")
                .setHeader(Constants.FILE_NAME,constant("None"))
                .process(e -> { 
                	// Add correlation id only if missing
                	e.getIn().setHeader(Constants.CORRELATION_ID, e.getIn().getHeader(Constants.CORRELATION_ID,UUID.randomUUID().toString()));
                })

		        .process(e -> Status.addStatus(e, Action.VALIDATION, State.PENDING))
		        .to("direct:updateStatus")

	            .process(e -> e.getIn().setHeader(CHOUETTE_REFERENTIAL, getProviderRepository().getProvider(e.getIn().getHeader(PROVIDER_ID, Long.class)).chouetteInfo.referential))
                .process(e -> e.getIn().setHeader(JSON_PART, getJsonFileContent(e.getIn().getHeader(PROVIDER_ID, Long.class)))) //Using header to add json data
                .log(LoggingLevel.DEBUG,correlation()+"Creating multipart request")
                .process(e -> toGenericChouetteMultipart(e))
                .setHeader(Exchange.CONTENT_TYPE, simple("multipart/form-data"))
                .toD(chouetteUrl + "/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/validator") // TODO set to validation
                .process(e -> {
                    e.getIn().setHeader(Constants.CHOUETTE_JOB_STATUS_URL, e.getIn().getHeader("Location").toString().replaceFirst("http", "http4"));
                })
                .setHeader(Constants.CHOUETTE_JOB_STATUS_ROUTING_DESTINATION,constant("direct:processValidationResult"))
        		.setHeader(Constants.CHOUETTE_JOB_STATUS_JOB_TYPE, constant(Action.VALIDATION.name()))
		        .to("activemq:queue:ChouettePollStatusQueue")
                .routeId("chouette-send-validation-job");

        // Will be sent here after polling completes
		 from("direct:processValidationResult")
		        .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
		        .setBody(constant(""))
		        .choice()
		        .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'OK'"))
		            .to("direct:checkScheduledJobsBeforeTriggeringExport")
		            .process(e -> Status.addStatus(e, Action.VALIDATION, State.OK))
		        .when(simple("${header.action_report_result} == 'OK' and ${header.validation_report_result} == 'NOK'"))
		        	.log(LoggingLevel.INFO,correlation()+"Validation failed (processed ok, but timetable data is faulty)")
		            .process(e -> Status.addStatus(e, Action.VALIDATION, State.FAILED))
		        .otherwise()
		            .log(LoggingLevel.ERROR,correlation()+"Validation went wrong")
		            .process(e -> Status.addStatus(e, Action.VALIDATION, State.FAILED))
		        .end()
		        .to("direct:updateStatus")
		        .routeId("chouette-process-validation-status");

	 // Check that no other import jobs in status SCHEDULED exists for this referential. If so, do not trigger export
		from("direct:checkScheduledJobsBeforeTriggeringExport")
				.setProperty("job_status_url",simple("{{chouette.url}}/chouette_iev/referentials/${header." + CHOUETTE_REFERENTIAL + "}/jobs?action=importer&status=SCHEDULED&status=STARTED"))
				.toD("${exchangeProperty.job_status_url}")
				.choice()
				.when().jsonpath("$.*[?(@.status == 'SCHEDULED')].status")
					.log(LoggingLevel.INFO,correlation()+"Validation ok, skipping export as there are more import jobs active")
				.when(method(getClass(),"shouldTransferData").isEqualTo(true))
					.log(LoggingLevel.INFO,correlation()+"Validation ok, transfering data to next dataspace")
					.to("activemq:queue:ChouetteTransferExportQueue")
				.otherwise()
					.log(LoggingLevel.INFO,correlation()+"Validation ok, triggering GTFS export.")
			        .setBody(constant(""))
					.to("activemq:queue:ChouetteExportQueue") // Check on provider if should trigger transfer
				.end()
				.routeId("chouette-process-job-list-after-validation");

    }
    

    String getJsonFileContent(Long providerId) {
        ChouetteInfo chouetteInfo = getProviderRepository().getProvider(providerId).chouetteInfo;
    
        ValidationParameters validationParameters = ValidationParameters.create("Automatisk",
                chouetteInfo.referential, chouetteInfo.organisation, chouetteInfo.user);
        validationParameters.enableValidation = true;
        return validationParameters.toJsonString();
    }
}


