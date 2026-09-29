package com.kramp.assignment.aggregator.domain;

/**
 * Represents a customer segment and its preference values.
 *
 * @param segment customer segment identifier
 * @param preferences typed preferences that may influence product presentation and ordering
 */
public record CustomerInfo(
        String segment,
        CustomerPreferences preferences
) {

    /**
     * Creates the non-personalized customer profile used when no profile is available.
     *
     * @return default segment and preferences
     */
    public static CustomerInfo standardProfile() {
        return new CustomerInfo("STANDARD", CustomerPreferences.empty());
    }
}
