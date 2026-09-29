package com.kramp.assignment.aggregator.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;

/**
 * Stores customer preferences that may influence product presentation and ordering.
 *
 * @param showPricesWithVat whether displayed prices include VAT, null when unspecified
 * @param preferredShippingMethod customer's preferred shipping method, null when unspecified
 * @param orderApprovalRequired whether orders require approval, null when unspecified
 * @param communicationChannel preferred customer communication channel, null when unspecified
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CustomerPreferences(
        @Nullable Boolean showPricesWithVat,
        @Nullable String preferredShippingMethod,
        @Nullable Boolean orderApprovalRequired,
        @Nullable String communicationChannel
) {
    public static CustomerPreferences empty() {
        return new CustomerPreferences(null, null, null, null);
    }
}
