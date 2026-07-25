package com.mango.products.domain.model;

import com.mango.products.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DateRangeTest {

    private static final LocalDate JAN_1 = LocalDate.of(2024, 1, 1);
    private static final LocalDate JUN_30 = LocalDate.of(2024, 6, 30);
    private static final LocalDate JUL_1 = LocalDate.of(2024, 7, 1);
    private static final LocalDate DEC_31 = LocalDate.of(2024, 12, 31);

    @Nested
    class Construction {

        @Test
        void acceptsNullEndDateAsOpenEnded() {
            DateRange range = DateRange.of(JAN_1, null);

            assertThat(range.isOpenEnded()).isTrue();
        }

        @Test
        void rejectsNullInitDate() {
            assertThatThrownBy(() -> DateRange.of(null, JUN_30))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void rejectsEndDateBeforeInitDate() {
            assertThatThrownBy(() -> DateRange.of(JUN_30, JAN_1))
                    .isInstanceOf(InvalidRequestException.class);
        }

        @Test
        void rejectsEndDateEqualToInitDate() {
            assertThatThrownBy(() -> DateRange.of(JAN_1, JAN_1))
                    .isInstanceOf(InvalidRequestException.class);
        }
    }

    @Nested
    class Contains {

        private final DateRange closed = DateRange.of(JAN_1, JUN_30);

        @Test
        void includesInitDateBoundary() {
            assertThat(closed.contains(JAN_1)).isTrue();
        }

        @Test
        void includesEndDateBoundary() {
            assertThat(closed.contains(JUN_30)).isTrue();
        }

        @Test
        void excludesDayBeforeInitDate() {
            assertThat(closed.contains(JAN_1.minusDays(1))).isFalse();
        }

        @Test
        void excludesDayAfterEndDate() {
            assertThat(closed.contains(JUN_30.plusDays(1))).isFalse();
        }

        @Test
        void openEndedRangeContainsAnyDateFromInitDateOnwards() {
            DateRange open = DateRange.of(JAN_1, null);

            assertThat(open.contains(JAN_1)).isTrue();
            assertThat(open.contains(LocalDate.of(2099, 1, 1))).isTrue();
            assertThat(open.contains(JAN_1.minusDays(1))).isFalse();
        }
    }

    @Nested
    class Overlaps {

        @Test
        void identicalRangesOverlap() {
            DateRange range = DateRange.of(JAN_1, JUN_30);

            assertThat(range.overlaps(range)).isTrue();
        }

        @Test
        void partialOverlapAtTheStart() {
            DateRange first = DateRange.of(JAN_1, JUN_30);
            DateRange second = DateRange.of(LocalDate.of(2024, 3, 1), DEC_31);

            assertThat(first.overlaps(second)).isTrue();
            assertThat(second.overlaps(first)).isTrue();
        }

        @Test
        void nestedRangeOverlaps() {
            DateRange outer = DateRange.of(JAN_1, DEC_31);
            DateRange inner = DateRange.of(LocalDate.of(2024, 3, 1), LocalDate.of(2024, 4, 1));

            assertThat(outer.overlaps(inner)).isTrue();
        }

        @Test
        void adjacentRangesWithNoSharedDayDoNotOverlap() {
            DateRange first = DateRange.of(JAN_1, JUN_30);
            DateRange second = DateRange.of(JUL_1, DEC_31);

            assertThat(first.overlaps(second)).isFalse();
            assertThat(second.overlaps(first)).isFalse();
        }

        @Test
        void rangesSharingExactlyOneBoundaryDayDoOverlap() {
            // Both ranges are closed-closed, so the shared day (JUN_30) belongs to both.
            DateRange first = DateRange.of(JAN_1, JUN_30);
            DateRange second = DateRange.of(JUN_30, DEC_31);

            assertThat(first.overlaps(second)).isTrue();
            assertThat(second.overlaps(first)).isTrue();
        }

        @Test
        void openEndedRangeOverlapsAnyLaterClosedRange() {
            DateRange open = DateRange.of(JAN_1, null);
            DateRange laterClosed = DateRange.of(LocalDate.of(2099, 1, 1), LocalDate.of(2099, 6, 30));

            assertThat(open.overlaps(laterClosed)).isTrue();
            assertThat(laterClosed.overlaps(open)).isTrue();
        }

        @Test
        void closedRangeEndingBeforeOpenEndedRangeStartsDoesNotOverlap() {
            DateRange closed = DateRange.of(JAN_1, JUN_30);
            DateRange open = DateRange.of(JUL_1, null);

            assertThat(closed.overlaps(open)).isFalse();
            assertThat(open.overlaps(closed)).isFalse();
        }

        @Test
        void twoOpenEndedRangesAlwaysOverlap() {
            DateRange first = DateRange.of(JAN_1, null);
            DateRange second = DateRange.of(JUL_1, null);

            assertThat(first.overlaps(second)).isTrue();
            assertThat(second.overlaps(first)).isTrue();
        }
    }
}
