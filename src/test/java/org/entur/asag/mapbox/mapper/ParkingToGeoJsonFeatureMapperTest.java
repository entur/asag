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
import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.CoveredEnumeration;
import org.rutebanken.netex.model.Parking;
import org.rutebanken.netex.model.ParkingVehicleEnumeration;

import java.math.BigInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.entur.asag.mapbox.mapper.ParkingToGeoJsonFeatureMapper.*;

public class ParkingToGeoJsonFeatureMapperTest {


    private ParkingToGeoJsonFeatureMapper parkingToGeoJsonFeatureMapper = new ParkingToGeoJsonFeatureMapper(new ZoneToGeoJsonFeatureMapper());


    @Test
    public void mapParkingToGeoJson() throws Exception {
        Parking parking = new Parking();
        parking.setTotalCapacity(BigInteger.valueOf(1L));
        ParkingVehicleEnumeration allPassengerVehicles = ParkingVehicleEnumeration.ALL_PASSENGER_VEHICLES;
        parking.getParkingVehicleTypes().add(allPassengerVehicles);
        parking.setPrincipalCapacity(BigInteger.valueOf(2));
        parking.setId("NSR:Parking:666");
        parking.setCovered(CoveredEnumeration.MIXED);

        Feature feature = parkingToGeoJsonFeatureMapper.mapParkingToGeoJson(parking);

        assertThat(feature.getProperty(TOTAL_CAPACITY).toString()).isEqualTo("1");
        assertThat(feature.getProperty(PRINCIPAL_CAPACITY).toString()).isEqualTo("2");
        assertThat(feature.getProperty(COVERED).toString()).isEqualTo("mixed");
        assertThat(feature.getProperty(PARKING_VEHICLE_TYPES).toString()).isEqualTo(allPassengerVehicles.value());
    }

}