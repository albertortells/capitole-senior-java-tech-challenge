package com.mango.products.domain.exception;

public class ProductNotFoundException extends DomainException {

    public ProductNotFoundException(Long productId) {
        super("Product not found: " + productId);
    }
}
