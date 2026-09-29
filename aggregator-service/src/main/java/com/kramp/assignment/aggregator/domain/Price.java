package com.kramp.assignment.aggregator.domain;

import jakarta.annotation.Nullable;

import java.math.BigDecimal;

/**
 * Represents base, discounted, and final product prices.
 *
 * @param status failure status; null when pricing data was returned successfully
 * @param basePrice price before customer discount, null when pricing is unavailable
 * @param customerDiscount discount applied for the customer, null when pricing is unavailable
 * @param finalPrice price after discount, null when pricing is unavailable
 * @param currency ISO 4217 currency code, null when pricing is unavailable
 */
public record Price(
        @Nullable PriceStatus status,
        @Nullable BigDecimal basePrice,
        @Nullable BigDecimal customerDiscount,
        @Nullable BigDecimal finalPrice,
        @Nullable String currency
) {
    public static Price unavailable() {
        return new Price(PriceStatus.UNAVAILABLE, null, null, null, null);
    }
}
