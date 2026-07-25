package com.mango.products.domain.exception;

import com.mango.products.domain.model.DateRange;

/** Raised when a new price's date range overlaps with an existing one for the same product. */
public class PriceOverlapException extends DomainException {

    public PriceOverlapException(Long productId, DateRange range) {
        super("Price range " + range + " overlaps with an existing price of product " + productId);
    }
}
