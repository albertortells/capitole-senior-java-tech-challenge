package com.mango.products.infrastructure.web.dto.request;

import java.math.BigDecimal;
import java.time.LocalDate;

public record AddPriceRequest(BigDecimal value, LocalDate initDate, LocalDate endDate) {
}
