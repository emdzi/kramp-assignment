package com.kramp.assignment.aggregator.domain;

import jakarta.annotation.Nullable;

/**
 * Carries the identifiers required to aggregate product data.
 *
 * @param productId product identifier
 * @param market market code used for catalog, price, and stock lookups
 * @param customerId optional customer identifier for personalized data
 */
public record AggregationContext(
        String productId,
        String market,
        @Nullable String customerId
) {
}
