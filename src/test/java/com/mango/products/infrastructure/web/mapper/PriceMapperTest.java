package com.mango.products.infrastructure.web.mapper;

import com.mango.products.domain.model.Price;
import com.mango.products.infrastructure.web.dto.response.PriceItemResponse;
import com.mango.products.infrastructure.web.dto.response.PriceValueResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriceMapperTest {

    private static final LocalDate INIT_DATE = LocalDate.of(2024, 1, 1);
    private static final LocalDate END_DATE = LocalDate.of(2024, 6, 30);

    @Test
    void mapsAClosedPriceToAnItemResponse() {
        Price price = Price.create(new BigDecimal("99.99"), INIT_DATE, END_DATE);

        PriceItemResponse response = PriceMapper.toItemResponse(price);

        assertThat(response.value()).isEqualByComparingTo("99.99");
        assertThat(response.initDate()).isEqualTo(INIT_DATE);
        assertThat(response.endDate()).isEqualTo(END_DATE);
    }

    @Test
    void mapsAnOpenEndedPriceWithANullEndDate() {
        Price price = Price.create(new BigDecimal("99.99"), INIT_DATE, null);

        PriceItemResponse response = PriceMapper.toItemResponse(price);

        assertThat(response.endDate()).isNull();
    }

    @Test
    void mapsToAValueOnlyResponse() {
        Price price = Price.create(new BigDecimal("99.99"), INIT_DATE, END_DATE);

        PriceValueResponse response = PriceMapper.toValueResponse(price);

        assertThat(response.value()).isEqualByComparingTo("99.99");
    }

    @Test
    void mapsAListPreservingOrder() {
        Price first = Price.create(new BigDecimal("1"), INIT_DATE, END_DATE);
        Price second = Price.create(new BigDecimal("2"), END_DATE.plusDays(1), null);

        List<PriceItemResponse> responses = PriceMapper.toItemResponses(List.of(first, second));

        assertThat(responses).hasSize(2);
        assertThat(responses.get(0).value()).isEqualByComparingTo("1");
        assertThat(responses.get(1).value()).isEqualByComparingTo("2");
    }
}
