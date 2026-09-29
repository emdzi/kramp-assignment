package com.kramp.assignment.aggregator.domain.port.outbound;

import com.kramp.assignment.aggregator.domain.Price;
import jakarta.annotation.Nullable;

/** Defines access to product prices, including customer-specific pricing. */
public interface ProductPricing {

    /**
     * Retrieves pricing for a product and market.
     *
     * @param productId product identifier
     * @param market market code used for pricing
     * @param customerId optional customer identifier for personalized pricing
     * @return price data when available, or a result with {@code UNAVAILABLE} status and null price fields
     */
    Price getPrice(String productId, String market, @Nullable String customerId);
}
