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
import org.geojson.Feature;
import org.geojson.Point;
import org.junit.Test;
import org.rutebanken.netex.model.*;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.mapper.ZoneToGeoJsonFeatureMapper.DESCRIPTION;
import static org.entur.asag.mapbox.mapper.ZoneToGeoJsonFeatureMapper.LANG;
import static org.entur.asag.mapbox.mapper.ZoneToGeoJsonFeatureMapper.NAME;


public class ZoneToGeoJsonFeatureMapperTest {


    @Test
    public void testMapZoneToFeature() throws JsonProcessingException {

        String stopPlaceName = "A name";

        double longitude = 59.120694;
        double latitude = 11.386149;
        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withName(new MultilingualString().withValue(stopPlaceName))
                .withDescription(new MultilingualString()
                        .withValue("description")
                        .withLang("nor"))
                .withCentroid(new SimplePoint_VersionStructure()
                        .withLocation(new LocationStructure()
                                .withLatitude(BigDecimal.valueOf(latitude)).withLongitude(BigDecimal.valueOf(longitude))));


        Feature feature = new ZoneToGeoJsonFeatureMapper().mapZoneToGeoJson(stopPlace);


        assertThat(feature).isNotNull();
        assertThat(feature.getId()).isEqualTo(stopPlace.getId());
        assertThat(feature.getGeometry()).isNotNull();
        assertThat(feature.getGeometry()).isInstanceOf(Point.class);

        String name = (String) feature.getProperties().get(NAME);
        assertThat(name).as("stopPlaceName").isEqualTo(stopPlaceName);

        String description = (String) feature.getProperties().get(DESCRIPTION);
        assertThat(description).isEqualTo("description");

        String descriptionLang = (String) feature.getProperties().get(DESCRIPTION+LANG);
        assertThat(descriptionLang).isEqualTo("nor");

        Point point = (Point) feature.getGeometry();

        assertThat(point.getCoordinates()).isNotNull();
        assertThat(point.getCoordinates().getLatitude()).isEqualTo(latitude);
        assertThat(point.getCoordinates().getLongitude()).isEqualTo(longitude);

        String value = new ObjectMapper().writeValueAsString(feature);
        System.out.println(value);
    }

}