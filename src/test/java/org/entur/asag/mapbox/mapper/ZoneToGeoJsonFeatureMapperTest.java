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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.opengis.gml._3.*;
import net.opengis.gml._3.ObjectFactory;
import org.geojson.Feature;
import org.geojson.Point;
import org.geojson.Polygon;
import org.junit.Test;
import org.rutebanken.netex.model.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.mapper.MapperHelper.LANG;
import static org.entur.asag.mapbox.mapper.ZoneToGeoJsonFeatureMapper.*;


public class ZoneToGeoJsonFeatureMapperTest {

    private static final net.opengis.gml._3.ObjectFactory openGisObjectFactory = new ObjectFactory();

    @Test
    public void testMapZoneToFeature() throws JsonProcessingException {

        String stopPlaceName = "A name";

        double longitude = 59.120694;
        double latitude = 11.386149;

        // Test fields not specific for stop place, but for zone
        Zone_VersionStructure zone = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withName(new MultilingualString().withValue(stopPlaceName))
                .withDescription(new MultilingualString()
                        .withValue("description")
                        .withLang("nor"))
                .withPrivateCode(new PrivateCodeStructure().withValue("privateCode"))
                .withCentroid(new SimplePoint_VersionStructure()
                        .withLocation(new LocationStructure()
                                .withLatitude(BigDecimal.valueOf(latitude)).withLongitude(BigDecimal.valueOf(longitude))));

        Feature feature = new ZoneToGeoJsonFeatureMapper().mapZoneToGeoJson(zone);


        assertThat(feature).isNotNull();
        assertThat(feature.getId()).isEqualTo(zone.getId());
        assertThat(feature.getGeometry()).isNotNull();
        assertThat(feature.getGeometry()).isInstanceOf(Point.class);

        String name = (String) feature.getProperties().get(NAME);
        assertThat(name).as("stopPlaceName").isEqualTo(stopPlaceName);

        String description = (String) feature.getProperties().get(DESCRIPTION);
        assertThat(description).isEqualTo("description");

        String descriptionLang = (String) feature.getProperties().get(DESCRIPTION + LANG);
        assertThat(descriptionLang).isEqualTo("nor");

        Point point = (Point) feature.getGeometry();

        assertThat(point.getCoordinates()).isNotNull();
        assertThat(point.getCoordinates().getLatitude()).isEqualTo(latitude);
        assertThat(point.getCoordinates().getLongitude()).isEqualTo(longitude);

        assertThat(feature.getProperty(PRIVATE_CODE).toString()).isEqualTo("privateCode");
        assertThat(feature.getProperty(ID).toString()).isEqualTo(zone.getId());

        String value = new ObjectMapper().writeValueAsString(feature);
        System.out.println(value);
    }


    @Test
    public void testMapZoneWithPolygonToFeature() throws JsonProcessingException {

        String zoneName = "A name";

        PolygonType polygonType = createPolygonType();


        // Test fields not specific for stop place, but for zone
        Zone_VersionStructure zone = new TariffZone()
                .withId("NSR:TariffZone:1")
                .withName(new MultilingualString().withValue(zoneName))
                .withPolygon(polygonType);

        Feature feature = new ZoneToGeoJsonFeatureMapper().mapZoneToGeoJson(zone);


        assertThat(feature).isNotNull();
        assertThat(feature.getId()).isEqualTo(zone.getId());
        assertThat(feature.getGeometry())
                .as("feature.geometry")
                .isNotNull()
                .isInstanceOf(Polygon.class);

        Polygon polygon = (Polygon) feature.getGeometry();

        assertThat(polygon.getCoordinates())
                .isNotNull()
                .extracting(lngLatAlts -> lngLatAlts.get(0))
                .extracting(list -> list.getLatitude() + ", " + list.getLongitude())
                .contains("9.8468, 59.2649");

        String value = new ObjectMapper().writeValueAsString(feature);
        System.out.println(value);
    }
    private PolygonType createPolygonType() {
        List<Double> values = new ArrayList<>();
        values.add(9.8468);
        values.add(59.2649);
        values.add(9.8456);
        values.add(59.2654);
        values.add(9.8457);
        values.add(59.2655);
        values.add(9.8443);
        values.add(59.2663);
        values.add(values.get(0));
        values.add(values.get(1));

        DirectPositionListType positionList = new DirectPositionListType().withValue(values);

        LinearRingType linearRing = new LinearRingType()
                .withPosList(positionList);

        PolygonType polygonType = new PolygonType()
                .withId("KVE-07")
                .withExterior(new AbstractRingPropertyType()
                        .withAbstractRing(openGisObjectFactory.createLinearRing(linearRing)));

        return polygonType;

    }
}