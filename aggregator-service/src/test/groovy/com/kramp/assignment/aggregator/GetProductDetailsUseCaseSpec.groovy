package com.kramp.assignment.aggregator

import com.kramp.assignment.aggregator.application.service.GetProductDetailsUseCaseImpl
import com.kramp.assignment.aggregator.domain.*
import com.kramp.assignment.aggregator.domain.port.outbound.*
import spock.lang.Specification

import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GetProductDetailsUseCaseSpec extends Specification {

    def 'starts every requested upstream lookup concurrently'() {
        given:
        def productCatalog = Mock(ProductCatalog)
        def productPricing = Mock(ProductPricing)
        def stockAvailability = Mock(StockAvailability)
        def customerProfiles = Mock(CustomerProfiles)
        def allLookupsStarted = new CountDownLatch(4)
        def product = new Product('P-1', 'Product', 'Description', [:], [])
        def price = new Price(null, new BigDecimal('10.00'),
                BigDecimal.ZERO, new BigDecimal('10.00'), 'EUR')
        def stock = new Stock(null, 5, 'Warehouse', '2 days')
        def customer = CustomerInfo.standardProfile()
        def useCase = new GetProductDetailsUseCaseImpl(
                productCatalog, productPricing, stockAvailability, customerProfiles)

        when:
        def result = useCase.execute(new AggregationContext('P-1', 'nl-NL', 'TEST-CUSTOMER-100042'))

        then:
        1 * productCatalog.getProduct('P-1', 'nl-NL') >> {
            awaitAllLookups(allLookupsStarted)
            product
        }
        1 * productPricing.getPrice('P-1', 'nl-NL', 'TEST-CUSTOMER-100042') >> {
            awaitAllLookups(allLookupsStarted)
            price
        }
        1 * stockAvailability.getStock('P-1', 'nl-NL') >> {
            awaitAllLookups(allLookupsStarted)
            stock
        }
        1 * customerProfiles.getCustomerProfile('TEST-CUSTOMER-100042') >> {
            awaitAllLookups(allLookupsStarted)
            Optional.of(customer)
        }
        result.product() == product
        result.price() == price
        result.stock() == stock
        result.customerInfo() == customer
    }

    private static void awaitAllLookups(CountDownLatch allLookupsStarted) {
        allLookupsStarted.countDown()
        if (!allLookupsStarted.await(1, TimeUnit.SECONDS)) {
            throw new AssertionError('Upstream lookups were not started concurrently')
        }
    }
}
