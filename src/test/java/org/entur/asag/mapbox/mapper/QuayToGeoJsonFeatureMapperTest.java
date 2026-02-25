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
import org.rutebanken.netex.model.Quay;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.entur.asag.mapbox.mapper.QuayToGeoJsonFeatureMapper.PUBLIC_CODE;

public class QuayToGeoJsonFeatureMapperTest {

    private QuayToGeoJsonFeatureMapper quayToGeoJsonFeatureMapper = new QuayToGeoJsonFeatureMapper(new ZoneToGeoJsonFeatureMapper());

    @Test
    public void mapQuayToGeojsonFeature() throws Exception {
        Quay quay = new Quay();
        quay.setPublicCode("publicCode");

        Feature feature = quayToGeoJsonFeatureMapper.mapQuayToGeojsonFeature(quay);

        assertThat(feature.getProperty(PUBLIC_CODE).toString()).isEqualTo("publicCode");
    }

}