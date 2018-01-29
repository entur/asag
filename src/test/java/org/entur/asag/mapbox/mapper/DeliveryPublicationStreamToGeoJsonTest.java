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

import javax.xml.bind.JAXBException;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;
import static org.assertj.core.api.Assertions.assertThat;

public class DeliveryPublicationStreamToGeoJsonTest {


    private ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper = new ZoneToGeoJsonFeatureMapper();
    private QuayToGeoJsonFeatureMapper quayToGeoJsonFeatureMapper = new QuayToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper = new StopPlaceToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private ValidityFilter validityFilter = new ValidityFilter();
    private ParkingToGeoJsonFeatureMapper parkingToGeoJsonFeatureMapper = new ParkingToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);
    private DeliveryPublicationStreamToGeoJson deliveryPublicationStreamToGeoJson = new DeliveryPublicationStreamToGeoJson(stopPlaceToGeoJsonFeatureMapper, parkingToGeoJsonFeatureMapper, quayToGeoJsonFeatureMapper, validityFilter);

    public DeliveryPublicationStreamToGeoJsonTest() throws JAXBException {
    }

    @Test
    public void transform() throws Exception {

        FileInputStream fileInputStream = new FileInputStream("src/test/resources/publication-delivery.xml");

        ByteArrayOutputStream byteArrayOutputStream = (ByteArrayOutputStream) deliveryPublicationStreamToGeoJson.transform(fileInputStream);

        System.out.println(byteArrayOutputStream.toString());
        FeatureCollection featureCollection =
                new ObjectMapper().readValue(byteArrayOutputStream.toString(), FeatureCollection.class);

        assertThat(featureCollection).isNotNull();
        assertThat(featureCollection.getFeatures())
                .isNotEmpty()
                .extracting(Feature::getId)
                .containsExactly("NSR:StopPlace:1", "NSR:StopPlace:10", "NSR:Quay:8", "NSR:Parking:99");

        System.out.println(featureCollection);

        assertThat(featureCollection.getFeatures())
                .extracting(Feature::getGeometry).doesNotContainNull();

        assertThat(resolvePropertiesByValue(featureCollection, "name"))
                .contains("Drangedal stasjon", "Paradis");


        assertThat(resolvePropertiesByValue(featureCollection, "entityType"))
                .contains("StopPlace", "StopPlace", "Parking", "Quay");

        assertThat(resolvePropertiesByValue(featureCollection, "finalStopPlaceType"))
                .contains("onstreetBus", "railStation");
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