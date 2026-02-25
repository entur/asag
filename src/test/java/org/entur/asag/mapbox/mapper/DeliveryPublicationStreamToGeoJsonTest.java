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

package org.entur.asag.mapbox.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.entur.asag.mapbox.DeliveryPublicationStreamToGeoJson;
import org.entur.asag.mapbox.filter.ValidityFilter;
import org.geojson.Feature;
import org.geojson.FeatureCollection;
import org.junit.Test;
import org.rutebanken.netex.validation.NeTExValidator;
import org.xml.sax.SAXException;

import javax.xml.bind.JAXBException;
import javax.xml.transform.stream.StreamSource;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryPublicationStreamToGeoJsonTest {


    public static final String SRC_TEST_RESOURCES_PUBLICATION_DELIVERY_XML = "src/test/resources/publication-delivery.xml";
    private ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper = new ZoneToGeoJsonFeatureMapper();
    private QuayToGeoJsonFeatureMapper quayToGeoJsonFeatureMapper = new QuayToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper = new StopPlaceToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private ValidityFilter validityFilter = new ValidityFilter();
    private ParkingToGeoJsonFeatureMapper parkingToGeoJsonFeatureMapper = new ParkingToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private TariffZoneToGeoJsonFeatureMapper tariffZoneToGeoJsonFeatureMapper = new TariffZoneToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private DeliveryPublicationStreamToGeoJson deliveryPublicationStreamToGeoJson = new DeliveryPublicationStreamToGeoJson(stopPlaceToGeoJsonFeatureMapper, parkingToGeoJsonFeatureMapper, quayToGeoJsonFeatureMapper, tariffZoneToGeoJsonFeatureMapper, validityFilter);

    private NeTExValidator neTExValidator = new NeTExValidator();

    public DeliveryPublicationStreamToGeoJsonTest() throws JAXBException, IOException, SAXException {
    }

    @Test
    public void transform() throws Exception {

        neTExValidator.validate(new StreamSource(new FileInputStream(SRC_TEST_RESOURCES_PUBLICATION_DELIVERY_XML)));

        FileInputStream fileInputStream = new FileInputStream(SRC_TEST_RESOURCES_PUBLICATION_DELIVERY_XML);
        ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) deliveryPublicationStreamToGeoJson.transform(fileInputStream);

        System.out.println(byteArrayOutputStream.toString());
        FeatureCollection featureCollection =
                new ObjectMapper().readValue(byteArrayOutputStream.toString(), FeatureCollection.class);

        assertThat(featureCollection).isNotNull();
        assertThat(featureCollection.getFeatures())
                .isNotEmpty()
                .extracting(Feature::getId)
                .contains("NSR:StopPlace:1", "NSR:StopPlace:10", "NSR:Quay:8", "NSR:Parking:99", "VKT:TariffZone:729", "VKT:TariffZone:730");

        System.out.println(featureCollection);

        assertThat(featureCollection.getFeatures())
                .extracting(Feature::getGeometry).doesNotContainNull();

        assertThat(resolvePropertiesByValue(featureCollection, "name"))
                .contains("Drangedal stasjon", "Paradis");


        assertThat(resolvePropertiesByValue(featureCollection, "entityType"))
                .contains("StopPlace", "StopPlace", "Parking", "Quay", "TariffZone");

        assertThat(resolvePropertiesByValue(featureCollection, "finalStopPlaceType"))
                .contains("onstreetBus", "railStation");
    }

    /**
     * Regression test for the instance-level statefulness defect in
     * DeliveryPublicationStreamToGeoJson. The stopPlaces, parkings and tariffZones
     * sets are initialised in the constructor and never cleared between calls.
     * Calling transform() a second time on the same bean (which Spring uses as a
     * singleton) accumulates entities from the first call into the output.
     *
     * This test documents the current behaviour. A correct fix would clear the
     * collections at the start of each transform() invocation.
     */
    @Test
    public void secondTransformCallOnSameInstanceAccumulatesStateFromFirstCall() throws Exception {
        // First call — valid input
        FileInputStream firstInput = new FileInputStream(SRC_TEST_RESOURCES_PUBLICATION_DELIVERY_XML);
        ByteArrayOutputStream firstOutput = (ByteArrayOutputStream) deliveryPublicationStreamToGeoJson.transform(firstInput);
        FeatureCollection firstCollection = new ObjectMapper().readValue(firstOutput.toString(), FeatureCollection.class);
        int firstCount = firstCollection.getFeatures().size();

        // Second call — completely empty NeTEx (no stops, parkings or tariff zones)
        String emptyNeTEx = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
                + "<PublicationDelivery xmlns=\"http://www.netex.org.uk/netex\" version=\"139\">"
                + "<PublicationTimestamp>2018-01-16T02:58:31.377</PublicationTimestamp>"
                + "<ParticipantRef>NSR</ParticipantRef>"
                + "<dataObjects>"
                + "<SiteFrame modification=\"new\" version=\"1\" id=\"NSR:SiteFrame:1\"/>"
                + "</dataObjects>"
                + "</PublicationDelivery>";

        ByteArrayOutputStream secondOutput = (ByteArrayOutputStream) deliveryPublicationStreamToGeoJson.transform(
                new ByteArrayInputStream(emptyNeTEx.getBytes(StandardCharsets.UTF_8)));
        FeatureCollection secondCollection = new ObjectMapper().readValue(secondOutput.toString(), FeatureCollection.class);

        // A stateless implementation with empty input would produce 0 features.
        // Due to accumulated state the second call still contains data from the first call.
        assertThat(secondCollection.getFeatures()).isNotEmpty();
        List<String> firstIds = firstCollection.getFeatures().stream()
                .map(Feature::getId)
                .collect(Collectors.toList());
        assertThat(secondCollection.getFeatures())
                .extracting(Feature::getId)
                .containsAll(firstIds);
    }

    /**
     * Ensures that malformed XML causes a RuntimeException to be thrown rather
     * than silently producing empty or partial output.
     */
    @Test(expected = RuntimeException.class)
    public void transformThrowsRuntimeExceptionOnMalformedXml() throws JAXBException {
        DeliveryPublicationStreamToGeoJson freshInstance = new DeliveryPublicationStreamToGeoJson(
                stopPlaceToGeoJsonFeatureMapper,
                parkingToGeoJsonFeatureMapper,
                quayToGeoJsonFeatureMapper,
                tariffZoneToGeoJsonFeatureMapper,
                validityFilter);

        freshInstance.transform(new ByteArrayInputStream("<<<<not valid xml".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * Verifies that a TariffZone without any geometry (no polygon, no centroid) is
     * silently skipped and does NOT appear in the GeoJSON output.
     * VKT:TariffZone:788 in publication-delivery.xml has no geometry.
     */
    @Test
    public void tariffZoneWithoutGeometryIsExcludedFromOutput() throws Exception {
        FileInputStream fileInputStream = new FileInputStream(SRC_TEST_RESOURCES_PUBLICATION_DELIVERY_XML);
        ByteArrayOutputStream output = (ByteArrayOutputStream) new DeliveryPublicationStreamToGeoJson(
                stopPlaceToGeoJsonFeatureMapper,
                parkingToGeoJsonFeatureMapper,
                quayToGeoJsonFeatureMapper,
                tariffZoneToGeoJsonFeatureMapper,
                validityFilter).transform(fileInputStream);

        FeatureCollection featureCollection = new ObjectMapper().readValue(output.toString(), FeatureCollection.class);

        assertThat(featureCollection.getFeatures())
                .extracting(Feature::getId)
                .doesNotContain("VKT:TariffZone:788");
    }

    /**
     * Verifies that an expired stop place (ToDate in the past) is excluded from output.
     * NSR:StopPlace:22 in publication-delivery.xml has ToDate=2017-06-20 (expired).
     */
    @Test
    public void expiredStopPlaceIsExcludedFromOutput() throws Exception {
        FileInputStream fileInputStream = new FileInputStream(SRC_TEST_RESOURCES_PUBLICATION_DELIVERY_XML);
        ByteArrayOutputStream output = (ByteArrayOutputStream) new DeliveryPublicationStreamToGeoJson(
                stopPlaceToGeoJsonFeatureMapper,
                parkingToGeoJsonFeatureMapper,
                quayToGeoJsonFeatureMapper,
                tariffZoneToGeoJsonFeatureMapper,
                validityFilter).transform(fileInputStream);

        FeatureCollection featureCollection = new ObjectMapper().readValue(output.toString(), FeatureCollection.class);

        assertThat(featureCollection.getFeatures())
                .extracting(Feature::getId)
                .doesNotContain("NSR:StopPlace:22");
    }

    private List<String> resolvePropertiesByValue(FeatureCollection featureCollection, String key) {
        return featureCollection.getFeatures()
                .stream()
                .map(Feature::getProperties)
                .flatMap(properties -> properties.entrySet().stream())
                .filter(entrySet -> entrySet.getKey().equals(key))
                .map(Map.Entry::getValue)
                .map(object -> (String) object)
                .collect(toList());
    }
}