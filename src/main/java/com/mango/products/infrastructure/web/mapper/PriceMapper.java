package com.mango.products.infrastructure.web.mapper;

import com.mango.products.domain.model.Price;
import com.mango.products.infrastructure.web.dto.response.PriceItemResponse;
import com.mango.products.infrastructure.web.dto.response.PriceValueResponse;

import java.util.List;

public final class PriceMapper {

    private PriceMapper() {
    }

    public static PriceItemResponse toItemResponse(Price price) {
        return new PriceItemResponse(price.value(), price.range().initDate(), price.range().endDate());
    }

    public static PriceValueResponse toValueResponse(Price price) {
        return new PriceValueResponse(price.value());
    }

    public static List<PriceItemResponse> toItemResponses(List<Price> prices) {
        return prices.stream().map(PriceMapper::toItemResponse).toList();
    }
}
