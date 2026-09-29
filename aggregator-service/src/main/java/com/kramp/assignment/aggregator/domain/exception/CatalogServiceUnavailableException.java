package com.kramp.assignment.aggregator.domain.exception;

/** Signals that the catalog could not provide a response for the request. */
public class CatalogServiceUnavailableException extends RuntimeException {

    /**
     * Creates an exception with a diagnostic message.
     *
     * @param message reason the catalog is unavailable
     */
    public CatalogServiceUnavailableException(String message) {
        super(message);
    }
}
