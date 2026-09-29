package com.kramp.assignment.aggregator.infrastructure.adapter.outbound.client;

import com.kramp.assignment.aggregator.domain.CustomerInfo;
import com.kramp.assignment.aggregator.domain.port.outbound.CustomerProfiles;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;

/** Retrieves customer profiles from the configured upstream service. */
@Slf4j
@Component
public class CustomerProfilesRestClient implements CustomerProfiles {

    private static final String SERVICE_NAME = "customerProfiles";

    private final RestClient restClient;

    /**
     * Creates a client for the configured customer profile service.
     *
     * @param restClientBuilder Spring-configured REST client builder
     * @param customerServiceUrl base URL of the customer service
     */
    public CustomerProfilesRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${app.upstream.customer.url}") String customerServiceUrl) {
        this.restClient = restClientBuilder.baseUrl(customerServiceUrl).build();
    }

    /** Returns an empty result when the circuit breaker handles an upstream failure. */
    @Override
    @CircuitBreaker(name = SERVICE_NAME, fallbackMethod = "fallbackCustomer")
    public Optional<CustomerInfo> getCustomerProfile(String customerId) {
        CustomerInfo customer = restClient.get()
                .uri(uriBuilder -> uriBuilder.path("/customer/{customerId}").build(customerId))
                .retrieve()
                .body(CustomerInfo.class);

        return Optional.ofNullable(customer);
    }

    @SuppressWarnings("unused")
    private Optional<CustomerInfo> fallbackCustomer(String customerId, Throwable t) {
        log.warn("Fallback triggered for {} (customer: {}). Reason: {}",
                SERVICE_NAME, customerId, t.getMessage());
        return Optional.empty();
    }
}
