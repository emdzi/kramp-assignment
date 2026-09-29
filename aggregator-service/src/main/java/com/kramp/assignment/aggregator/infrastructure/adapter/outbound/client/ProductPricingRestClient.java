package com.kramp.assignment.aggregator.infrastructure.adapter.outbound.client;

import com.kramp.assignment.aggregator.domain.Price;
import com.kramp.assignment.aggregator.domain.port.outbound.ProductPricing;
import jakarta.annotation.Nullable;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;

/** Retrieves product pricing from the configured upstream pricing service. */
@Slf4j
@Component
public class ProductPricingRestClient implements ProductPricing {

    private static final String SERVICE_NAME = "productPricing";

    private final RestClient restClient;

    /**
     * Creates a client for the configured pricing service.
     *
     * @param restClientBuilder Spring-configured REST client builder
     * @param pricingServiceUrl base URL of the pricing service
     */
    public ProductPricingRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.upstream.pricing.url}") String pricingServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(pricingServiceUrl).build();
    }

    /** Returns an unavailable price when the circuit breaker fallback handles an upstream failure. */
    @Override
    @CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "fallbackPrice")
    public Price getPrice(String productId, String market, @Nullable String customerId) {
        Price price = restClient.get()
                .uri(uriBuilder -> buildPriceUri(uriBuilder, productId, market, customerId))
                .retrieve()
                .body(Price.class);
        return price;
    }

    private URI buildPriceUri(
            UriBuilder uriBuilder, String productId, String market, @Nullable String customerId) {
        var builder = uriBuilder
                .path("/pricing/{productId}")
                .queryParam("market", market);

        if (customerId != null && !customerId.isBlank()) {
            builder.queryParam("customerId", customerId);
        }

        return builder.build(productId);
    }

    @SuppressWarnings("unused")
    private Price fallbackPrice(
            String productId, String market, @Nullable String customerId, Throwable t) {
        log.warn("Fallback triggered for {} (product: {}, customer: {}). Reason: {}",
                SERVICE_NAME, productId, customerId, t.getMessage());
        return Price.unavailable();
    }

}
