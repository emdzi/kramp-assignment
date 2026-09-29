package com.kramp.assignment.aggregator.domain.port.outbound;

import com.kramp.assignment.aggregator.domain.Stock;

/** Defines access to product stock availability from an upstream service. */
public interface StockAvailability {

    /**
     * Retrieves stock information for a product and market.
     *
     * @param productId product identifier
     * @param market market code used for the lookup
     * @return stock data when available, or a result with {@code UNKNOWN} status and null stock fields
     */
    Stock getStock(String productId, String market);
}
