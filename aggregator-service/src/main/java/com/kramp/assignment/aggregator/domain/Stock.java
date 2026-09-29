package com.kramp.assignment.aggregator.domain;

import jakarta.annotation.Nullable;

/**
 * Represents product inventory and delivery information.
 *
 * @param status failure status; null when the stock source returned a quantity
 * @param stockLevel known available quantity, including zero; null when unknown
 * @param warehouseLocation warehouse holding the stock
 * @param expectedDelivery estimated delivery time
 */
public record Stock(
        @Nullable StockStatus status,
        @Nullable Integer stockLevel,
        @Nullable String warehouseLocation,
        @Nullable String expectedDelivery
) {
    public static Stock unknown() {
        return new Stock(StockStatus.UNKNOWN, null, null, null);
    }
}
