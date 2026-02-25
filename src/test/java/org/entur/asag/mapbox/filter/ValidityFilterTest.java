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

package org.entur.asag.mapbox.filter;

import org.junit.jupiter.api.Test;
import org.rutebanken.netex.model.ValidBetween;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

public class ValidityFilterTest {

    private final ValidityFilter filter = new ValidityFilter();

    // --- List overload ---

    @Test
    public void emptyListIsValid() {
        assertThat(filter.isValidNow(Collections.emptyList())).isTrue();
    }

    @Test
    public void nullListIsValid() {
        assertThat(filter.isValidNow((java.util.List<ValidBetween>) null)).isTrue();
    }

    @Test
    public void singleValidEntryInListIsValid() {
        assertThat(filter.isValidNow(Collections.singletonList(withFromDate(yesterday())))).isTrue();
    }

    @Test
    public void singleExpiredEntryInListIsInvalid() {
        assertThat(filter.isValidNow(Collections.singletonList(withToDate(yesterday())))).isFalse();
    }

    @Test
    public void multipleEntriesListReturnsResultForFirstEvaluated() {
        // findAny() returns the first element mapped — with a sequential stream this is always
        // the first item in the list. This test documents that behaviour.
        ValidBetween expired = withToDate(yesterday());
        ValidBetween valid = withFromDate(yesterday());

        // First entry is expired → result is false
        assertThat(filter.isValidNow(Arrays.asList(expired, valid))).isFalse();
    }

    // --- Single ValidBetween overload ---

    @Test
    public void nullValidBetweenIsValid() {
        assertThat(filter.isValidNow((ValidBetween) null)).isTrue();
    }

    @Test
    public void noDatesSetIsValid() {
        assertThat(filter.isValidNow(new ValidBetween())).isTrue();
    }

    @Test
    public void fromDateInPastNoToDateIsValid() {
        assertThat(filter.isValidNow(withFromDate(yesterday()))).isTrue();
    }

    @Test
    public void fromDateInFutureIsInvalid() {
        assertThat(filter.isValidNow(withFromDate(tomorrow()))).isFalse();
    }

    @Test
    public void toDateInFutureNoFromDateIsValid() {
        assertThat(filter.isValidNow(withToDate(tomorrow()))).isTrue();
    }

    @Test
    public void toDateInPastIsInvalid() {
        assertThat(filter.isValidNow(withToDate(yesterday()))).isFalse();
    }

    @Test
    public void validRangeSpanningNowIsValid() {
        ValidBetween vb = new ValidBetween();
        vb.setFromDate(yesterday());
        vb.setToDate(tomorrow());
        assertThat(filter.isValidNow(vb)).isTrue();
    }

    @Test
    public void expiredRangeFromAndToInPastIsInvalid() {
        ValidBetween vb = new ValidBetween();
        vb.setFromDate(LocalDateTime.now().minusDays(2));
        vb.setToDate(yesterday());
        assertThat(filter.isValidNow(vb)).isFalse();
    }

    @Test
    public void futureRangeFromAndToInFutureIsInvalid() {
        ValidBetween vb = new ValidBetween();
        vb.setFromDate(tomorrow());
        vb.setToDate(LocalDateTime.now().plusDays(2));
        assertThat(filter.isValidNow(vb)).isFalse();
    }

    // --- helpers ---

    private ValidBetween withFromDate(LocalDateTime date) {
        ValidBetween vb = new ValidBetween();
        vb.setFromDate(date);
        return vb;
    }

    private ValidBetween withToDate(LocalDateTime date) {
        ValidBetween vb = new ValidBetween();
        vb.setToDate(date);
        return vb;
    }

    private LocalDateTime yesterday() {
        return LocalDateTime.now().minusDays(1);
    }

    private LocalDateTime tomorrow() {
        return LocalDateTime.now().plusDays(1);
    }
}