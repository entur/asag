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
import org.rutebanken.netex.model.Parking;
import org.rutebanken.netex.model.ParkingVehicleEnumeration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.entur.asag.mapbox.mapper.MapperHelper.setIfNotNull;
import static org.entur.asag.mapbox.mapper.MapperHelper.setResolvedValue;

@Service
public class ParkingToGeoJsonFeatureMapper {

    static final String PUBLIC_CODE = "publicCode";
    static final String HAS_PARENT_SITE_REF = "hasParentSiteRef";

    private static final Logger logger = LoggerFactory.getLogger(ParkingToGeoJsonFeatureMapper.class);
    public static final String TOTAL_CAPACITY = "totalCapacity";
    public static final String PRINCIPAL_CAPACITY = "principalCapacity";
    public static final String NUMBER_OF_PARKING_LEVELS = "numberOfParkingLevels";
    public static final String COVERED = "covered";
    public static final String PARKING_VEHICLE_TYPES = "parkingVehicleTypes";

    private final ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper;

    @Autowired
    public ParkingToGeoJsonFeatureMapper(ZoneToGeoJsonFeatureMapper zoneToGeoJsonFeatureMapper) {
        this.zoneToGeoJsonFeatureMapper = zoneToGeoJsonFeatureMapper;
    }

    public Feature mapParkingToGeoJson(Parking parking) {
        Feature feature = zoneToGeoJsonFeatureMapper.mapZoneToGeoJson(parking);

        setIfNotNull(TOTAL_CAPACITY, parking.getTotalCapacity(), feature::setProperty);
        setIfNotNull(PRINCIPAL_CAPACITY, parking.getPrincipalCapacity(), feature::setProperty);
        setIfNotNull(NUMBER_OF_PARKING_LEVELS, parking.getNumberOfParkingLevels(), feature::setProperty);
        setIfNotNull(PUBLIC_CODE, parking.getPublicCode(), feature::setProperty);
        setResolvedValue(COVERED, parking.getCovered(), feature::setProperty);

        feature.setProperty(PARKING_VEHICLE_TYPES, Stream.of(parking.getParkingVehicleTypes())
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(ParkingVehicleEnumeration::value)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(",")));

        feature.setProperty(HAS_PARENT_SITE_REF, String.valueOf(parking.getParentSiteRef() != null));

        return feature;

    }

}
