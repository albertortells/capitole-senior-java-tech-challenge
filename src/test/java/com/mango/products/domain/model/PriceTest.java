package com.mango.products.domain.model;

import com.mango.products.domain.exception.InvalidRequestException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PriceTest {

    private static final LocalDate INIT_DATE = LocalDate.of(2024, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2024, 6, 30);

    @Test
    void createsAValidPrice() {
        Price price = Price.create(new BigDecimal("99.99"), INIT_DATE, END_DATE);

        assertThat(price.value()).isEqualByComparingTo("99.99");
        assertThat(price.range()).isEqualTo(DateRange.of(INIT_DATE, END_DATE));
    }

    @Test
    void allowsNullEndDateForAnOpenEndedPrice() {
        Price price = Price.create(new BigDecimal("99.99"), INIT_DATE, null);

        assertThat(price.range().isOpenEnded()).isTrue();
    }

    @Test
    void rejectsZeroValue() {
        assertThatThrownBy(() -> Price.create(BigDecimal.ZERO, INIT_DATE, END_DATE))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsNegativeValue() {
        assertThatThrownBy(() -> Price.create(new BigDecimal("-1"), INIT_DATE, END_DATE))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsNullInitDate() {
        assertThatThrownBy(() -> Price.create(new BigDecimal("99.99"), null, END_DATE))
                .isInstanceOf(InvalidRequestException.class);
    }

    @Test
    void rejectsInitDateNotBeforeEndDate() {
        assertThatThrownBy(() -> Price.create(new BigDecimal("99.99"), END_DATE, INIT_DATE))
                .isInstanceOf(InvalidRequestException.class);
    }
}
