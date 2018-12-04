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

package org.entur.asag.mapbox;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.io.IOUtils;
import org.entur.asag.mapbox.filter.ValidityFilter;
import org.entur.asag.mapbox.mapper.ParkingToGeoJsonFeatureMapper;
import org.entur.asag.mapbox.mapper.QuayToGeoJsonFeatureMapper;
import org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper;
import org.entur.asag.mapbox.mapper.TariffZoneToGeoJsonFeatureMapper;
import org.entur.asag.netex.PublicationDeliveryHelper;
import org.geojson.Feature;
import org.rutebanken.netex.model.EntityInVersionStructure;
import org.rutebanken.netex.model.EntityStructure;
import org.rutebanken.netex.model.Parking;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.StopPlace;
import org.rutebanken.netex.model.TariffZone;
import org.rutebanken.netex.model.Zone_VersionStructure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class DeliveryPublicationStreamToGeoJson {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPublicationStreamToGeoJson.class);

    private final StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper;

    private final ParkingToGeoJsonFeatureMapper parkingToGeoJsonFeatureMapper;

    private final QuayToGeoJsonFeatureMapper quayToGeoJsonFeatureMapper;

    private final TariffZoneToGeoJsonFeatureMapper tariffZoneToGeoJsonFeatureMapper;

    private final ValidityFilter validityFilter;
    private final Unmarshaller unmarshaller;
    private ObjectMapper jacksonObjectMapper = new ObjectMapper();
    private Map<Class, AtomicInteger> incrementorsByType = new HashMap<>();
    private Map<String, Class<? extends EntityInVersionStructure>> mappableTypes = new HashMap<>();
    private Map<String, String> stopPlaceTransportType;

    @Autowired
    public DeliveryPublicationStreamToGeoJson(StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper,
                                              ParkingToGeoJsonFeatureMapper parkingToGeoJsonFeatureMapper,
                                              QuayToGeoJsonFeatureMapper quayToGeoJsonFeatureMapper,
                                              TariffZoneToGeoJsonFeatureMapper tariffZoneToGeoJsonFeatureMapper,
                                              ValidityFilter validityFilter) throws JAXBException {
        this.stopPlaceToGeoJsonFeatureMapper = stopPlaceToGeoJsonFeatureMapper;
        this.parkingToGeoJsonFeatureMapper = parkingToGeoJsonFeatureMapper;
        this.quayToGeoJsonFeatureMapper = quayToGeoJsonFeatureMapper;
        this.tariffZoneToGeoJsonFeatureMapper = tariffZoneToGeoJsonFeatureMapper;
        this.validityFilter = validityFilter;
        unmarshaller = PublicationDeliveryHelper.createUnmarshaller();

        mappableTypes.put("StopPlace", StopPlace.class);
        mappableTypes.put("Parking", Parking.class);
        mappableTypes.put("TariffZone", TariffZone.class);
    }

    public OutputStream transform(InputStream publicationDeliveryStream) {
        return traverse(publicationDeliveryStream);
    }

    private OutputStream traverse(InputStream publicationDeliveryStream) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);

        boolean lastWasMapped = false;
        try {

            ByteArrayOutputStream boas = new ByteArrayOutputStream();
            IOUtils.copy(publicationDeliveryStream,boas);
            byte[] bytes = boas.toByteArray();

            writeFeatureCollectionStart(outputStreamWriter);


            PublicationDeliveryStructure publicationDeliveryStructure = PublicationDeliveryHelper.unmarshall(new ByteArrayInputStream(bytes));
            stopPlaceTransportType = PublicationDeliveryHelper.resolveStops(publicationDeliveryStructure)
                    .filter(p -> p.getStopPlaceType() !=null)
                    .collect(Collectors.toMap(stopPlace -> stopPlace.getId(), stopPlace -> getStopType(stopPlace)));

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(new ByteArrayInputStream(bytes));

            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.peek();

                if (xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();
                    String localPartOfName = startElement.getName().getLocalPart();
                    if (mappableTypes.containsKey(localPartOfName)) {
                        lastWasMapped = handle(localPartOfName,
                                lastWasMapped,
                                xmlEventReader,
                                mappableTypes.get(localPartOfName),
                                outputStream,
                                outputStreamWriter);
                    }
                }
                xmlEventReader.next();
            }

            writeFeatureCollectionEnd(outputStreamWriter);

        } catch (Exception e) {
            throw new RuntimeException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
        return outputStream;
    }

    private String getStopType(StopPlace stopPlace) {
        Optional<String> optionalSubmode = PublicationDeliveryHelper.resolveFirstSubmodeToSingleValue(stopPlace);

            if (!optionalSubmode.isPresent()) {
                return stopPlace.getStopPlaceType().value();
            } else {
                return optionalSubmode.get();
            }

    }

    private <T extends EntityInVersionStructure> boolean handle(String localPartOfName,
                                                                boolean lastWasMapped,
                                                                XMLEventReader xmlEventReader,
                                                                Class<T> clazz,
                                                                OutputStream outputStream,
                                                                OutputStreamWriter outputStreamWriter) throws IOException, JAXBException {

        if (clazz.getSimpleName().equals(localPartOfName)) {
            T unmarshalledEntity = unmarshaller.unmarshal(xmlEventReader, clazz).getValue();
            if (validityFilter.isValidNow(unmarshalledEntity.getValidBetween())) {

                if (unmarshalledEntity instanceof Zone_VersionStructure) {
                    Zone_VersionStructure zone = (Zone_VersionStructure) unmarshalledEntity;
                    if (zone.getPolygon() == null && zone.getCentroid() == null) {
                        logger.warn("Got zone ({}) without centroid and polygon. Ignoring it.", zone.getId());
                        return lastWasMapped;
                    }
                }


                if (lastWasMapped) {
                    writeComma(outputStreamWriter);
                }

                if (StopPlace.class.isAssignableFrom(clazz)) {
                    StopPlace stopPlace = (StopPlace) unmarshalledEntity;
                    TreeSet<String> adjacentSites = PublicationDeliveryHelper.resolveAdjacentSites(stopPlace);
                    Set<String> adjacentSitesTypes = new HashSet<>();
                    if (!adjacentSites.isEmpty()) {
                        for (String siteRef : adjacentSites) {
                            String adjacentSiteType = stopPlaceTransportType.get(siteRef);
                            adjacentSitesTypes.add(adjacentSiteType);

                        }
                    }
                    writeStop(stopPlace, adjacentSitesTypes, outputStream, outputStreamWriter);
                } else if (Parking.class.isAssignableFrom(clazz)) {
                    writeParking((Parking) unmarshalledEntity, outputStream);
                } else if (TariffZone.class.isAssignableFrom(clazz)) {
                    writeTariffZone((TariffZone) unmarshalledEntity, outputStream, outputStreamWriter);
                }


                AtomicInteger counter = incrementorsByType.computeIfAbsent(clazz, key -> new AtomicInteger());
                counter.incrementAndGet();
                logEveryN(1000, counter, localPartOfName);

                return true;
            }
        }
        return lastWasMapped;
    }

    private void writeParking(Parking parking, OutputStream outputStream) throws IOException {
        Feature feature = parkingToGeoJsonFeatureMapper.mapParkingToGeoJson(parking);
        jacksonObjectMapper.writeValue(outputStream, feature);
    }

    private void writeStop(StopPlace stopPlace, Set<String> adjacentStopType, OutputStream outputStream, OutputStreamWriter outputStreamWriter) throws IOException {
        Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace , adjacentStopType);
        jacksonObjectMapper.writeValue(outputStream, feature);

        for (Feature quayFeature : quayToGeoJsonFeatureMapper.mapQuaysToGeojsonFeatures(stopPlace.getQuays())) {
            writeComma(outputStreamWriter);
            jacksonObjectMapper.writeValue(outputStream, quayFeature);
        }
    }

    private void writeTariffZone(TariffZone tariffZone, OutputStream outputStream, OutputStreamWriter outputStreamWriter) throws IOException {
        Feature feature = tariffZoneToGeoJsonFeatureMapper.mapTariffZoneToGeoJson(tariffZone);
        jacksonObjectMapper.writeValue(outputStream, feature);
    }

    private void writeComma(OutputStreamWriter outputStreamWriter) throws IOException {
        outputStreamWriter.write(",\n");
        outputStreamWriter.flush();
    }

    private void writeFeatureCollectionStart(OutputStreamWriter outputStreamWriter) throws IOException {
        outputStreamWriter.write("{\n\"features\": [");
        outputStreamWriter.flush();
    }

    private void writeFeatureCollectionEnd(OutputStreamWriter outputStreamWriter) throws IOException {
        outputStreamWriter.write("\n], \"type\": \"FeatureCollection\"\n}");
        outputStreamWriter.flush();
    }

    private void logEveryN(int n, AtomicInteger counter, String type) {
        if (counter.get() % n == 0) {
            logger.info("Transformed {} {}", counter.get(), type);
        }
    }
}
