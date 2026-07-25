package com.mango.products.domain.exception;

import java.time.LocalDate;

/** Raised when a product exists but has no price in effect for the requested date. */
public class PriceNotFoundForDateException extends DomainException {

    public PriceNotFoundForDateException(Long productId, LocalDate date) {
        super("No price in effect for product " + productId + " on " + date);
    }
}
