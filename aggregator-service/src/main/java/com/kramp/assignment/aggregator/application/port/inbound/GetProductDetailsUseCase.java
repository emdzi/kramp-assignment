package com.kramp.assignment.aggregator.application.port.inbound;

import com.kramp.assignment.aggregator.domain.AggregatedProduct;
import com.kramp.assignment.aggregator.domain.AggregationContext;

/** Defines the application operation for retrieving aggregated product details. */
public interface GetProductDetailsUseCase {

    /**
     * Retrieves product details and related data for the supplied context.
     *
     * @param context product, market, and optional customer identifiers
     * @return the product and any available related data
     */
    AggregatedProduct execute(AggregationContext context);
}
