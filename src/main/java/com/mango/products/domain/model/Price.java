package com.mango.products.domain.model;

import com.mango.products.domain.exception.InvalidRequestException;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A monetary amount in effect during a given {@link DateRange}. Money is modeled as
 * {@link BigDecimal}, never {@code double}, to avoid binary floating-point rounding errors. */
public record Price(BigDecimal value, DateRange range) {

    public Price {
        if (value == null || value.signum() <= 0) {
            throw new InvalidRequestException("value must be a positive number");
        }
        if (range == null) {
            throw new InvalidRequestException("range is required");
        }
    }

    public static Price create(BigDecimal value, LocalDate initDate, LocalDate endDate) {
        return new Price(value, DateRange.of(initDate, endDate));
    }
}
