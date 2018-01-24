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
import org.entur.asag.mapbox.filter.ValidityFilter;
import org.entur.asag.mapbox.mapper.StopPlaceToGeoJsonFeatureMapper;
import org.entur.asag.netex.PublicationDeliveryHelper;
import org.geojson.Feature;
import org.rutebanken.netex.model.StopPlace;
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
import java.io.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DeliveryPublicationStreamToGeoJson {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryPublicationStreamToGeoJson.class);

    private final StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper;

    private final ValidityFilter validityFilter;

    private ObjectMapper jacksonObjectMapper = new ObjectMapper();

    private final Unmarshaller unmarshaller;

    @Autowired
    public DeliveryPublicationStreamToGeoJson(StopPlaceToGeoJsonFeatureMapper stopPlaceToGeoJsonFeatureMapper, ValidityFilter validityFilter) throws JAXBException {
        this.stopPlaceToGeoJsonFeatureMapper = stopPlaceToGeoJsonFeatureMapper;
        this.validityFilter = validityFilter;
        unmarshaller = PublicationDeliveryHelper.createUnmarshaller();
    }

    public OutputStream transform(InputStream publicationDeliveryStream) {
        return traverse(publicationDeliveryStream);
    }

    private OutputStream traverse(InputStream publicationDeliveryStream) {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);

        AtomicInteger stopsCounter = new AtomicInteger();

        boolean lastWasMapped = false;
        try {
            writeFeatureCollectionStart(outputStreamWriter);

            XMLEventReader xmlEventReader = XMLInputFactory.newInstance().createXMLEventReader(publicationDeliveryStream);

            while (xmlEventReader.hasNext()) {
                XMLEvent xmlEvent = xmlEventReader.peek();

                if (xmlEvent.isStartElement()) {
                    StartElement startElement = xmlEvent.asStartElement();
                    String localPartOfName = startElement.getName().getLocalPart();
                    lastWasMapped = handleXmlElement(localPartOfName, xmlEventReader, stopsCounter, outputStream, outputStreamWriter, lastWasMapped);
                }
                xmlEventReader.next();
            }

            writeFeatureCollectionEnd(outputStreamWriter);

        } catch (Exception e) {
            throw new RuntimeException("Parsing of DeliveryPublications failed: " + e.getMessage(), e);
        }
        return outputStream;
    }

    private boolean handleXmlElement(String localPartOfName, XMLEventReader xmlEventReader, AtomicInteger stops, OutputStream outputStream, OutputStreamWriter outputStreamWriter, boolean lastWasMapped) throws JAXBException, IOException {
        if ("StopPlace".equals(localPartOfName)) {
            StopPlace stopPlace = unmarshaller.unmarshal(xmlEventReader, StopPlace.class).getValue();
            if (validityFilter.isValidNow(stopPlace.getValidBetween())) {

                if (lastWasMapped) {
                    outputStreamWriter.write(",\n");
                    outputStreamWriter.flush();
                }
                Feature feature = stopPlaceToGeoJsonFeatureMapper.mapStopPlaceToGeoJson(stopPlace);
                jacksonObjectMapper.writeValue(outputStream, feature);
                stops.incrementAndGet();
                logEveryN(1000, stops, StopPlace.class.getSimpleName());
                return true;
            }
        }
        return false;
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
