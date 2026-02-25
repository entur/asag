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

import jakarta.xml.bind.JAXBElement;
import org.geojson.Feature;
import org.rutebanken.netex.model.Quay;
import org.rutebanken.netex.model.Quays_RelStructure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static org.entur.asag.mapbox.mapper.MapperHelper.setIfNotNull;

@Service
public class QuayToGeoJsonFeatureMapper {

    static final String PUBLIC_CODE = "publicCode";

    private ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper;

    @Autowired
    public QuayToGeoJsonFeatureMapper(ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper) {
        this.zoneToGeoJsonFeatureMapper = zoneToGeoJsonFeatureMapper;
    }

    public Set<Feature> mapQuaysToGeojsonFeatures(Quays_RelStructure quays_relStructure) {

        Set<Feature> mappedQuays = new HashSet<>();
        if (quays_relStructure != null && !CollectionUtils.isEmpty(quays_relStructure.getQuayRefOrQuay())) {
            return quays_relStructure.getQuayRefOrQuay().stream()
                    .filter(Objects::nonNull)
                    .map(JAXBElement::getValue)
                    .filter(o -> o instanceof Quay)
                    .map(o -> (Quay) o)
                    .map(this::mapQuayToGeojsonFeature)
                    .collect(Collectors.toSet());
        }
        return mappedQuays;
    }

    public Feature mapQuayToGeojsonFeature(Quay quay) {

        Feature feature = zoneToGeoJsonFeatureMapper.mapZoneToGeoJson(quay);
        setIfNotNull(PUBLIC_CODE, quay.getPublicCode(), feature::setProperty);
        return feature;

    }
}
