package com.mango.products.infrastructure.web.mapper;

import com.mango.products.domain.model.Price;
import com.mango.products.domain.model.Product;
import com.mango.products.infrastructure.web.dto.response.PriceHistoryResponse;
import com.mango.products.infrastructure.web.dto.response.ProductResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

class ProductMapperTest {

    @Test
    void mapsAProductToAResponse() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");

        ProductResponse response = ProductMapper.toResponse(product);

        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.name()).isEqualTo("Zapatillas");
        assertThat(response.description()).isEqualTo("Modelo 2025");
    }

    @Test
    void mapsAProductWithItsPriceHistory() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");
        product.addPrice(Price.create(new BigDecimal("99.99"), LocalDate.of(2024, 1, 1), LocalDate.of(2024, 6, 30)));

        PriceHistoryResponse response = ProductMapper.toHistoryResponse(product);

        assertThat(response.name()).isEqualTo("Zapatillas");
        assertThat(response.description()).isEqualTo("Modelo 2025");
        assertThat(response.prices()).hasSize(1);
        assertThat(response.prices().get(0).value()).isEqualByComparingTo("99.99");
    }

    @Test
    void mapsAProductWithNoPricesToAnEmptyList() {
        Product product = Product.create(1L, "Zapatillas", "Modelo 2025");

        PriceHistoryResponse response = ProductMapper.toHistoryResponse(product);

        assertThat(response.prices()).isEmpty();
    }
}
