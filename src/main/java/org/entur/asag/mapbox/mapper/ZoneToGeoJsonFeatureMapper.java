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

import com.google.common.base.CharMatcher;
import net.opengis.gml._3.AbstractRingPropertyType;
import net.opengis.gml._3.DirectPositionListType;
import net.opengis.gml._3.LinearRingType;
import org.geojson.Feature;
import org.geojson.LngLatAlt;
import org.geojson.Point;
import org.geojson.Polygon;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBElement;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.entur.asag.mapbox.mapper.MapperHelper.*;

@Service
public class ZoneToGeoJsonFeatureMapper {

    private static final Logger logger = LoggerFactory.getLogger(ZoneToGeoJsonFeatureMapper.class);

    public static final String ID = "id";
    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String ENTITY_TYPE = "entityType";
    public static final String PRIVATE_CODE = "privateCode";
    public static final String CODE_SPACE = "codeSpace";


    public Feature mapZoneToGeoJson(Zone_VersionStructure zone) {

        Feature feature = new Feature();
        feature.setId(zone.getId());

        mapMultilingualString(NAME, feature, zone.getName());
        mapMultilingualString(DESCRIPTION, feature, zone.getDescription());
        setPrivateCode(PRIVATE_CODE, zone.getPrivateCode(), feature::setProperty);
        setIfNotNull(ID, zone.getId(), feature::setProperty);
        parseAndMapCodeSpace(zone, feature);
        feature.setProperty(ENTITY_TYPE, zone.getClass().getSimpleName());

        mapGeometry(zone, feature);

        return feature;
    }

    public void parseAndMapCodeSpace(Zone_VersionStructure zone, Feature feature) {
        if(zone.getId() != null) {
            if(CharMatcher.is(':').countIn(zone.getId()) == 2) {
                feature.setProperty(CODE_SPACE, zone.getId().split(":")[0]);
            }
        }
    }

    public void mapGeometry(Zone_VersionStructure zone, Feature feature) {
            if (zone.getCentroid() != null && zone.getCentroid().getLocation() != null) {
                double latitude = zone.getCentroid().getLocation().getLatitude().doubleValue();
                double longitude = zone.getCentroid().getLocation().getLongitude().doubleValue();

                LngLatAlt lngLatAlt = new LngLatAlt(longitude, latitude);
                Point multiPoint = new Point(lngLatAlt);
                feature.setGeometry(multiPoint);
            } else if (zone.getPolygon() != null) {
                List<Double> doubles = extractValues(zone.getPolygon().getExterior());
                Polygon polygon = new Polygon(convertCoordinateListToLngLatList(doubles));
                feature.setGeometry(polygon);
            } else {
                logger.warn("Cannot find centroid or polygon for Zone with ID: " + zone.getId());
            }
        }

    public List<LngLatAlt> convertCoordinateListToLngLatList(List<Double> coordinateList) {
        List<LngLatAlt> lngLatAlts = new ArrayList<>();
        for (int index = 0; index < coordinateList.size(); index += 2) {
            LngLatAlt lngLatAlt = new LngLatAlt(coordinateList.get(index + 1), coordinateList.get(index));
            lngLatAlts.add(lngLatAlt);
        }
        return lngLatAlts;

    }


    public List<Double> extractValues(AbstractRingPropertyType abstractRingPropertyType) {
        return Optional.of(abstractRingPropertyType)
                .map(AbstractRingPropertyType::getAbstractRing)
                .map(JAXBElement::getValue)
                .map(abstractRing -> ((LinearRingType) abstractRing))
                .map(LinearRingType::getPosList)
                .map(DirectPositionListType::getValue)
                .get();
    }
}