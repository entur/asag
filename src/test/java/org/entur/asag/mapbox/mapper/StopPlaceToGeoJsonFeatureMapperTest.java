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

import org.entur.asag.netex.PublicationDeliveryHelper;
import org.geojson.Feature;
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.AirSubmodeEnumeration;
import org.rutebanken.netex.model.EntityStructure;
import org.rutebanken.netex.model.InterchangeWeightingEnumeration;
import org.rutebanken.netex.model.KeyListStructure;
import org.rutebanken.netex.model.KeyValueStructure;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.SiteRefStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.StopTypeEnumeration;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.ADJACENT_SITES;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.FINAL_STOP_PLACE_TYPE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.HAS_PARENT_SITE_REF;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.IS_PARENT_STOP_PLACE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.IS_PRIMARY_ADJACENT_SITE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.NETEX_IS_PARENT_STOP_PLACE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.PUBLIC_CODE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.STOP_PLACE_TYPE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.SUBMODE;
import static org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper.WEIGHTING;

public class StopPlaceToGeoJsonFeatureMapperTest {

    private ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper = new ZoneToGeoJsonFeatureMapper();

    private StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper = new StopPlaceToGeoJsonFeatureMapper(zoneToGeoJsonFeatureMapper);

    @Test
    public void testStopPlaceContainsMultipleAdjacentSites() throws Exception {
        File initialFile = new File("src/test/resources/adjacent_sites_netex.xml");
        InputStream targetStream = new FileInputStream(initialFile);

        PublicationDeliveryStructure publicationDeliveryStructure = PublicationDeliveryHelper.unmarshall(targetStream);
        Map<String, String> stopPlaceType = PublicationDeliveryHelper.resolveStops(publicationDeliveryStructure)
                .filter(p -> p.getStopPlaceType() !=null)
                .collect(Collectors.toMap(EntityStructure::getId, s -> s.getStopPlaceType().value()));

        Optional<StopPlace> first = PublicationDeliveryHelper.resolveStops(publicationDeliveryStructure).findFirst();

        StopPlace stopPlace = first.get();

        Set<String> adjacentSites = PublicationDeliveryHelper.resolveAdjacentSites(stopPlace);
        Set<String> adjacentSitesTypes = new HashSet<>();
        if (!adjacentSites.isEmpty()) {
            for (String siteRef : adjacentSites) {
                String adjacentSiteType = stopPlaceType.get(siteRef);
                adjacentSitesTypes.add(adjacentSiteType);

            }
        }
        adjacentSitesTypes.add(stopPlace.getStopPlaceType().value());

        String final_stop_place_type = adjacentSitesTypes.stream().sorted().collect(Collectors.joining("_"));
        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace, final_stop_place_type);

        Map<String, Object> properties = feature.getProperties();

        Set<String> result = new HashSet<>();
        result.addAll((Collection<? extends String>) properties.get(ADJACENT_SITES));
        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(2);
        assertThat(result).contains("NSR:StopPlace:59880");
        assertThat(properties.get(IS_PRIMARY_ADJACENT_SITE)).isEqualTo("true");
        assertThat(properties.get(STOP_PLACE_TYPE)).isEqualTo(stopPlace.getStopPlaceType().value());
        assertThat(properties.get(HAS_PARENT_SITE_REF)).isEqualTo("true");
        assertThat(properties.get(IS_PARENT_STOP_PLACE)).isEqualTo("false");
        assertThat(properties.get(WEIGHTING)).isEqualTo(stopPlace.getWeighting().value());
        assertThat(feature.getProperties().get(PUBLIC_CODE)).isEqualTo(stopPlace.getPublicCode());

    }

    @Test
    public void testMappingStopPlaceToGeojson() {

        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withStopPlaceType(StopTypeEnumeration.BUS_STATION)
                .withAirSubmode(AirSubmodeEnumeration.AIRSHIP_SERVICE)
                .withWeighting(InterchangeWeightingEnumeration.PREFERRED_INTERCHANGE)
                .withPublicCode("Public code")
                .withKeyList(new KeyListStructure()
                        .withKeyValue(new KeyValueStructure()
                                .withKey(NETEX_IS_PARENT_STOP_PLACE)
                                .withValue("true")))
                .withParentSiteRef(new SiteRefStructure()
                        .withRef("NSR:StopPlace:2")
                        .withVersion("2"));


        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace, StopTypeEnumeration.BUS_STATION.value());

        Map<String, Object> properties = feature.getProperties();

        assertThat(properties.get(STOP_PLACE_TYPE)).isEqualTo(stopPlace.getStopPlaceType().value());
        assertThat(properties.get(SUBMODE)).isEqualTo(stopPlace.getAirSubmode().value());
        assertThat(properties.get(FINAL_STOP_PLACE_TYPE)).isEqualTo(stopPlace.getAirSubmode().value());
        assertThat(properties.get(HAS_PARENT_SITE_REF)).isEqualTo("true");
        assertThat(properties.get(IS_PARENT_STOP_PLACE)).isEqualTo("true");
        assertThat(properties.get(WEIGHTING)).isEqualTo(stopPlace.getWeighting().value());
        assertThat(feature.getProperties().get(PUBLIC_CODE)).isEqualTo(stopPlace.getPublicCode());
    }

    @Test
    public void testMappingStopPlaceWithoutSubmodeToGeojson() {

        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withStopPlaceType(StopTypeEnumeration.BUS_STATION);

        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace, StopTypeEnumeration.BUS_STATION.value());

        Map<String, Object> properties = feature.getProperties();

        assertThat(properties.get(FINAL_STOP_PLACE_TYPE)).isEqualTo(stopPlace.getStopPlaceType().value());
    }

}