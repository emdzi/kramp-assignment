package com.kramp.assignment.aggregator.application.service;

import com.kramp.assignment.aggregator.application.port.inbound.GetProductDetailsUseCase;
import com.kramp.assignment.aggregator.domain.AggregatedProduct;
import com.kramp.assignment.aggregator.domain.AggregationContext;
import com.kramp.assignment.aggregator.domain.CustomerInfo;
import com.kramp.assignment.aggregator.domain.Price;
import com.kramp.assignment.aggregator.domain.Product;
import com.kramp.assignment.aggregator.domain.Stock;
import com.kramp.assignment.aggregator.domain.port.outbound.CustomerProfiles;
import com.kramp.assignment.aggregator.domain.port.outbound.ProductCatalog;
import com.kramp.assignment.aggregator.domain.port.outbound.ProductPricing;
import com.kramp.assignment.aggregator.domain.port.outbound.StockAvailability;
import jakarta.annotation.Nullable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.StructuredTaskScope;

/** Fetches product-related data concurrently and combines the results. */
@Service
@RequiredArgsConstructor
@SuppressWarnings("preview")
public class GetProductDetailsUseCaseImpl implements GetProductDetailsUseCase {

    private final ProductCatalog productCatalog;
    private final ProductPricing productPricing;
    private final StockAvailability stockAvailability;
    private final CustomerProfiles customerProfiles;

    @Override
    public AggregatedProduct execute(AggregationContext context) {
        try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
            AggregationTasks tasks = forkTasks(scope, context);
            awaitTasks(scope);
            return aggregate(tasks);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Product aggregation was interrupted", e);
        }
    }

    private AggregationTasks forkTasks(
            StructuredTaskScope.ShutdownOnFailure scope,
            AggregationContext context) {
        return new AggregationTasks(
                scope.fork(() -> productCatalog.getProduct(context.productId(), context.market())),
                scope.fork(() -> productPricing.getPrice(context.productId(), context.market(), context.customerId())),
                scope.fork(() -> stockAvailability.getStock(context.productId(), context.market())),
                scope.fork(() -> fetchCustomerProfile(context.customerId()))
        );
    }

    private CustomerInfo fetchCustomerProfile(@Nullable String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return CustomerInfo.standardProfile();
        }
        return customerProfiles.getCustomerProfile(customerId).orElseGet(CustomerInfo::standardProfile);
    }

    private void awaitTasks(StructuredTaskScope.ShutdownOnFailure scope) throws InterruptedException {
        scope.join();
        scope.throwIfFailed(exception -> exception instanceof RuntimeException runtimeException
                ? runtimeException
                : new RuntimeException("Error during product information aggregation", exception));
    }

    private AggregatedProduct aggregate(AggregationTasks tasks) {
        return new AggregatedProduct(
                tasks.catalog().get(),
                tasks.price().get(),
                tasks.stock().get(),
                tasks.customer().get()
        );
    }

    /** Holds the forked results until all upstream calls complete. */
    private record AggregationTasks(
            StructuredTaskScope.Subtask<Product> catalog,
            StructuredTaskScope.Subtask<Price> price,
            StructuredTaskScope.Subtask<Stock> stock,
            StructuredTaskScope.Subtask<CustomerInfo> customer
    ) {
    }
}
