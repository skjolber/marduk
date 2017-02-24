package no.rutebanken.marduk.geocoder.routes.kartverket;


import no.rutebanken.marduk.routes.BaseRouteBuilder;
import no.rutebanken.marduk.routes.status.SystemStatus;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import static no.rutebanken.marduk.Constants.*;
import static no.rutebanken.marduk.geocoder.GeoCoderConstants.*;
@Component
public class AddressDownloadRouteBuilder extends BaseRouteBuilder {

	/**
	 * One time per 24H on MON-FRI
	 */
	@Value("${kartverket.addresses.download.cron.schedule:0+0+23+?+*+MON-FRI}")
	private String cronSchedule;

	@Value("${kartverket.place.names.blobstore.subdirectory:kartverket}")
	private String blobStoreSubdirectoryForKartverket;

	@Value("${kartverket.addresses.dataSetId:offisielle-adresser-utm33-csv}")
	private String addressesDataSetId;

	@Override
	public void configure() throws Exception {
		super.configure();

		singletonFrom("quartz2://marduk/addressDownload?cron=" + cronSchedule + "&trigger.timeZone=Europe/Oslo")
				.autoStartup("{{kartverket.address.download.autoStartup:false}}")
				.log(LoggingLevel.INFO, "Quartz triggers address download.")
				.setBody(constant(KARTVERKET_ADDRESS_DOWNLOAD))
				.to("direct:geoCoderStart")
				.routeId("address-download-quartz");

		from(KARTVERKET_ADDRESS_DOWNLOAD.getEndpoint())
				.log(LoggingLevel.INFO, "Start downloading address information from mapping authority")
				.process(e -> SystemStatus.builder(e).start(SystemStatus.Action.FILE_TRANSFER).entity("Kartverket addresses").build()).to("direct:updateSystemStatus")
				.setHeader(KARTVERKET_DATASETID, constant(addressesDataSetId))
				.setHeader(FOLDER_NAME, constant(blobStoreSubdirectoryForKartverket + "/addresses"))
				.to("direct:uploadUpdatedFiles")
				.choice()
				.when(simple("${header." + CONTENT_CHANGED + "}"))
				.log(LoggingLevel.INFO, "Uploaded updated address information from mapping authority. Initiating update of Pelias")
				.setBody(constant(null))
				.setProperty(GEOCODER_NEXT_TASK, constant(PELIAS_UPDATE_START))
				.otherwise()
				.log(LoggingLevel.INFO, "Finished downloading address information from mapping authority with no changes")
				.end()
				.process(e -> SystemStatus.builder(e).state(SystemStatus.State.OK).build()).to("direct:updateSystemStatus")
				.routeId("address-download");
	}


}