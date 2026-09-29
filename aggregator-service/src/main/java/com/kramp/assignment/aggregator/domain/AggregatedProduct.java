package com.kramp.assignment.aggregator.domain;

/**
 * Holds a catalog product and any related data available for the request.
 *
 * @param product catalog product, required for a successful aggregation
 * @param price optional price data
 * @param stock optional stock data
 * @param customerInfo optional personalized or standard customer profile data
 */
public record AggregatedProduct(
        Product product,
        Price price,
        Stock stock,
        CustomerInfo customerInfo
) {}