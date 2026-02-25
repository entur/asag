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

import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.AirSubmodeEnumeration;
import org.rutebanken.netex.model.PublicationDeliveryStructure;
import org.rutebanken.netex.model.StopPlace;

import java.io.FileInputStream;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

public class PublicationDeliveryHelperTest {

    private static final String SITE_FRAME_XML = "src/test/resources/publication-delivery.xml";
    private static final String COMPOSITE_FRAME_XML = "src/test/resources/composite-frame-delivery.xml";

    // --- resolveSiteFrames / resolveStops ---

    @Test
    public void resolveStopsFromSiteFrameDirectly() throws Exception {
        PublicationDeliveryStructure delivery = unmarshall(SITE_FRAME_XML);
        List<StopPlace> stops = PublicationDeliveryHelper.resolveStops(delivery).collect(Collectors.toList());

        // publication-delivery.xml has 3 stop places (including the expired one)
        assertThat(stops).hasSize(3);
        assertThat(stops).extracting(s -> s.getId())
                .contains("NSR:StopPlace:1", "NSR:StopPlace:10", "NSR:StopPlace:22");
    }

    /**
     * Exercises the CompositeFrame branch of resolveSiteFramesFromCommonFrame.
     * Real-world NeTEx exports (e.g. Entur full Nordic packages) wrap SiteFrames
     * inside a CompositeFrame. Without this path, resolveStops() would return
     * an empty stream and the GeoJSON output would be silently empty.
     */
    @Test
    public void resolveStopsFromCompositeFrameWrappingSiteFrame() throws Exception {
        PublicationDeliveryStructure delivery = unmarshall(COMPOSITE_FRAME_XML);
        List<StopPlace> stops = PublicationDeliveryHelper.resolveStops(delivery).collect(Collectors.toList());

        assertThat(stops).hasSize(2);
        assertThat(stops).extracting(s -> s.getId())
                .containsOnly("NSR:StopPlace:999", "NSR:StopPlace:1000");
    }

    @Test
    public void resolveStopTypesFromCompositeFrame() throws Exception {
        PublicationDeliveryStructure delivery = unmarshall(COMPOSITE_FRAME_XML);
        List<String> types = PublicationDeliveryHelper.resolveStops(delivery)
                .map(s -> s.getStopPlaceType().value())
                .collect(Collectors.toList());

        assertThat(types).containsOnly("busStation", "railStation");
    }

    // --- resolveAdjacentSites ---

    @Test
    public void resolveAdjacentSitesReturnsEmptySetWhenNone() {
        StopPlace stopPlace = new StopPlace().withId("NSR:StopPlace:1");
        assertThat(PublicationDeliveryHelper.resolveAdjacentSites(stopPlace)).isEmpty();
    }

    // --- resolveFirstSubmodeToSingleValue ---

    @Test
    public void resolveFirstSubmodeReturnsSubmodeWhenSet() {
        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withAirSubmode(AirSubmodeEnumeration.AIRSHIP_SERVICE);

        Optional<String> submode = PublicationDeliveryHelper.resolveFirstSubmodeToSingleValue(stopPlace);

        assertThat(submode).isPresent();
        assertThat(submode.get()).isEqualTo(AirSubmodeEnumeration.AIRSHIP_SERVICE.value());
    }

    @Test
    public void resolveFirstSubmodeReturnsEmptyWhenNoSubmodeSet() {
        StopPlace stopPlace = new StopPlace().withId("NSR:StopPlace:1");

        Optional<String> submode = PublicationDeliveryHelper.resolveFirstSubmodeToSingleValue(stopPlace);

        assertThat(submode).isEmpty();
    }

    @Test
    public void resolveFirstSubmodeFiltersOutUnknownValue() {
        // AirSubmode "unknown" is filtered by the "unknown".equals(value) guard
        StopPlace stopPlace = new StopPlace()
                .withId("NSR:StopPlace:1")
                .withAirSubmode(AirSubmodeEnumeration.UNKNOWN);

        Optional<String> submode = PublicationDeliveryHelper.resolveFirstSubmodeToSingleValue(stopPlace);

        assertThat(submode).isEmpty();
    }

    // --- helper ---

    private PublicationDeliveryStructure unmarshall(String path) throws Exception {
        return PublicationDeliveryHelper.unmarshall(new FileInputStream(path));
    }
}