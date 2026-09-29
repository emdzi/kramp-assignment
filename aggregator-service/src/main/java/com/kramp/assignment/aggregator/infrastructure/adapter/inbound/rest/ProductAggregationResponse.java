package com.kramp.assignment.aggregator.infrastructure.adapter.inbound.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.kramp.assignment.aggregator.domain.AggregatedProduct;
import com.kramp.assignment.aggregator.domain.CustomerInfo;
import com.kramp.assignment.aggregator.domain.CustomerPreferences;
import com.kramp.assignment.aggregator.domain.Price;
import com.kramp.assignment.aggregator.domain.PriceStatus;
import com.kramp.assignment.aggregator.domain.Product;
import com.kramp.assignment.aggregator.domain.Stock;
import com.kramp.assignment.aggregator.domain.StockStatus;
import jakarta.annotation.Nullable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Exposes aggregated product data through the public REST API.
 *
 * @param product catalog details
 * @param price price details and availability status
 * @param stock stock details and availability status
 * @param customerInfo optional personalized or standard customer profile
 */
public record ProductAggregationResponse(
        ProductResponse product,
        PriceResponse price,
        StockResponse stock,
        @Nullable CustomerInfoResponse customerInfo
) {
    /**
     * Maps the domain aggregation result to its REST response representation.
     *
     * @param aggregatedProduct domain result to expose
     * @return response DTO containing the available data
     */
    public static ProductAggregationResponse from(AggregatedProduct aggregatedProduct) {
        return new ProductAggregationResponse(
                ProductResponse.from(aggregatedProduct.product()),
                PriceResponse.from(aggregatedProduct.price()),
                StockResponse.from(aggregatedProduct.stock()),
                CustomerInfoResponse.from(aggregatedProduct.customerInfo())
        );
    }

    /**
     * Exposes catalog product fields in the API response.
     *
     * @param id product identifier
     * @param name product name
     * @param description product description
     * @param specs product specifications
     * @param images product image locations
     */
    public record ProductResponse(
            String id,
            String name,
            String description,
            Map<String, String> specs,
            List<String> images
    ) {
        private static ProductResponse from(Product product) {
            return new ProductResponse(product.id(), product.name(), product.description(), product.specs(), product.images());
        }
    }

    /**
     * Exposes product pricing fields in the API response.
     *
     * @param status present only when the pricing source failed
     * @param basePrice price before customer discount, null when unavailable
     * @param customerDiscount customer discount, null when unavailable
     * @param finalPrice price after discount, null when unavailable
     * @param currency ISO 4217 currency code, null when unavailable
     */
    public record PriceResponse(
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable PriceStatus status,
            @Nullable BigDecimal basePrice,
            @Nullable BigDecimal customerDiscount,
            @Nullable BigDecimal finalPrice,
            @Nullable String currency
    ) {
        private static PriceResponse from(Price price) {
            return new PriceResponse(
                    price.status(),
                    price.basePrice(),
                    price.customerDiscount(),
                    price.finalPrice(),
                    price.currency()
            );
        }
    }

    /**
     * Exposes stock and delivery fields in the API response.
     *
     * @param status present only when the stock source failed
     * @param stockLevel known quantity, including zero; null when unknown
     * @param warehouseLocation warehouse holding the stock
     * @param expectedDelivery estimated delivery time
     */
    public record StockResponse(
            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable StockStatus status,
            @Nullable Integer stockLevel,
            @Nullable String warehouseLocation,
            @Nullable String expectedDelivery
    ) {
        private static StockResponse from(Stock stock) {
            return new StockResponse(
                    stock.status(),
                    stock.stockLevel(),
                    stock.warehouseLocation(),
                    stock.expectedDelivery()
            );
        }
    }

    /**
     * Exposes customer segment and preferences in the API response.
     *
     * @param segment customer segment identifier
     * @param preferences customer preferences
     */
    public record CustomerInfoResponse(
            String segment,
            CustomerPreferences preferences
    ) {
        @Nullable
        private static CustomerInfoResponse from(@Nullable CustomerInfo customerInfo) {
            return customerInfo == null ? null : new CustomerInfoResponse(
                    customerInfo.segment(),
                    customerInfo.preferences()
            );
        }
    }
}
