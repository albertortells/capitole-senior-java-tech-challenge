package com.mango.products.support;

import com.mango.products.domain.model.Price;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Test Data Builder for {@link Price}: keeps test setup readable when many tests only
 * vary one or two fields (typically the dates) out of several.
 */
public class PriceTestDataBuilder {

    private BigDecimal value = new BigDecimal("10.00");
    private LocalDate initDate = LocalDate.of(2024, 1, 1);
    private LocalDate endDate = LocalDate.of(2024, 6, 30);

    private PriceTestDataBuilder() {
    }

    public static PriceTestDataBuilder aPrice() {
        return new PriceTestDataBuilder();
    }

    public PriceTestDataBuilder withValue(String value) {
        this.value = new BigDecimal(value);
        return this;
    }

    public PriceTestDataBuilder from(LocalDate initDate) {
        this.initDate = initDate;
        return this;
    }

    public PriceTestDataBuilder to(LocalDate endDate) {
        this.endDate = endDate;
        return this;
    }

    public PriceTestDataBuilder openEnded() {
        this.endDate = null;
        return this;
    }

    public Price build() {
        return Price.create(value, initDate, endDate);
    }
}
