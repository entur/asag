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
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import static org.entur.asag.mapbox.mapper.MapperHelper.mapMultilingualString;
import static org.entur.asag.mapbox.mapper.MapperHelper.setPrivateCode;

@Service
public class ZoneToGeoJsonFeatureMapper {

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String ENTITY_TYPE = "entityType";
    public static final String PRIVATE_CODE = "privateCode";
    private static final Logger logger = LoggerFactory.getLogger(ZoneToGeoJsonFeatureMapper.class);

    public Feature mapZoneToGeoJson(Zone_VersionStructure zone) {

        Feature feature = new Feature();
        feature.setId(zone.getId());

        mapMultilingualString(NAME, feature, zone.getName());
        mapMultilingualString(DESCRIPTION, feature, zone.getDescription());
        setPrivateCode(PRIVATE_CODE, zone.getPrivateCode(), feature::setProperty);

        feature.setProperty(ENTITY_TYPE, zone.getClass().getSimpleName());

        if (zone.getCentroid() != null && zone.getCentroid().getLocation() != null) {
            double latitude = zone.getCentroid().getLocation().getLatitude().doubleValue();
            double longitude = zone.getCentroid().getLocation().getLongitude().doubleValue();

            LngLatAlt lngLatAlt = new LngLatAlt(longitude, latitude);
            Point multiPoint = new Point(lngLatAlt);
            feature.setGeometry(multiPoint);
        } else {
            logger.warn("Cannot find centroid for Zone with ID: " + zone.getId());
        }

        return feature;
    }


}
