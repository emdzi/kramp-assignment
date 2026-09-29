package com.kramp.assignment.aggregator.infrastructure.adapter.inbound.rest;

import com.kramp.assignment.aggregator.application.port.inbound.GetProductDetailsUseCase;
import com.kramp.assignment.aggregator.domain.AggregatedProduct;
import com.kramp.assignment.aggregator.domain.AggregationContext;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Handles HTTP requests for aggregated product details. */
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
public class ProductAggregatorController {

    private final GetProductDetailsUseCase getProductDetailsUseCase;

    /**
     * Returns catalog, pricing, stock, and optional customer data for a product.
     *
     * @param productId product identifier from the URL path; the mock supports {@code P-1} and {@code P-2}
     * @param market supported locale; defaults to {@code nl-NL}
     * @param customerId optional customer identifier for personalized data; the mock supports
     *                   {@code TEST-CUSTOMER-100042} and {@code TEST-CUSTOMER-200017}
     * @return HTTP 200 with aggregated product details
     * @apiNote Example requests:
     * <pre>
     * GET /api/v1/products/P-1?market=nl-NL&amp;customerId=TEST-CUSTOMER-100042
     * GET /api/v1/products/P-1?market=nl-NL&amp;customerId=TEST-CUSTOMER-200017
     * GET /api/v1/products/P-2?market=nl-NL&amp;customerId=TEST-CUSTOMER-100042
     * GET /api/v1/products/P-2?market=nl-NL&amp;customerId=TEST-CUSTOMER-200017
     * GET /api/v1/products/P-1
     * </pre>
     */
    @GetMapping("/{productId}")
    public ResponseEntity<ProductAggregationResponse> getProductDetails(
            @PathVariable @NotBlank String productId,
            @RequestParam(defaultValue = "nl-NL")
            @Pattern(regexp = "nl-NL|de-DE|pl-PL", message = "Market must be one of: nl-NL, de-DE, pl-PL")
            String market,
            @RequestParam(required = false) @Nullable String customerId) {

        AggregationContext context = new AggregationContext(productId, market, customerId);
        AggregatedProduct aggregatedProduct = getProductDetailsUseCase.execute(context);

        return ResponseEntity.ok(ProductAggregationResponse.from(aggregatedProduct));
    }
}
