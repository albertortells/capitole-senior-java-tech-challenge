package com.mango.products.infrastructure.web.mapper;

import com.mango.products.domain.model.Product;
import com.mango.products.infrastructure.web.dto.response.PriceHistoryResponse;
import com.mango.products.infrastructure.web.dto.response.ProductResponse;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product) {
        return new ProductResponse(product.id(), product.name(), product.description());
    }

    public static PriceHistoryResponse toHistoryResponse(Product product) {
        return new PriceHistoryResponse(
                product.name(),
                product.description(),
                PriceMapper.toItemResponses(product.history())
        );
    }
}
