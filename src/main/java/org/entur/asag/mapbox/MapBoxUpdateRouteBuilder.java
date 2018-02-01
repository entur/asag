/*
 * Licensed under the EUPL, Version 1.2 or – as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * You may not use this work except in compliance with the Licence.
 * You may obtain a copy of the Licence at:
 *
 *   https://joinup.ec.europa.eu/software/page/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */

package org.entur.asag.mapbox;


import com.google.common.base.Strings;
import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.spring.SpringRouteBuilder;
import org.apache.commons.io.FileUtils;
import org.entur.asag.mapbox.model.MapBoxAwsCredentials;
import org.entur.asag.mapbox.model.MapBoxUploadStatus;
import org.entur.asag.mapbox.model.MapboxUploadRequest;
import org.entur.asag.util.ZipFileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.FileInputStream;

import static org.apache.camel.Exchange.FILE_NAME;
import static org.apache.commons.io.FileUtils.deleteDirectory;

@Component
public class MapBoxUpdateRouteBuilder extends SpringRouteBuilder {

    private static final String TIAMAT_EXPORT_GCP_PATH = "tiamat-export";
    public static final String LOOP_COUNTER = "LoopCounter";
    public static final String FILE_HANDLE = "FileHandle";

    public static final String TIAMAT_EXPORT_LATEST_FILE_NAME = "tiamat_export_geocoder_latest.zip";

    public static final String PROPERTY_STATE = "state";
    public static final String STATE_FINISHED = "finished";
    public static final String STATE_ERROR = "error";
    public static final String STATE_TIMEOUT = "timeout";

    /**
     * Use the same tiamat data as the geocoder
     */
    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Value("${mapbox.download.directory:files/mapbox}")
    private String localWorkingDirectory;

    @Value("${mapbox.api.url:https4://api.mapbox.com}")
    private String mapboxApiUrl;

    @Value("${blobstore.gcs.project.id:}")
    private String projectId;

    @Value("${mapbox.access.token:}")
    private String mapboxAccessToken;

    @Value("${mapbox.user:entur}")
    private String mapboxUser;

    @Value("${mapbox.aws.region:us-east-1")
    private String awsRegion;

    @Value("${mapbox.upload.status.max.retries:20}")
    private int mapboxUploadPollMaxRetries;

    @Value("${mapbox.upload.status.poll.delay:20000}")
    private int mapboxUploadPollDelay;

    @Override
    public void configure() throws Exception {

        /*
          	the map ID to create or replace in the format  username.nameoftileset - limited to 32 characters
          	(only  - and  _ special characters allowed, limit does not include username)
         */
        final String tilesetName = mapboxUser + "." + (Strings.isNullOrEmpty(projectId) ? "tileset" : projectId);
        final String geojsonFilename = (Strings.isNullOrEmpty(projectId) ? mapboxUser : projectId) + ".geojson";

        from("direct:uploadTiamatToMapboxAsGeoJson")
                .bean("uploadStatusHubotReporter", "postStarted")
                .setHeader(TIAMAT_EXPORT_GCP_PATH, simple(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TIAMAT_EXPORT_LATEST_FILE_NAME))
                .to("direct:recreateLocalMapboxDirectory")
                .to("direct:downloadLatestTiamatExportToMapboxFolder")
                .to("direct:mapboxUnzipLatestTiamatExportToFolder")
                .to("direct:retrieveMapboxAwsCredentials")
                .to("direct:findFirstXmlFileRecursive")
                .to("direct:transformToGeoJsonFromTiamat")
                .setHeader(FILE_NAME, constant(geojsonFilename))
                .to("file://" + localWorkingDirectory + "?fileName=" + geojsonFilename)
                .to("direct:uploadMapboxDataAws")
                .to("direct:initiateMapboxUpload")
                .delay(mapboxUploadPollDelay)
                .to("direct:pollRetryMapboxStatus")
                .routeId("mapbox-convert-upload-tiamat-data");

        from("direct:initiateMapboxUpload")
                .process(exchange -> exchange.getOut().setBody(
                        new MapboxUploadRequest(tilesetName,
                            ((MapBoxAwsCredentials) exchange.getIn().getHeader("credentials")).getUrl(),
                            exchange.getIn().getHeader(FILE_NAME).toString())))
                .marshal().json(JsonLibrary.Jackson)
                .log(LoggingLevel.INFO, "Upload: ${body}")
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.POST))
                .setHeader(Exchange.CONTENT_TYPE, constant("application/json"))
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "?access_token=" + mapboxAccessToken + "&throwExceptionOnFailure=false")
                .to("log:DEBUG?showBody=true&showHeaders=true")
                .unmarshal().json(JsonLibrary.Jackson, MapBoxUploadStatus.class)
                .log(LoggingLevel.INFO, "Received ${body}")
                .routeId("initiate-mapbox-upload");

        from("direct:uploadMapboxDataAws")
                .bean("awsS3Uploader", "upload")
                .routeId("upload-mapbox-data-aws");

        from("direct:findFirstXmlFileRecursive")
                .process(e -> e.getIn().setBody( FileUtils.listFiles(new File(localWorkingDirectory + "/tiamat"), new String[]{"xml"}, true).stream().findFirst().get()))
                .routeId("mapbox-find-first-xml-file-recursive");

        from("direct:pollRetryMapboxStatus")
                .process(e -> e.getIn().setHeader(LOOP_COUNTER, 0))
                .loopDoWhile(simple("${header."+LOOP_COUNTER +"} <= " + mapboxUploadPollMaxRetries))
                .process(e -> e.getIn().setHeader(LOOP_COUNTER, (Integer) e.getIn().getHeader(LOOP_COUNTER, 0) + 1))
                .to("direct:endIfMapboxUploadError")

                .choice()
                    .when(simple("${body.complete}"))
                        .log(LoggingLevel.INFO,"Tileset upload complete: ${body.id}")
                        .setProperty(PROPERTY_STATE, simple(STATE_FINISHED))
                        .bean("uploadStatusHubotReporter", "postUploadStatusToHubot")
                        .stop()
                    .otherwise()
                        .choice()
                            .when(simple("${body.message}"))
                            .log(LoggingLevel.INFO, "Got message, Exiting: ${body.message}")
                            .bean("uploadStatusHubotReporter", "postUploadStatusToHubot")
                            .stop()
                        .otherwise()
                            .log(LoggingLevel.INFO, "Tileset upload ${body.id}: Not complete yet.. wait a bit and try again. (${header.\"" + LOOP_COUNTER + "\"})")
                            .delay(mapboxUploadPollDelay)
                            .to("direct:fetchMapboxUploadStatus")
                        .end()
                .endChoice()

                .choice()
                    .when(simple("${header." + LOOP_COUNTER + "} > " + mapboxUploadPollMaxRetries))
                        .log(LoggingLevel.WARN, getClass().getName(), "Giving up after looping after " + mapboxUploadPollMaxRetries + " iterations")
                        .setProperty(PROPERTY_STATE, simple(STATE_TIMEOUT))
                        .bean("uploadStatusHubotReporter", "postUploadStatusToHubot")
                        .stop() // end route?
                .endChoice()
                .routeId("mapbox-poll-retry-upload-status");

        from("direct:endIfMapboxUploadError")
                .choice()
                    .when(simple("${body.error}"))
                    .log(LoggingLevel.ERROR, "Got error uploading tileset. ${body}")
                    .setProperty(PROPERTY_STATE, simple(STATE_ERROR))
                    .bean("uploadStatusHubotReporter", "postUploadStatusToHubot")
                    .stop()
                .endChoice()
                .routeId("mapbox-end-if-upload-error");

        from("direct:fetchMapboxUploadStatus")
                .setProperty("tilesetId", simple("${body.id}"))
                .log("Checking status for tileset: ${property.tilesetId}")
                .setBody(simple(""))
                .setHeader(Exchange.HTTP_METHOD, constant(org.apache.camel.component.http4.HttpMethods.GET))
                .toD(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "/${property.tilesetId}?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxUploadStatus.class)
                .log("Received status ${body}")
                .routeId("fetch-mapbox-upload-status");

        from("direct:retrieveMapboxAwsCredentials")
                .log(LoggingLevel.INFO, "About to retrieve credentials for aws from mapbox. User: "+ mapboxUser)
                .to(mapboxApiUrl + "/uploads/v1/" + mapboxUser + "/credentials?access_token=" + mapboxAccessToken)
                .unmarshal().json(JsonLibrary.Jackson, MapBoxAwsCredentials.class)
                .setHeader("credentials", simple("${body}"))
                .log(LoggingLevel.INFO, "retrieved credentials: ${header.credentials}")
                .routeId("mapbox-retrieve-aws-credentials");

        from("direct:downloadLatestTiamatExportToMapboxFolder")
                .setHeader(FILE_HANDLE, header(TIAMAT_EXPORT_GCP_PATH))
                .bean("blobStoreService", "getBlob")
                .choice()
                .when(body().isNotEqualTo(null))
                .to("file:" + localWorkingDirectory + "/tiamat/?fileName=" + TIAMAT_EXPORT_LATEST_FILE_NAME)
                .otherwise()
                .log(LoggingLevel.INFO, getClass().getName(), "${header." + FILE_HANDLE + "} was empty when trying to fetch it from blobstore.")
                .routeId("mapbox-download-latest-tiamat-export-to-folder");

        from("direct:mapboxUnzipLatestTiamatExportToFolder")
                .process(e -> ZipFileUtils.unzipFile(new FileInputStream(localWorkingDirectory + "/tiamat/" + TIAMAT_EXPORT_LATEST_FILE_NAME), localWorkingDirectory + "/tiamat"))
                .log(LoggingLevel.INFO, "Unzipped file to folder tiamat")
                .routeId("mapbox-unzip-tiamat-export");

        from("direct:recreateLocalMapboxDirectory")
                .setHeader(Exchange.FILE_PARENT, constant(localWorkingDirectory))
                .to("direct:cleanUpLocalDirectory")
                .process(e -> new File(localWorkingDirectory).mkdirs())
                .routeId("mapbox-recreate-mapbox-directory");

        from("direct:transformToGeoJsonFromTiamat")
                .log(LoggingLevel.INFO, "convert tiamat data to geojson")
                .bean("deliveryPublicationStreamToGeoJson", "transform")
                .routeId("mapbox-transform-from-tiamat");

        from("direct:cleanUpLocalDirectory")
                .log(LoggingLevel.DEBUG, getClass().getName(), "Deleting local directory ${property." + Exchange.FILE_PARENT + "} ...")
                .process(e -> deleteDirectory(new File(e.getIn().getHeader(Exchange.FILE_PARENT, String.class))))
                .log(LoggingLevel.DEBUG, getClass().getName(),  "Local directory ${property." + Exchange.FILE_PARENT + "} cleanup done.")
                .routeId("cleanup-local-dir");
    }
}
