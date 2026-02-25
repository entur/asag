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

package org.entur.asag;


import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.entur.asag.mapbox.MapBoxUpdateRouteBuilder;
import org.entur.asag.service.BlobStoreService;
import org.entur.asag.service.UploadStatusHubotReporter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.test.annotation.DirtiesContext;

import java.io.File;
import java.io.FileInputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.NoSuchElementException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.commons.io.FileUtils;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.MapBoxUpdateRouteBuilder.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@SpringBootTest(classes = MapBoxUpdateRouteBuilder.class,
        properties = {
                "spring.main.sources=org.entur.asag",
                "mapbox.api.url=http://localhost:${wiremock.server.port}",
                "mapbox.upload.status.poll.delay=0",
                "mapbox.upload.status.max.retries=3",
                "blobstore.gcs.container.name=container",
                "blobstore.gcs.credential.path=credpath",
                "blobstore.gcs.project.id=123",
                "helper.slack.endpoint=http://localhost:${wiremock.server.port}/hubot/say/",
                "camel.springboot.use-advice-with=true",
                "asag.run.on.startup=false"
        })
@AutoConfigureWireMock(port = 0)
public class MapBoxUpdateRouteBuilderTest extends AsagRouteBuilderIntegrationTestBase {

    private static final String TILESET_ID = "someId";
    private static final String RETRIEVE_CREDENTIALS_PATH_PATTERN = "/uploads/v1/(\\w+)/credentials";
    private static final String UPLOAD_INITIATE_PATH_PATTERN = "/uploads/v1/\\w+\\?{1}access_token.*";
    private static final String UPLOAD_STATUS_PATH_PATTERN = "/uploads/v1/\\w+/" + TILESET_ID;

    private static final String MAPBOX_RESPONSE_NOT_COMPLETE = "{\"id\":\"" + TILESET_ID + "\", \"name\":\"tiamat.geojson\", \"complete\":false, \"error\":null, \"created\":\"2018-01-19T10:14:41.359Z\"," +
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":0}";

    private static final String MAPBOX_RESPONSE_ERROR = "{\"id\":\"" + TILESET_ID + "\", \"name\":\"tiamat.geojson\", \"complete\":false, \"error\":\"Failure!\", \"created\":\"2018-01-19T10:14:41.359Z\"," +
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":0, \"message\":\"message\"}";

    private static final String MAPBOX_RESPONSE_COMPLETE = "{\"id\":\"" + TILESET_ID + "\", \"name\":\"tiamat.geojson\", \"complete\":true, \"error\":null, \"created\":\"2018-01-19T10:14:41.359Z\"," +
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":1}";

    private static final String MAPBOX_CREDENTIALS_RESPONSE = "{ \"bucket\": \"bucket\", \"key\": \"key\", \"accessKeyId\": \"accessKeyId\", " +
            " \"secretAccessKey\": \"secretAKey\", \"sessionToken\": \"sestoken\", \"url\": \"http://localhost:0000\" }";

    @Autowired
    private CamelContext context;

    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Produce("direct:uploadTiamatToMapboxAsGeoJson")
    protected ProducerTemplate producerTemplate;

    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    private UploadStatusHubotReporter uploadStatusHubotReporter;

    @Value("${wiremock.server.port}")
    private int wiremockServerPort;

    @BeforeEach
    public void before() throws Exception {

        System.out.println("Wiremock port is: " + +wiremockServerPort);
        replaceEndpoint("mapbox-convert-upload-tiamat-data", "direct:uploadMapboxDataAws", "mock:uploadMapboxDataAws");

        when(blobStoreService
                .getBlob(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TIAMAT_EXPORT_LATEST_FILE_NAME))
                .thenReturn(new FileInputStream(new File(getClass().getResource("/stops.zip").getFile())));

        stubHubot();
        context.start();
    }

    /**
     * Test that the finished state is set upon successful upload
     *
     * @throws Exception
     */

    @Test
    public void testMapLayerDataSuccess() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        stubSuccess();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_FINISHED);
    }

    /**
     * Test that error response from mapbox is handled
     */
    @Test
    public void testMapLayerDataError() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        stubError();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_ERROR);
    }

    /**
     * Test that a state is set when giving up checking the status
     */

    @Test
    public void testMapLayerDataTimeout() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        // Always return not complete
        stubNotComplete();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_TIMEOUT);
    }


    /**
     * When the blob store returns null (file not found in GCS), the download route
     * logs and continues without writing the ZIP file. The subsequent unzip step then
     * tries to open a FileInputStream on a non-existent path, causing a
     * FileNotFoundException wrapped in RuntimeException. The route should fail rather
     * than silently produce empty GeoJSON.
     */
    @Test
    public void testNullBlobCausesRouteFailure() throws Exception {
        // Reset the blob stub from @Before so getBlob() returns null
        reset(blobStoreService);
        FileUtils.deleteDirectory(new File("files/mapbox/tiamat"));

        boolean failed = false;
        try {
            Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", exc -> {});
            failed = e.isFailed();
        } catch (Exception e) {
            // ProducerTemplate may propagate the exception directly — both are failure indicators
            failed = true;
        }

        assertThat(failed)
                .as("Route should fail when the blob store returns null (file not found in GCS)")
                .isTrue();
    }

    /**
     * When the downloaded ZIP contains no XML files, findFirstXmlFileRecursive throws
     * NoSuchElementException from Optional.get() on an empty stream. The route should
     * fail rather than silently continuing with a null body.
     */
    @Test
    public void testZipWithNoXmlFilesCausesRouteFailure() throws Exception {
        reset(blobStoreService);

        // Remove any XML files left in the working directory by previous tests.
        // The route's cleanUpLocalDirectory step is commented out, so files persist
        // across test runs within the same JVM invocation.
        FileUtils.deleteDirectory(new File("files/mapbox/tiamat"));

        // Build a ZIP that contains only a non-XML file
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("data.csv");
            zos.putNextEntry(entry);
            zos.write("id,name\n1,stop".getBytes());
            zos.closeEntry();
        }

        when(blobStoreService.getBlob(anyString()))
                .thenReturn(new ByteArrayInputStream(baos.toByteArray()));

        stubCredentials();

        boolean failed = false;
        try {
            Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", exc -> {});
            failed = e.isFailed();
            if (failed && e.getException() != null) {
                assertThat(e.getException()).isInstanceOf(NoSuchElementException.class);
            }
        } catch (Exception e) {
            failed = true;
            // Unwrap CamelExecutionException to verify the root cause
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            assertThat(cause).isInstanceOf(NoSuchElementException.class);
        }

        assertThat(failed)
                .as("Route should fail when the ZIP contains no XML files")
                .isTrue();
    }

    private void assertState(Exchange e, String state) {
        assertThat(e.getProperties().get(PROPERTY_STATE)).isEqualTo(state);
    }

    private void stubHubot() {
        stubFor(post(urlEqualTo("/hubot/say/"))
        .willReturn(aResponse().withBody("OK")));
    }

    private void stubError() {
        stubFor(post(urlMatching(UPLOAD_INITIATE_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_ERROR)));
    }

    private void stubNotComplete() {
        stubFor(get(urlPathMatching(UPLOAD_STATUS_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_NOT_COMPLETE)));
    }

    private void stubSuccess() {
        stubFor(get(urlPathMatching(UPLOAD_STATUS_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_COMPLETE)));
    }

    private void stubCredentials() {
        stubFor(get(urlPathMatching(RETRIEVE_CREDENTIALS_PATH_PATTERN))
                .willReturn(
                        aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withBody(MAPBOX_CREDENTIALS_RESPONSE)));
    }

    public void stubInitiateUpload() {
        stubFor(post(urlMatching(UPLOAD_INITIATE_PATH_PATTERN))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(MAPBOX_RESPONSE_NOT_COMPLETE)));
    }
}