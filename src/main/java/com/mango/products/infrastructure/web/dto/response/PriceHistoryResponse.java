package com.mango.products.infrastructure.web.dto.response;

import java.util.List;

public record PriceHistoryResponse(String name, String description, List<PriceItemResponse> prices) {
}
