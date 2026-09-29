package com.kramp.assignment.aggregator.domain;

import java.util.List;
import java.util.Map;

/**
 * Represents product details supplied by the catalog.
 *
 * @param id product identifier
 * @param name product name
 * @param description product description
 * @param specs product specifications keyed by name
 * @param images product image locations
 */
public record Product(
        String id,
        String name,
        String description,
        Map<String, String> specs,
        List<String> images
) {
}
