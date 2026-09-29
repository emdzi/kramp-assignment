package com.kramp.assignment.aggregator.infrastructure.adapter.outbound.client;

import com.kramp.assignment.aggregator.domain.Stock;
import com.kramp.assignment.aggregator.domain.port.outbound.StockAvailability;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/** Retrieves stock availability from the configured upstream service. */
@Slf4j
@Component
public class StockAvailabilityRestClient implements StockAvailability {

    private static final String SERVICE_NAME = "stockAvailability";

    private final RestClient restClient;

    /**
     * Creates a client for the configured stock availability service.
     *
     * @param restClientBuilder Spring-configured REST client builder
     * @param stockAvailabilityUrl base URL of the stock service
     */
    public StockAvailabilityRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.upstream.stock-availability.url}") String stockAvailabilityUrl) {
        this.restClient = restClientBuilder.baseUrl(stockAvailabilityUrl).build();
    }

    /** Returns unknown stock when the circuit breaker fallback handles an upstream failure. */
    @Override
    @CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "fallbackStock")
    public Stock getStock(String productId, String market) {
        Stock stock = restClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/availability/{productId}")
                        .queryParam("market", market)
                        .build(productId))
                .retrieve()
                .body(Stock.class);
        return stock;
    }

    @SuppressWarnings("unused")
    private Stock fallbackStock(String productId, String market, Throwable t) {
        log.warn("Fallback triggered for {} (product: {}, market: {}). Reason: {}",
                SERVICE_NAME, productId, market, t.getMessage());
        return Stock.unknown();
    }
}
