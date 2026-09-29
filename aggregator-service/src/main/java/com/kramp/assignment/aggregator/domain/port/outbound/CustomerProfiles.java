package com.kramp.assignment.aggregator.domain.port.outbound;

import com.kramp.assignment.aggregator.domain.CustomerInfo;
import jakarta.annotation.Nullable;

import java.util.Optional;

/** Defines access to customer profile data from an upstream service. */
public interface CustomerProfiles {

    /**
     * Retrieves the profile for a customer.
     *
     * @param customerId customer identifier for a personalized profile
     * @return the profile when available, otherwise empty
     */
    Optional<CustomerInfo> getCustomerProfile(String customerId);
}
