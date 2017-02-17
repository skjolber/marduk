package no.rutebanken.marduk.rest;

import no.rutebanken.marduk.Constants;
import no.rutebanken.marduk.domain.BlobStoreFiles;
import no.rutebanken.marduk.domain.BlobStoreFiles.File;
import no.rutebanken.marduk.exceptions.MardukException;
import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.chouette.json.JobResponse;
import no.rutebanken.marduk.routes.chouette.json.Status;
import no.rutebanken.marduk.services.GraphStatusResponse;
import org.apache.camel.Body;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.model.rest.RestPropertyDefinition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.*;

/**
 * REST interface for backdoor triggering of messages
 */
@Component
public class AdminRestRouteBuilder extends BaseRouteBuilder {


	private static final String JSON = "application/json";
	private static final String X_OCTET_STREAM = "application/x-octet-stream";
	private static final String PLAIN = "text/plain";

	@Value("${server.admin.port}")
	public String port;

	@Value("${server.admin.host}")
	public String host;

	@Override
	public void configure() throws Exception {
		super.configure();

		RestPropertyDefinition corsAllowedHeaders = new RestPropertyDefinition();
		corsAllowedHeaders.setKey("Access-Control-Allow-Headers");
		corsAllowedHeaders.setValue("Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers, Authorization");

		restConfiguration().setCorsHeaders(Collections.singletonList(corsAllowedHeaders));

		restConfiguration()
				.component("netty4-http")
				.bindingMode(RestBindingMode.json)
				.enableCORS(true)
				.dataFormatProperty("prettyPrint", "true")
				.componentProperty("urlDecodeHeaders", "true")
				.host(host)
				.port(port)
				.apiContextPath("/api-doc")
				.apiProperty("api.title", "Marduk Admin API").apiProperty("api.version", "1.0")

				.contextPath("/admin");

		rest("/application")
				.post("/filestores/clean")
				.description("Clean Idempotent File Stores")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-application-clean-idempotent-file-repos")
				.to("direct:cleanIdempotentFileStore")
				.setBody(constant(null))
				.endRest();

		rest("/services/chouette")
				.get("/jobs")
				.description("List Chouette jobs for all providers. Filters defaults to status=SCHEDULED,STARTED")
				.param()
				.required(Boolean.FALSE)
				.name("status")
				.type(RestParamType.query)
				.description("Chouette job statuses")
				.allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
				.endParam()
				.param()
				.required(Boolean.FALSE)
				.name("action")
				.type(RestParamType.query)
				.description("Chouette job types")
				.allowableValues("importer", "exporter", "validator")
				.endParam()
				.outType(ProviderAndJobs[].class)
				.consumes(PLAIN)
				.produces(JSON)
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route()
				.log(LoggingLevel.INFO, correlation() + "Get chouette active jobs all providers")
				.removeHeaders("CamelHttp*")
				.process(e -> e.getIn().setHeader("status", e.getIn().getHeader("status") != null ? e.getIn().getHeader("status") : Arrays.asList("STARTED", "SCHEDULED")))
				.to("direct:chouetteGetJobsAll")
				.routeId("admin-chouette-list-jobs-all")
				.endRest()
				.delete("/jobs")
				.description("Cancel all Chouette jobs for all providers")
				.responseMessage().code(200).message("All jobs canceled").endResponseMessage()
				.responseMessage().code(500).message("Could not cancel all jobs").endResponseMessage()
				.route()
				.log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs for all providers")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteCancelAllJobsForAllProviders")
				.routeId("admin-chouette-cancel-all-jobs-all")
				.setBody(constant(null))
				.endRest()
				.post("/clean/{filter}")
				.description("Triggers the clean ALL dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations)")
				.param()
				.required(Boolean.TRUE)
				.name("filter")
				.type(RestParamType.path)
				.description("Optional filter to clean only level 1, level 2 or all spaces (no parameter value)")
				.allowableValues("all", "level1", "level2")

				.endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.responseMessage().code(500).message("Internal error - check filter").endResponseMessage()
				.route()
				.log(LoggingLevel.INFO, correlation() + "Chouette clean all dataspaces")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteCleanAllReferentials")
				.setBody(constant(null))
				.routeId("admin-chouette-clean-all")
				.endRest();


		rest("/services/chouette/{providerId}")
				.post("/import")
				.description("Triggers the import->validate->export process in Chouette for each blob store file handle. Use /files call to obtain available files. Files are imported in the same order as they are provided")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.type(BlobStoreFiles.class)
				.outType(String.class)
				.consumes(JSON)
				.produces(PLAIN)
				.responseMessage().code(200).message("Job accepted").endResponseMessage()
				.responseMessage().code(500).message("Invalid providerId").endResponseMessage()
				.route()
				.removeHeaders("CamelHttp*")
				.setHeader(PROVIDER_ID, header("providerId"))
				.split(method(ImportFilesSplitter.class, "splitFiles"))

				.process(e -> e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND
						                                               + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
						                                               + "/" + e.getIn().getBody(String.class)))
				.process(e -> e.getIn().setHeader(CORRELATION_ID, UUID.randomUUID().toString()))
				.log(LoggingLevel.INFO, correlation() + "Chouette start import fileHandle=${body}")

				.process(e -> {
					String fileNameForStatusLogging = "reimport-" + e.getIn().getBody(String.class);
					e.getIn().setHeader(Constants.FILE_NAME, fileNameForStatusLogging);
				})
				.setBody(constant(null))

				.inOnly("activemq:queue:ProcessFileQueue")
				.routeId("admin-chouette-import")
				.endRest()
				.get("/files")
				.description("List files available for reimport into Chouette")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.outType(BlobStoreFiles.class)
				.consumes(PLAIN)
				.produces(JSON)
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Invalid providerId").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "blob store get files")
				.removeHeaders("CamelHttp*")
				.to("direct:listBlobsFlat")
				.routeId("admin-chouette-import-list")
				.endRest()
				.get("/files/{fileName}")
				.description("Download file for reimport into Chouette")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.param().name("fileName").type(RestParamType.path).description("Name of file to fetch").dataType("String").endParam()
				.consumes(PLAIN)
				.produces(X_OCTET_STREAM)
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Invalid fileName").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.process(e -> e.getIn().setHeader(FILE_HANDLE, Constants.BLOBSTORE_PATH_INBOUND
						                                               + getProviderRepository().getReferential(e.getIn().getHeader(PROVIDER_ID, Long.class))
						                                               + "/" + e.getIn().getHeader("fileName")))
				.log(LoggingLevel.INFO, correlation() + "blob store download file by name")
				.removeHeaders("CamelHttp*")
				.to("direct:getBlob")
				.choice().when(simple("${body} == null")).setHeader(Exchange.HTTP_RESPONSE_CODE, constant(404)).endChoice()
				.routeId("admin-chouette-file-download")
				.endRest()
				.get("/lineStats")
				.description("List stats about data in chouette for a given provider")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.bindingMode(RestBindingMode.off)
				.consumes(PLAIN)
				.produces(JSON)
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Invalid providerId").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "get stats")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteGetStats")
				.routeId("admin-chouette-stats")
				.endRest()
				.get("/jobs")
				.description("List Chouette jobs for a given provider")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.param()
				.required(Boolean.FALSE)
				.name("status")
				.type(RestParamType.query)
				.description("Chouette job statuses")
				.allowableValues(Arrays.asList(Status.values()).stream().map(Status::name).collect(Collectors.toList()))
				.endParam()
				.param()
				.required(Boolean.FALSE)
				.name("action")
				.type(RestParamType.query)
				.description("Chouette job types")
				.allowableValues("importer", "exporter", "validator")
				.endParam()
				.outType(JobResponse[].class)
				.consumes(PLAIN)
				.produces(JSON)
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Invalid providerId").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "Get chouette jobs status=${header.status} action=${header.action}")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteGetJobsForProvider")
				.routeId("admin-chouette-list-jobs")
				.endRest()
				.delete("/jobs")
				.description("Cancel all Chouette jobs for a given provider")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Job deleted").endResponseMessage()
				.responseMessage().code(500).message("Invalid jobId").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "Cancel all chouette jobs")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteCancelAllJobsForProvider")
				.routeId("admin-chouette-cancel-all-jobs")
				.endRest()
				.delete("/jobs/{jobId}")
				.description("Cancel a Chouette job for a given provider")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.param().name("jobId").type(RestParamType.path).description("Job id as returned in any of the /jobs GET calls").dataType("int").endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Job deleted").endResponseMessage()
				.responseMessage().code(500).message("Invalid jobId").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.setHeader(Constants.CHOUETTE_JOB_ID, header("jobId"))
				.log(LoggingLevel.INFO, correlation() + "Cancel chouette job")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteCancelJob")
				.routeId("admin-chouette-cancel-job")
				.endRest()
				.post("/export")
				.description("Triggers the export process in Chouette. Note that NO validation is performed before export, and that the data must be guaranteed to be error free")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "Chouette start export")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:ChouetteExportQueue")
				.routeId("admin-chouette-export")
				.endRest()
				.post("/validate")
				.description("Triggers the validate->export process in Chouette")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "Chouette start validation")
				.removeHeaders("CamelHttp*")
				.setHeader(CHOUETTE_JOB_STATUS_JOB_VALIDATION_LEVEL, constant(no.rutebanken.marduk.routes.status.Status.Action.VALIDATION_LEVEL_1.name()))
				.inOnly("activemq:queue:ChouetteValidationQueue")
				.routeId("admin-chouette-validate")
				.endRest()
				.post("/clean")
				.description("Triggers the clean dataspace process in Chouette. Only timetable data are deleted, not job data (imports, exports, validations)")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "Chouette clean dataspace")
				.removeHeaders("CamelHttp*")
				.to("direct:chouetteCleanReferential")
				.routeId("admin-chouette-clean")
				.endRest()
				.post("/transfer")
				.description("Triggers transfer of data from one dataspace to the next")
				.param().name("providerId").type(RestParamType.path).description("Provider id as obtained from the nabu service").dataType("int").endParam()
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.setHeader(PROVIDER_ID, header("providerId"))
				.log(LoggingLevel.INFO, correlation() + "Chouette transfer dataspace")
				.removeHeaders("CamelHttp*")
				.setHeader(PROVIDER_ID, header("providerId"))
				.inOnly("activemq:queue:ChouetteTransferExportQueue")
				.routeId("admin-chouette-transfer")
				.endRest();


		rest("/services/graph")
				.post("/build")
				.description("Triggers building of the OTP graph using existing gtfs and map data")
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.log(LoggingLevel.INFO, "OTP build graph")
				.removeHeaders("CamelHttp*")
				.setBody(simple(""))
				.inOnly("activemq:queue:OtpGraphQueue")
				.routeId("admin-build-graph")
				.endRest()
				.get("/status")
				.description("Query status of OTP graph building")
				.consumes(PLAIN)
				.produces(JSON)
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).endResponseMessage()
				.outType(GraphStatusResponse.class)
				.route()
				.log(LoggingLevel.DEBUG, "OTP get graph status")
				.removeHeaders("CamelHttp*")
				.setBody(simple(null))
				.bean("graphStatusService", "getStatus")
				.routeId("admin-build-graph-status")
				.endRest();

		rest("/services/fetch")
				.post("/osm")
				.description("Triggers downloading of the latest OSM data")
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.log(LoggingLevel.INFO, "OSM update map data")
				.removeHeaders("CamelHttp*")
				.to("direct:considerToFetchOsmMapOverNorway")
				.routeId("admin-fetch-osm")
				.endRest();

		rest("/services/marduk")
				.post("/file")
				.description("Adjust the marduk file in hubot and etcd")
				.consumes(PLAIN)
				.produces(PLAIN)
				.responseMessage().code(200).message("Command accepted").endResponseMessage()
				.route()
				.convertBodyTo(String.class)
				// Does not work - expecting json for some reason: .process(p -> p.getOut().setHeader(FILE_HANDLE, p.getIn().getBody()))
				.setHeader(FILE_HANDLE, simple("static"))
				.process(p -> {
					throw new MardukException("This an endpoint for development purposes ONLY. ");
				})
				.log(LoggingLevel.INFO, "Want to set ${header." + FILE_HANDLE + "}")
				.to("direct:notify")
				.to("direct:notifyEtcd")
				.setBody(simple("done"))
				.routeId("admin-marduk-file")
				.endRest();


		rest("geocoder/administrativeUnits")
				.get("/download")
				.description("Trigger download of administrative units from Norwegian mapping authority")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-administrative-units-download")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:AdministrativeUnitsDownloadQueue")
				.setBody(constant(null))
				.endRest()
				.get("/update")
				.description("Trigger import of administrative units to Tiamat")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-administrative-units-tiamat-update")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:TiamatAdministrativeUnitsUpdateQueue")
				.setBody(constant(null))
				.endRest();

		rest("geocoder/poi")
				.get("/update")
				.description("Trigger import of place of interest info to Tiamat")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-place-of-interest-tiamat-update")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:TiamatPlaceOfInterestUpdateQueue")
				.setBody(constant(null))
				.endRest();


		rest("geocoder/address")
				.get("/download")
				.description("Trigger download of address info from Norwegian mapping authority")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-address-download")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:AddressDownloadQueue")
				.setBody(constant(null))
				.endRest();


		rest("geocoder/placeNames")
				.get("/download")
				.description("Trigger download of place names from Norwegian mapping authority")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-place-names-download")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:AddressDownloadQueue")
				.setBody(constant(null))
				.endRest();


		rest("geocoder/tiamat")
				.get("/export")
				.description("Trigger export from Tiamat")
				.responseMessage().code(200).endResponseMessage()
				.responseMessage().code(500).message("Internal error").endResponseMessage()
				.route().routeId("admin-tiamat-export")
				.removeHeaders("CamelHttp*")
				.inOnly("activemq:queue:TiamatExportQueue")
				.setBody(constant(null))
				.endRest();

	}

	public static class ImportFilesSplitter {
		public List<String> splitFiles(@Body BlobStoreFiles files) {
			return files.getFiles().stream().map(File::getName).collect(Collectors.toList());
		}
	}
}


