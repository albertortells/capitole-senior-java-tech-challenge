package com.mango.products.infrastructure.web.dto.response;

public record ErrorResponse(int status, String error, String message) {
}
