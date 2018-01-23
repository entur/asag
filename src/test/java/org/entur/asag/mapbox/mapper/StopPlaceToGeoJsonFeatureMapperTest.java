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
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.IS_PARENT_STOP_PLACE;

public class StopPlaceToGeoJsonFeatureMapperTest {

    @Test
    public void testMappingStopPlaceToGeojson() {

        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withStopPlaceType(StopTypeEnumeration.BUS_STATION)
                .withAirSubmode(AirSubmodeEnumeration.AIRSHIP_SERVICE)
                .withWeighting(InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE)
                .withKeyList(new KeyListStructure()
                    .withKeyValue(new KeyValueStructure()
                            .withKey(IS_PARENT_STOP_PLACE)
                            .withValue("true")))
                .withParentSiteRef(new SiteRefStructure()
                        .withRef("NSR:StopPlace:2")
                        .withVersion("2"));

        StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper = new StopPlaceToGeoJsonFeatureMapper(new ZoneToGeoJsonFeatureMapper());

        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace);

        Map<String, Object> properties = feature.getProperties();

        assertThat(properties.get("stopPlaceType")).isEqualTo(stopPlace.getStopPlaceType().value());
        assertThat(properties.get("submode")).isEqualTo(stopPlace.getAirSubmode().value());
        assertThat(properties.get("hasParentSiteRef")).isEqualTo(true);
        assertThat(properties.get("isParentStopPlace")).isEqualTo(true);

    }

}