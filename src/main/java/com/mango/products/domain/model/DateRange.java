package com.mango.products.domain.model;

import com.mango.products.domain.exception.InvalidRequestException;

import java.time.LocalDate;

/**
 * Closed date interval: both {@code initDate} and {@code endDate} are inclusive.
 * {@code endDate == null} means the range is open-ended (still in effect indefinitely).
 */
public record DateRange(LocalDate initDate, LocalDate endDate) {

    public DateRange {
        if (initDate == null) {
            throw new InvalidRequestException("initDate is required");
        }
        if (endDate != null && !initDate.isBefore(endDate)) {
            throw new InvalidRequestException("initDate must be strictly before endDate");
        }
    }

    public static DateRange of(LocalDate initDate, LocalDate endDate) {
        return new DateRange(initDate, endDate);
    }

    public boolean isOpenEnded() {
        return endDate == null;
    }

    public boolean contains(LocalDate date) {
        if (date.isBefore(initDate)) {
            return false;
        }
        return isOpenEnded() || !date.isAfter(endDate);
    }

    /**
     * Two closed ranges overlap unless one of them fully ends (inclusive) before the
     * other starts. A shared boundary day (e.g. one range ending and another starting
     * on the same date) counts as an overlap, since that single day would otherwise
     * belong to both ranges at once.
     */
    public boolean overlaps(DateRange other) {
        boolean thisEndsBeforeOtherStarts = !isOpenEnded() && endDate.isBefore(other.initDate);
        boolean otherEndsBeforeThisStarts = !other.isOpenEnded() && other.endDate.isBefore(initDate);
        return !(thisEndsBeforeOtherStarts || otherEndsBeforeThisStarts);
    }
}
