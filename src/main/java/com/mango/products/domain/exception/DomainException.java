package com.mango.products.domain.exception;

/**
 * Base type for every business-rule violation raised by the domain. Kept abstract so
 * that callers always deal with a concrete, semantically meaningful subtype.
 */
public abstract class DomainException extends RuntimeException {

    protected DomainException(String message) {
        super(message);
    }
}
