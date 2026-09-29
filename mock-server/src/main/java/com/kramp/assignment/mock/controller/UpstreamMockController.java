package com.kramp.assignment.mock.controller;

import com.kramp.assignment.mock.SlaSimulator;
import com.kramp.assignment.mock.controller.UpstreamMockData.AvailabilityResponse;
import com.kramp.assignment.mock.controller.UpstreamMockData.CatalogResponse;
import com.kramp.assignment.mock.controller.UpstreamMockData.CustomerResponse;
import com.kramp.assignment.mock.controller.UpstreamMockData.PricingResponse;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Provides simulated catalog, pricing, stock, and customer upstream endpoints. */
@RestController
@RequestMapping("/api/v1/upstream")
@RequiredArgsConstructor
public class UpstreamMockController {

    private final SlaSimulator slaSimulator;
    private final UpstreamMockData upstreamMockData;

    /**
     * Returns simulated catalog details for a known product and market.
     *
     * @param productId product identifier from the URL path
     * @param market market locale from the query string
     * @return mock catalog response
     */
    @GetMapping("/catalog/{productId}")
    public CatalogResponse getCatalog(@PathVariable String productId, @RequestParam String market) {
        slaSimulator.simulate(50, 0.999);
        return upstreamMockData.catalog(productId, market);
    }

    /**
     * Returns simulated pricing for a known product, market, and optional customer.
     *
     * @param productId product identifier from the URL path
     * @param market market code from the query string
     * @param customerId optional customer identifier for customer-specific discounts
     * @return mock pricing response
     */
    @GetMapping("/pricing/{productId}")
    public PricingResponse getPricing(
            @PathVariable String productId,
            @RequestParam String market,
            @RequestParam(required = false) @Nullable String customerId) {
        slaSimulator.simulate(80, 0.995);
        return upstreamMockData.pricing(productId, market, customerId);
    }

    /**
     * Returns simulated inventory for a known product and market.
     *
     * @param productId product identifier from the URL path
     * @param market market code from the query string
     * @return mock stock response
     */
    @GetMapping("/availability/{productId}")
    public AvailabilityResponse getAvailability(@PathVariable String productId, @RequestParam String market) {
        slaSimulator.simulate(100, 0.98);
        return upstreamMockData.availability(productId, market);
    }

    /**
     * Returns a standard profile without preferences or a known personalized customer profile.
     *
     * @param customerId optional customer identifier from the URL path
     * @return mock customer profile response
     */
    @GetMapping({"/customer", "/customer/{customerId}"})
    public CustomerResponse getCustomer(@PathVariable(required = false) @Nullable String customerId) {
        slaSimulator.simulate(60, 0.99);
        return upstreamMockData.customer(customerId);
    }
}
