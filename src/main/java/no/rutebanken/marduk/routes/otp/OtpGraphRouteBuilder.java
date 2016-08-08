package no.rutebanken.marduk.routes.otp;

import no.rutebanken.marduk.routes.BaseRouteBuilder;
import org.apache.camel.LoggingLevel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.stream.Collectors;

import static no.rutebanken.marduk.Constants.CURRENT_AGGREGATED_GTFS_FILENAME;
import static no.rutebanken.marduk.Constants.FILE_HANDLE;
import static no.rutebanken.marduk.Constants.GRAPH_OBJ;
import static no.rutebanken.marduk.Constants.OTP_GRAPH_DIR;

/**
 * Trigger OTP graph building
 */
@Component
public class OtpGraphRouteBuilder extends BaseRouteBuilder {

    private static final String BUILD_CONFIG_JSON = "build-config.json";
    private static final String NORWAY_LATEST_OSM_PBF = "norway-latest.osm.pbf";
    private static final String TIMESTAMP = "RutebankenTimeStamp";

    @Value("${otp.graph.build.directory}")
    private String otpGraphBuildDirectory;

    @Value("${otp.graph.blobstore.subdirectory}")
    private String blobStoreSubdirectory;

    @Value("${osm.pbf.blobstore.subdirectory:osm}")
    private String blobStoreSubdirectoryForOsm;

    @Value("${activemq.broker.name:amqp-srv1}")
    private String brokerName;

    @Value("${activemq.broker.host:activemq}")
    private String brokerMgmtHost;

    @Value("${activemq.broker.mgmt.port:8161}")
    private String brokerMgmtPort;

    @Value("${spring.activemq.user}")
    private String brokerUser;

    @Value("${spring.activemq.password}")
    private String brokerPassword;

    @Value("${otp.graph.purge.queue:true}")
    private boolean purgeQueue;


    @Override
    public void configure() throws Exception {
        super.configure();

        //TODO Report status?
        from("activemq:queue:OtpGraphQueue?maxConcurrentConsumers=1")
        	.autoStartup("{{otp.graph.build.autoStartup:true}}")
                .choice()
                .when(constant(purgeQueue))
                    .log(LoggingLevel.INFO,correlation()+"Purging OtpGraphQueue.")
                    .to("http4://" + brokerMgmtHost + ":" + brokerMgmtPort + "/api/jolokia/exec/org.apache.activemq:type=Broker,brokerName=" +
                        brokerName + ",destinationType=Queue,destinationName=OtpGraphQueue/purge()?authUsername=" + brokerUser + "&authPassword=" + brokerPassword)
                .end()
                .setProperty(TIMESTAMP, simple("${date:now:yyyyMMddHHmmss}"))
                .setProperty(OTP_GRAPH_DIR, simple(otpGraphBuildDirectory + "/${property." + TIMESTAMP + "}"))
                .log(LoggingLevel.INFO,correlation()+"Starting graph building in directory ${property." + OTP_GRAPH_DIR + "}.")
                .to("direct:fetchLatestGtfs")
                .to("direct:fetchConfig")
                .to("direct:fetchMap")
                .to("direct:buildGraph")
                .log(LoggingLevel.INFO,correlation()+"Done with OTP graph building route.")
                .routeId("otp-graph-build");

        from("direct:fetchLatestGtfs")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Fetching gtfs files for all providers.")
                .setBody(simple(getAggregatedGtfsFiles()))
                .split(body())
                .to("direct:getGtfsFiles")
                .routeId("otp-graph-fetch-gtfs");

        from("direct:getGtfsFiles")
            	.log(LoggingLevel.INFO,correlation()+"Fetching outbound/gtfs/${body}")
                .setProperty("fileName", body())
                .setHeader(FILE_HANDLE, simple("outbound/gtfs/${property.fileName}"))
                .to("direct:getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/${property.fileName}")
                .otherwise()
                .log(LoggingLevel.INFO,correlation()+"${property.fileName} was empty when trying to fetch it from blobstore.")
                .routeId("otp-graph-get-gtfs");

        from("direct:fetchConfig")
                .log(LoggingLevel.DEBUG,correlation()+"Fetching config ...")
                .to("language:constant:resource:classpath:no/rutebanken/marduk/routes/otp/" + BUILD_CONFIG_JSON)
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/" + BUILD_CONFIG_JSON)
                .log(LoggingLevel.DEBUG, correlation()+BUILD_CONFIG_JSON + " fetched.")
                .routeId("otp-graph-fetch-config");

        from("direct:fetchMap")
                .log(LoggingLevel.DEBUG,correlation()+"Fetching map ...")
                .removeHeaders("*")
                .setHeader(FILE_HANDLE, simple(blobStoreSubdirectoryForOsm +"/"+"norway-latest.osm.pbf"))
                .to("direct:getBlob")
                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/" + NORWAY_LATEST_OSM_PBF)
                .log(LoggingLevel.DEBUG,correlation()+NORWAY_LATEST_OSM_PBF + " fetched.")
                .routeId("otp-graph-fetch-map");


        from("direct:buildGraph")
                .log(LoggingLevel.INFO,correlation()+"Building OTP graph...")
                .doTry()
	                .process(new GraphBuilderProcessor())
	                .setBody(constant(""))
	                .toD("file:" + otpGraphBuildDirectory + "?fileName=${property." + TIMESTAMP +"}/" + GRAPH_OBJ + ".done")
	                .to("log:" + getClass().getName() + "?level=DEBUG&showAll=true&multiline=true")
	                .log(LoggingLevel.INFO,correlation()+"Done building new OTP graph.")
        		.doCatch(Exception.class)
        			.log(LoggingLevel.ERROR,correlation()+"Graph building failed: "+exceptionMessage())
        		.end()
        		.routeId("otp-graph-build-otp");


    }

    String getAggregatedGtfsFiles(){
        return getProviderRepository().getProviders().stream()
                .map(p -> p.chouetteInfo.referential + "-" + CURRENT_AGGREGATED_GTFS_FILENAME).collect(Collectors.joining(","));
    }

}
