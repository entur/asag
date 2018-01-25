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

import org.geojson.Feature;
import org.junit.Test;
import org.rutebanken.netex.model.*;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.*;

public class StopPlaceToGeoJsonFeatureMapperTest {

    private ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper = new ZoneToGeoJsonFeatureMapper();

    private StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper = new StopPlaceToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);

    @Test
    public void testMappingStopPlaceToGeojson() {

        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withStopPlaceType(StopTypeEnumeration.BUS_STATION)
                .withAirSubmode(AirSubmodeEnumeration.AIRSHIP_SERVICE)
                .withWeighting(InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE)
                .withKeyList(new KeyListStructure()
                    .withKeyValue(new KeyValueStructure()
                            .withKey(NETEX_IS_PARENT_STOP_PLACE)
                            .withValue("true")))
                .withParentSiteRef(new SiteRefStructure()
                        .withRef("NSR:StopPlace:2")
                        .withVersion("2"));


        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace);

        Map<String, Object> properties = feature.getProperties();

        assertThat(properties.get(STOP_PLACE_TYPE)).isEqualTo(stopPlace.getStopPlaceType().value());
        assertThat(properties.get(SUBMODE)).isEqualTo(stopPlace.getAirSubmode().value());
        assertThat(properties.get(FINAL_STOP_PLACE_TYPE)).isEqualTo(stopPlace.getAirSubmode().value());
        assertThat(properties.get(HAS_PARENT_SITE_REF)).isEqualTo("true");
        assertThat(properties.get(IS_PARENT_STOP_PLACE)).isEqualTo("true");
        assertThat(properties.get(WEIGHTING)).isEqualTo(stopPlace.getWeighting().value());

    }

    @Test
    public void testMappingStopPlaceWithoutSubmodeToGeojson() {

        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withStopPlaceType(StopTypeEnumeration.BUS_STATION);

        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace);

        Map<String, Object> properties = feature.getProperties();

        assertThat(properties.get(FINAL_STOP_PLACE_TYPE)).isEqualTo(stopPlace.getStopPlaceType().value());
    }

}