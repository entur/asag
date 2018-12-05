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


import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.entur.asag.mapbox.MapBoxUpdateRouteBuilder;
import org.entur.asag.mapbox.model.MapBoxUploadStatus;
import org.entur.asag.service.BlobStoreService;
import org.entur.asag.service.UploadStatusHubotReporter;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.io.File;
import java.io.FileInputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.MapBoxUpdateRouteBuilder.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;


@SpringBootTest(classes = MapBoxUpdateRouteBuilder.class,
        properties = {
                "spring.main.sources=org.entur.asag",
                "mapbox.api.url=http4://localhost:${wiremock.server.port}",
                "mapbox.upload.status.poll.delay=0",
                "blobstore.gcs.container.name=container",
                "blobstore.gcs.credential.path=credpath",
                "blobstore.gcs.project.id=123",
                "helper.hubot.endpoint=http://localhost:${wiremock.server.port}/hubot/say/"

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
    private ModelCamelContext context;

    @Value("${tiamat.export.blobstore.subdirectory:tiamat/geocoder}")
    private String blobStoreSubdirectoryForTiamatGeoCoderExport;

    @Produce(uri = "direct:uploadTiamatToMapboxAsGeoJson")
    protected ProducerTemplate producerTemplate;

    @Autowired
    private BlobStoreService blobStoreService;

    @Autowired
    private UploadStatusHubotReporter uploadStatusHubotReporter;

    @Value("${wiremock.server.port}")
    private int wiremockServerPort;

    @Before
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
    @Ignore
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
    @Ignore
    @Test
    public void testMapLayerDataTimeout() throws Exception {
        stubCredentials();
        stubInitiateUpload();
        // Always return not complete
        stubNotComplete();
        Exchange e = producerTemplate.request("direct:uploadTiamatToMapboxAsGeoJson", System.out::println);
        assertState(e, STATE_TIMEOUT);
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