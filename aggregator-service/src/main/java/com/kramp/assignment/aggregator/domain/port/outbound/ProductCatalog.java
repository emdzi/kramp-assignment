package com.kramp.assignment.aggregator.domain.port.outbound;

import com.kramp.assignment.aggregator.domain.Product;
import com.kramp.assignment.aggregator.domain.exception.CatalogServiceUnavailableException;
import com.kramp.assignment.aggregator.domain.exception.ProductNotFoundException;

/** Defines access to product details in the upstream catalog. */
public interface ProductCatalog {

    /**
     * Retrieves a product for a market.
     *
     * @param productId product identifier
     * @param market market code used for the lookup
     * @return the requested product
     * @throws ProductNotFoundException if the product does not exist
     * @throws CatalogServiceUnavailableException if the catalog cannot provide a response
     */
    Product getProduct(String productId, String market);
}
