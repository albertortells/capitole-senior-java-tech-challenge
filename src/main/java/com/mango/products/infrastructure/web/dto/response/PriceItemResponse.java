package com.mango.products.infrastructure.web.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;

public record PriceItemResponse(BigDecimal value, LocalDate initDate, LocalDate endDate) {
}
