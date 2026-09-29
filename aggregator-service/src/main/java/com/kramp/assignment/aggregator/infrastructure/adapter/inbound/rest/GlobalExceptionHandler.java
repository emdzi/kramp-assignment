package com.kramp.assignment.aggregator.infrastructure.adapter.inbound.rest;

import com.kramp.assignment.aggregator.domain.exception.CatalogServiceUnavailableException;
import com.kramp.assignment.aggregator.domain.exception.ProductNotFoundException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;
import java.time.Instant;

/** Converts application and validation exceptions into RFC 7807 responses. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final URI PRODUCT_NOT_FOUND_TYPE = URI.create("https://api.kramp.com/errors/not-found");
    private static final URI RESOURCE_NOT_FOUND_TYPE = URI.create("https://api.kramp.com/errors/resource-not-found");
    private static final URI SERVICE_UNAVAILABLE_TYPE = URI.create("https://api.kramp.com/errors/service-unavailable");
    private static final URI INTERNAL_ERROR_TYPE = URI.create("https://api.kramp.com/errors/internal-error");
    private static final URI INVALID_REQUEST_TYPE = URI.create("https://api.kramp.com/errors/invalid-request");

    /**
     * Creates a not-found problem response for a missing product.
     *
     * @param ex exception describing the missing product
     * @return RFC 7807 response with HTTP status 404
     */
    @ExceptionHandler(ProductNotFoundException.class)
    public ProblemDetail handleProductNotFoundException(ProductNotFoundException ex) {
        log.warn("Product not found: {}", ex.getMessage());

        return createProblemDetail(HttpStatus.NOT_FOUND, "Product Not Found", ex.getMessage(), PRODUCT_NOT_FOUND_TYPE);
    }

    /**
     * Creates a not-found problem response when no controller or static resource matches the request path.
     *
     * @param ex exception describing the unmatched request path
     * @return RFC 7807 response with HTTP status 404
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ProblemDetail handleNoResourceFoundException(NoResourceFoundException ex) {
        log.warn("No handler or static resource found for request: {}", ex.getResourcePath());

        return createProblemDetail(
                HttpStatus.NOT_FOUND,
                "Resource Not Found",
                "No endpoint or resource was found for the requested path.",
                RESOURCE_NOT_FOUND_TYPE
        );
    }

    /**
     * Creates a service-unavailable problem response for catalog failures.
     *
     * @param ex exception describing the catalog failure
     * @return RFC 7807 response with HTTP status 503
     */
    @ExceptionHandler(CatalogServiceUnavailableException.class)
    public ProblemDetail handleCatalogServiceUnavailableException(CatalogServiceUnavailableException ex) {
        log.error("Catalog Service unavailable: {}", ex.getMessage());

        return createProblemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                ex.getMessage(),
                SERVICE_UNAVAILABLE_TYPE
        );
    }

    /**
     * Reports an open upstream circuit as service unavailability rather than an internal error.
     *
     * @param ex exception raised when a circuit breaker rejects a call
     * @return RFC 7807 response with HTTP status 503
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ProblemDetail handleCircuitBreakerOpen(CallNotPermittedException ex) {
        log.warn("Upstream circuit breaker is open: {}", ex.getMessage());
        return createProblemDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "An upstream service is temporarily unavailable.",
                SERVICE_UNAVAILABLE_TYPE
        );
    }

    /**
     * Creates a bad-request problem response for invalid request parameters.
     *
     * @param ex validation failure raised for controller method parameters
     * @return RFC 7807 response with HTTP status 400
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ProblemDetail handleValidationException(HandlerMethodValidationException ex) {
        log.warn("Invalid request parameters: {}", ex.getMessage());

        return createProblemDetail(
                HttpStatus.BAD_REQUEST,
                "Invalid Request",
                "One or more request parameters are invalid.",
                INVALID_REQUEST_TYPE
        );
    }

    /**
     * Creates a generic internal-error response for unexpected failures.
     *
     * @param ex unexpected exception to log
     * @return RFC 7807 response with HTTP status 500
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        return createProblemDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error",
                "An unexpected internal error occurred.",
                INTERNAL_ERROR_TYPE
        );
    }

    private ProblemDetail createProblemDetail(HttpStatus status, String title, String detail, URI type) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(title);
        problemDetail.setType(type);
        problemDetail.setProperty("timestamp", Instant.now());
        return problemDetail;
    }
}
