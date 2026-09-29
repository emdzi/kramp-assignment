package com.kramp.assignment.aggregator.infrastructure.adapter.outbound.client;

import com.kramp.assignment.aggregator.domain.Product;
import com.kramp.assignment.aggregator.domain.exception.CatalogServiceUnavailableException;
import com.kramp.assignment.aggregator.domain.exception.ProductNotFoundException;
import com.kramp.assignment.aggregator.domain.port.outbound.ProductCatalog;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriBuilder;

import java.io.IOException;
import java.net.URI;

/** Retrieves product details from the configured upstream catalog. */
@Slf4j
@Component
public class ProductCatalogRestClient implements ProductCatalog {

    private static final String SERVICE_NAME = "productCatalog";

    private final RestClient restClient;

    /**
     * Creates a client for the configured product catalog.
     *
     * @param restClientBuilder Spring-configured REST client builder
     * @param catalogServiceUrl base URL of the catalog service
     */
    public ProductCatalogRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.upstream.catalog.url}") String catalogServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(catalogServiceUrl).build();
    }

    @Override
    @CircuitBreaker(name = SERVICE_NAME)
    public Product getProduct(String productId, String market) {
        return restClient.get()
                .uri(uriBuilder -> buildProductUri(uriBuilder, productId, market))
                .retrieve()
                .onStatus(HttpStatusCode::is4xxClientError, (request, response) -> handleClientError(productId, response))
                .onStatus(HttpStatusCode::is5xxServerError, (request, response) -> handleServerError(response))
                .body(Product.class);
    }

    private URI buildProductUri(UriBuilder uriBuilder, String productId, String market) {
        return uriBuilder
                .path("/catalog/{productId}")
                .queryParam("market", market)
                .build(productId);
    }

    private void handleClientError(String productId, ClientHttpResponse response) throws IOException {
        if (response.getStatusCode().value() == 404) {
            log.warn("Product not found in Catalog Service. ProductId: {}", productId);
            throw new ProductNotFoundException("Product not found with id: " + productId);
        }

        log.error("Client error from Catalog Service: {}", response.getStatusCode());
        throw new CatalogServiceUnavailableException("Catalog Service returned error: " + response.getStatusCode());
    }

    private void handleServerError(ClientHttpResponse response) throws IOException {
        log.error("Catalog Service is unavailable. Status: {}", response.getStatusCode());
        throw new CatalogServiceUnavailableException("Catalog Service is currently unavailable");
    }
}
