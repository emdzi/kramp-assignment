package com.kramp.assignment.mock.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.annotation.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;

/** Holds representative upstream data and builds the corresponding mock responses. */
@Component
public class UpstreamMockData {

    private static final String PREMIUM_CUSTOMER_ID = "TEST-CUSTOMER-100042";
    private static final String STANDARD_CUSTOMER_ID = "TEST-CUSTOMER-200017";
    private static final BigDecimal EUR_TO_PLN_RATE = new BigDecimal("4.30");
    private static final Map<String, ProductMetadata> PRODUCTS = Map.of(
            "P-1", new ProductMetadata(
                    Map.of("weight", "1.2kg", "material", "Rubber"),
                    List.of("http://images.kramp.com/belt1.jpg"),
                    new BigDecimal("45.99"),
                    12
            ),
            "P-2", new ProductMetadata(
                    Map.of("weight", "0.4kg", "material", "Steel"),
                    List.of("http://images.kramp.com/bearing2.jpg"),
                    new BigDecimal("12.50"),
                    0
            )
    );
    private static final Map<String, MarketConfiguration> MARKETS = Map.of(
            "nl-NL", new MarketConfiguration(
                    "EUR", BigDecimal.ONE, "Hoofdmagazijn EU - Nederland", "2-3 werkdagen",
                    Map.of(
                            "P-1", new LocalizedProductText("Tractor V-snaar", "Zware aandrijfriem"),
                            "P-2", new LocalizedProductText("Lager", "Stalen kogellager")
                    )
            ),
            "de-DE", new MarketConfiguration(
                    "EUR", BigDecimal.ONE, "Hauptlager EU - Deutschland", "2-3 Werktage",
                    Map.of(
                            "P-1", new LocalizedProductText("Trapezriemen", "Hochleistungs-Getrieberiemen"),
                            "P-2", new LocalizedProductText("Lager", "Stahllager mit Kugeln")
                    )
            ),
            "pl-PL", new MarketConfiguration(
                    "PLN", EUR_TO_PLN_RATE, "Magazyn glowny UE - Polska", "2-3 dni robocze",
                    Map.of(
                            "P-1", new LocalizedProductText("Pas klinowy", "Wytrzymały pasek przekładniowy"),
                            "P-2", new LocalizedProductText("Łożysko", "Stalowe łożysko kulkowe")
                    )
            )
    );

    /** Represents a catalog response. */
    public record CatalogResponse(String id, String name, String description, Map<String, String> specs,
                                  List<String> images) {
    }

    /** Represents successfully returned pricing data. */
    public record PricingResponse(
            BigDecimal basePrice,
            BigDecimal customerDiscount,
            BigDecimal finalPrice,
            String currency
    ) {
    }

    /** Represents successfully returned stock data. */
    public record AvailabilityResponse(
            int stockLevel,
            String warehouseLocation,
            String expectedDelivery
    ) {
    }

    /** Represents typed customer presentation preferences. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PreferencesResponse(
            @Nullable Boolean showPricesWithVat,
            @Nullable String preferredShippingMethod,
            @Nullable Boolean orderApprovalRequired,
            @Nullable String communicationChannel
    ) {
        private static PreferencesResponse empty() {
            return new PreferencesResponse(null, null, null, null);
        }
    }

    /** Represents a mock customer profile response. */
    public record CustomerResponse(String segment, PreferencesResponse preferences) {
    }

    public CatalogResponse catalog(String productId, String market) {
        ProductMetadata product = requireProduct(productId);
        LocalizedProductText productText = marketConfiguration(market).products().get(productId);
        if (productText == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        return new CatalogResponse(productId, productText.name(), productText.description(),
                product.specs(), product.images());
    }

    public PricingResponse pricing(String productId, String market, @Nullable String customerId) {
        ProductMetadata product = requireProduct(productId);
        MarketConfiguration configuration = marketConfiguration(market);
        BigDecimal basePrice = convertEur(product.basePriceInEur(), configuration.exchangeRate());
        BigDecimal customerDiscount = discountFor(customerId, configuration.exchangeRate());
        return new PricingResponse(
                basePrice,
                customerDiscount,
                basePrice.subtract(customerDiscount),
                configuration.currency()
        );
    }

    public AvailabilityResponse availability(String productId, String market) {
        ProductMetadata product = requireProduct(productId);
        MarketConfiguration configuration = marketConfiguration(market);
        return new AvailabilityResponse(
                product.stockLevel(),
                configuration.warehouseLocation(),
                configuration.expectedDelivery()
        );
    }

    public CustomerResponse customer(@Nullable String customerId) {
        if (customerId == null || customerId.isBlank()) {
            return new CustomerResponse("STANDARD", PreferencesResponse.empty());
        }
        return switch (customerId) {
            case PREMIUM_CUSTOMER_ID -> new CustomerResponse(
                    "B2B_PREMIUM",
                    new PreferencesResponse(true, "EXPRESS", false, "EMAIL")
            );
            case STANDARD_CUSTOMER_ID -> new CustomerResponse(
                    "B2C_STANDARD",
                    new PreferencesResponse(false, "STANDARD", false, "EMAIL")
            );
            default -> throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found: " + customerId);
        };
    }

    private ProductMetadata requireProduct(String productId) {
        ProductMetadata product = PRODUCTS.get(productId);
        if (product == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found: " + productId);
        }
        return product;
    }

    private MarketConfiguration marketConfiguration(String market) {
        MarketConfiguration configuration = MARKETS.get(market);
        if (configuration == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported market locale: " + market);
        }
        return configuration;
    }

    private BigDecimal discountFor(@Nullable String customerId, BigDecimal exchangeRate) {
        BigDecimal discountInEur = switch (customerId == null ? "" : customerId) {
            case PREMIUM_CUSTOMER_ID -> new BigDecimal("10.00");
            case STANDARD_CUSTOMER_ID -> new BigDecimal("5.00");
            default -> BigDecimal.ZERO;
        };
        return convertEur(discountInEur, exchangeRate);
    }

    private BigDecimal convertEur(BigDecimal amountInEur, BigDecimal exchangeRate) {
        return amountInEur.multiply(exchangeRate).setScale(2, RoundingMode.HALF_UP);
    }

    private record ProductMetadata(
            Map<String, String> specs,
            List<String> images,
            BigDecimal basePriceInEur,
            int stockLevel
    ) {
    }

    /** Stores a product name and description localized for a market. */
    private record LocalizedProductText(String name, String description) {
    }

    private record MarketConfiguration(
            String currency,
            BigDecimal exchangeRate,
            String warehouseLocation,
            String expectedDelivery,
            Map<String, LocalizedProductText> products
    ) {
    }
}
