package com.kramp.assignment.aggregator.domain.exception;

/** Signals that the requested product does not exist in the catalog. */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message reason the product was not found
     */
    public ProductNotFoundException(String message) {
        super(message);
    }
}
