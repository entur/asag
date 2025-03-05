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

package org.entur.asag.netex;

import org.entur.asag.mapbox.mapper.MapperHelper;
import org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper;
import org.rutebanken.netex.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static jakarta.xml.bind.JAXBContext.newInstance;

/**
 * Common useful methods for resolving parts of NeTEx
 */
public class PublicationDeliveryHelper {
    private static final Logger logger = LoggerFactory.getLogger(StopPlaceToGeoJsonFeatureMapper.class);


    public static Stream<StopPlace> resolveStops(PublicationDeliveryStructure publicationDelivery) {

        return resolveSiteFrames(publicationDelivery)
                .filter(siteFrame -> siteFrame.getStopPlaces() != null)
                .map(Site_VersionFrameStructure::getStopPlaces)
                .filter(Objects::nonNull)
                .map(StopPlacesInFrame_RelStructure::getStopPlace)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream);
    }

    public static Stream<SiteFrame> resolveSiteFrames(PublicationDeliveryStructure publicationDeliveryStructure) {
        return publicationDeliveryStructure.getDataObjects().getCompositeFrameOrCommonFrame().stream()
                .map(JAXBElement::getValue)
                .filter(commonVersionFrame -> commonVersionFrame instanceof SiteFrame || commonVersionFrame instanceof CompositeFrame)
                .flatMap(PublicationDeliveryHelper::resolveSiteFramesFromCommonFrame);
    }

    public static Stream<SiteFrame> resolveSiteFramesFromCommonFrame(Common_VersionFrameStructure commonVersionFrame) {
        if (commonVersionFrame instanceof SiteFrame) {
            return Stream.of((SiteFrame) commonVersionFrame);
        } else if (commonVersionFrame instanceof CompositeFrame){
            return ((CompositeFrame) commonVersionFrame).getFrames().getCommonFrame().stream()
                    .map(JAXBElement::getValue)
                    .filter(commonFrame -> commonFrame instanceof SiteFrame)
                    .map(commonFrame -> (SiteFrame) commonFrame);
        } else return Stream.empty();
    }

    public static PublicationDeliveryStructure unmarshall(InputStream in) throws Exception {
        JAXBElement<PublicationDeliveryStructure> jaxbElement = createUnmarshaller().unmarshal(new StreamSource(in), PublicationDeliveryStructure.class);
        return jaxbElement.getValue();
    }

    public static Unmarshaller createUnmarshaller() throws JAXBException {
        JAXBContext publicationDeliveryContext = newInstance(PublicationDeliveryStructure.class);
        return publicationDeliveryContext.createUnmarshaller();
    }

    public static TreeSet<String> resolveAdjacentSites(StopPlace stopPlace) {
        TreeSet<String> siteRefs = new TreeSet<>(String::compareToIgnoreCase);
        Optional<SiteRefs_RelStructure> adjacentSites = Optional.ofNullable(stopPlace.getAdjacentSites());

        adjacentSites.ifPresent(adjacentSite -> {
            Set<String> collect = adjacentSite.getSiteRef().stream()
                    .map(s -> s.getValue().getRef())
                    .collect(Collectors.toSet());
            siteRefs.addAll(collect);

        });
        return siteRefs;
    }

    public static Optional<String> resolveFirstSubmodeToSingleValue(StopPlace stopPlace) {
        return Arrays.stream(StopPlace_VersionStructure.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("get") && method.getName().endsWith("Submode"))
                .map(method -> safeInvoke(method, stopPlace))
                .filter(Objects::nonNull)
                .map(MapperHelper::getEnumValue)
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(value -> !"unknown".equals(value))
                .findAny();
    }


    private static Object safeInvoke(Method method, StopPlace stopPlace) {
        try {
            return method.invoke(stopPlace);
        } catch (InvocationTargetException | IllegalAccessException e) {
            logger.warn("Error resolving submode from stop place {}. Ignoring this method: {}", stopPlace.getId(), method.getName(), e);
        }
        return null;
    }
}
