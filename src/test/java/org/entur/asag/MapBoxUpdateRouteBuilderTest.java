package org.entur.asag;


import org.apache.camel.Exchange;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.model.ModelCamelContext;
import org.entur.asag.mapbox.MapBoxUpdateRouteBuilder;
import org.entur.asag.service.BlobStoreService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;

import java.io.File;
import java.io.FileInputStream;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.MapBoxUpdateRouteBuilder.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;


@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = MapBoxUpdateRouteBuilder.class,
        properties = {
                "spring.main.sources=org.entur.asag",
                "mapbox.api.url=http4://localhost:${wiremock.server.port}",
                "mapbox.upload.status.poll.delay=0",
                "blobstore.gcs.container.name=container",
                "blobstore.gcs.credential.path=credpath",
                "blobstore.gcs.project.id=123"

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
            " \"modified\":\"2018-01-19T10:14:41.359Z\", \"tileset\":\"tilesetname\", \"owner\":\"owner\", \"progress\":0}";

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

    @Before
    public void before() throws Exception {
        replaceEndpoint("mapbox-convert-upload-tiamat-data", "direct:uploadMapboxDataAws", "mock:uploadMapboxDataAws");
//        inMemoryBlobStoreRepository.uploadBlob(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TIAMAT_EXPORT_LATEST_FILE_NAME,
//                new FileInputStream(new File("src/test/resources/no/rutebanken/marduk/routes/netex/stops.zip")), false);

        when(blobStoreService
                .getBlob(blobStoreSubdirectoryForTiamatGeoCoderExport + "/" + TIAMAT_EXPORT_LATEST_FILE_NAME))
                .thenReturn(new FileInputStream(new File("src/test/resources/stops.zip")));
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


    private void assertState(Exchange e, String state) {
        assertThat(e.getProperties().get(PROPERTY_STATE)).isEqualTo(state);
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
        stubFor(post(urlPathMatching(RETRIEVE_CREDENTIALS_PATH_PATTERN))
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