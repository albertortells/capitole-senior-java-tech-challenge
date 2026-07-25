package com.mango.products.domain.exception;

/** Raised when a request violates a basic validation rule (blank fields, bad ranges, non-positive values...). */
public class InvalidRequestException extends DomainException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
