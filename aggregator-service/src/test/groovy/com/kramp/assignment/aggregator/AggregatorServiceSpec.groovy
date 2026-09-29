package com.kramp.assignment.aggregator

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.hamcrest.Matchers
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.springframework.test.web.servlet.MockMvc
import spock.lang.Specification
import spock.lang.Unroll

import java.nio.charset.StandardCharsets
import java.math.RoundingMode
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest
@AutoConfigureMockMvc
class AggregatorServiceSpec extends Specification {

    private static final Set<String> REQUEST_URIS = ConcurrentHashMap.newKeySet()
    private static final Set<String> FAILING_UPSTREAMS = ConcurrentHashMap.newKeySet()
    private static final ConcurrentHashMap<String, AtomicInteger> UPSTREAM_CALL_COUNTS = new ConcurrentHashMap<>()
    private static HttpServer upstreamServer
    private static ExecutorService upstreamExecutor

    @Autowired
    MockMvc mockMvc

    @Autowired
    CircuitBreakerRegistry circuitBreakerRegistry

    @DynamicPropertySource
    static void registerUpstreamBaseUrl(DynamicPropertyRegistry registry) {
        upstreamServer = HttpServer.create(new InetSocketAddress('localhost', 0), 0)
        upstreamExecutor = Executors.newCachedThreadPool()
        upstreamServer.executor = upstreamExecutor
        ['catalog', 'pricing', 'availability', 'customer'].each { service ->
            upstreamServer.createContext("/api/v1/upstream/${service}") { HttpExchange exchange ->
                handleUpstreamRequest(exchange, service)
            }
        }
        upstreamServer.start()

        registry.add('app.upstream.base-url') {
            "http://localhost:${upstreamServer.address.port}/api/v1/upstream"
        }
    }

    def cleanupSpec() {
        upstreamServer?.stop(0)
        upstreamExecutor?.shutdownNow()
    }

    def setup() {
        REQUEST_URIS.clear()
        FAILING_UPSTREAMS.clear()
        UPSTREAM_CALL_COUNTS.clear()
        circuitBreakerRegistry.allCircuitBreakers.each { it.reset() }
    }

    def 'aggregates successful responses from every upstream service'() {
        when:
        def result = mockMvc.perform(get('/api/v1/products/P-1')
                .param('market', 'nl-NL')
                .param('customerId', 'TEST-CUSTOMER-100042'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.product.id').value('P-1'))
                .andExpect(jsonPath('$.product.name').value('Tractor V-snaar'))
                .andExpect(jsonPath('$.product.description').value('Zware aandrijfriem'))
                .andExpect(jsonPath('$.product.specs.material').value('Rubber'))
                .andExpect(jsonPath('$.product.images[0]').value('http://images.kramp.com/belt1.jpg'))
                .andExpect(jsonPath('$.price.basePrice').value(45.99))
                .andExpect(jsonPath('$.price.status').doesNotExist())
                .andExpect(jsonPath('$.price.customerDiscount').value(10.0))
                .andExpect(jsonPath('$.price.finalPrice').value(35.99))
                .andExpect(jsonPath('$.price.currency').value('EUR'))
                .andExpect(jsonPath('$.stock.status').doesNotExist())
                .andExpect(jsonPath('$.stock.stockLevel').value(12))
                .andExpect(jsonPath('$.stock.warehouseLocation').value('Hoofdmagazijn EU - Nederland'))
                .andExpect(jsonPath('$.stock.expectedDelivery').value('2-3 werkdagen'))
                .andExpect(jsonPath('$.customerInfo.segment').value('B2B_PREMIUM'))
                .andExpect(jsonPath('$.customerInfo.preferences.showPricesWithVat').value(true))
                .andExpect(jsonPath('$.customerInfo.preferences.preferredShippingMethod').value('EXPRESS'))
                .andExpect(jsonPath('$.customerInfo.preferences.orderApprovalRequired').value(false))
                .andExpect(jsonPath('$.customerInfo.preferences.communicationChannel').value('EMAIL'))
                .andReturn()

        then:
        result.response.status == 200
        REQUEST_URIS == [
                '/api/v1/upstream/catalog/P-1?market=nl-NL',
                '/api/v1/upstream/pricing/P-1?market=nl-NL&customerId=TEST-CUSTOMER-100042',
                '/api/v1/upstream/availability/P-1?market=nl-NL',
                '/api/v1/upstream/customer/TEST-CUSTOMER-100042'
        ] as Set
    }

    @Unroll
    def 'returns localized product text and the appropriate currency for market #market'() {
        expect:
        mockMvc.perform(get('/api/v1/products/P-1')
                .param('market', market)
                .param('customerId', 'TEST-CUSTOMER-100042'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.product.name').value(productName))
                .andExpect(jsonPath('$.product.description').value(description))
                .andExpect(jsonPath('$.price.basePrice').value(basePrice))
                .andExpect(jsonPath('$.price.customerDiscount').value(customerDiscount))
                .andExpect(jsonPath('$.price.finalPrice').value(finalPrice))
                .andExpect(jsonPath('$.price.currency').value(currency))

        where:
        market  | productName       | description                      | basePrice | customerDiscount | finalPrice | currency
        'nl-NL' | 'Tractor V-snaar' | 'Zware aandrijfriem'             | 45.99     | 10.0             | 35.99      | 'EUR'
        'de-DE' | 'Trapezriemen'    | 'Hochleistungs-Getrieberiemen'   | 45.99     | 10.0             | 35.99      | 'EUR'
        'pl-PL' | 'Pas klinowy'     | 'Wytrzymały pasek przekładniowy' | 197.76    | 43.0             | 154.76     | 'PLN'
    }

    def 'defaults market to nl-NL when the market parameter is omitted'() {
        when:
        def result = mockMvc.perform(get('/api/v1/products/P-1'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.product.name').value('Tractor V-snaar'))
                .andExpect(jsonPath('$.price.currency').value('EUR'))
                .andExpect(jsonPath('$.customerInfo.segment').value('STANDARD'))
                .andExpect(jsonPath('$.customerInfo.preferences').value([:]))
                .andReturn()

        then:
        result.response.status == 200
        REQUEST_URIS.contains('/api/v1/upstream/catalog/P-1?market=nl-NL')
        !REQUEST_URIS.any { it.startsWith('/api/v1/upstream/customer') }
    }

    def 'returns the standard customer profile when customer service is unavailable'() {
        given:
        FAILING_UPSTREAMS.add('customer')

        when:
        def result = mockMvc.perform(get('/api/v1/products/P-1')
                .param('market', 'nl-NL')
                .param('customerId', 'TEST-CUSTOMER-UNAVAILABLE'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.customerInfo.segment').value('STANDARD'))
                .andExpect(jsonPath('$.customerInfo.preferences').value([:]))
                .andReturn()

        then:
        result.response.status == 200
        REQUEST_URIS.contains('/api/v1/upstream/customer/TEST-CUSTOMER-UNAVAILABLE')
    }

    def 'fails the whole request when catalog is unavailable'() {
        given:
        FAILING_UPSTREAMS.add('catalog')

        expect:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath('$.title').value('Service Unavailable'))
    }

    def 'returns not found when catalog has no matching product'() {
        expect:
        mockMvc.perform(get('/api/v1/products/MISSING').param('market', 'nl-NL'))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.title').value('Product Not Found'))
    }

    def 'returns not found when no endpoint matches the request path'() {
        expect:
        mockMvc.perform(get('/api/v1/products'))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath('$.title').value('Resource Not Found'))
                .andExpect(jsonPath('$.status').value(404))
    }

    def 'returns product with unavailable price when pricing service fails'() {
        given:
        FAILING_UPSTREAMS.add('pricing')

        expect:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.product.id').value('P-1'))
                .andExpect(jsonPath('$.price.status').value('UNAVAILABLE'))
                .andExpect(jsonPath('$.price.basePrice').value(Matchers.nullValue()))
                .andExpect(jsonPath('$.price.customerDiscount').value(Matchers.nullValue()))
                .andExpect(jsonPath('$.price.finalPrice').value(Matchers.nullValue()))
                .andExpect(jsonPath('$.price.currency').value(Matchers.nullValue()))
    }

    def 'returns product with unknown stock when availability service fails'() {
        given:
        FAILING_UPSTREAMS.add('availability')

        expect:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.product.id').value('P-1'))
                .andExpect(jsonPath('$.stock.status').value('UNKNOWN'))
                .andExpect(jsonPath('$.stock.stockLevel').value(Matchers.nullValue()))
                .andExpect(jsonPath('$.stock.warehouseLocation').value(Matchers.nullValue()))
                .andExpect(jsonPath('$.stock.expectedDelivery').value(Matchers.nullValue()))
    }

    def 'returns a price and a known zero stock quantity when both sources respond'() {
        expect:
        mockMvc.perform(get('/api/v1/products/P-2').param('market', 'nl-NL'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.price.status').doesNotExist())
                .andExpect(jsonPath('$.price.finalPrice').value(12.5))
                .andExpect(jsonPath('$.stock.status').doesNotExist())
                .andExpect(jsonPath('$.stock.stockLevel').value(0))
    }

    def 'uses different customer-specific discounts for the two sample customers'() {
        expect:
        mockMvc.perform(get('/api/v1/products/P-1')
                .param('market', 'nl-NL')
                .param('customerId', 'TEST-CUSTOMER-100042'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.price.customerDiscount').value(10.0))
                .andExpect(jsonPath('$.customerInfo.segment').value('B2B_PREMIUM'))

        and:
        mockMvc.perform(get('/api/v1/products/P-1')
                .param('market', 'nl-NL')
                .param('customerId', 'TEST-CUSTOMER-200017'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.price.customerDiscount').value(5.0))
                .andExpect(jsonPath('$.customerInfo.segment').value('B2C_STANDARD'))
                .andExpect(jsonPath('$.customerInfo.preferences.showPricesWithVat').value(false))
    }

    def 'opens pricing circuit breaker after repeated upstream failures and skips subsequent calls'() {
        given:
        FAILING_UPSTREAMS.add('pricing')
        CircuitBreaker pricingCircuitBreaker = circuitBreakerRegistry.circuitBreaker('productPricing')

        when:
        10.times {
            mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath('$.product.id').value('P-1'))
                    .andExpect(jsonPath('$.price.status').value('UNAVAILABLE'))
        }

        then:
        pricingCircuitBreaker.state == CircuitBreaker.State.OPEN
        UPSTREAM_CALL_COUNTS.get('pricing').get() == 10

        when:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.product.id').value('P-1'))
                .andExpect(jsonPath('$.price.status').value('UNAVAILABLE'))

        then:
        pricingCircuitBreaker.state == CircuitBreaker.State.OPEN
        UPSTREAM_CALL_COUNTS.get('pricing').get() == 10
    }

    def 'returns localized stock details for each market'() {
        expect:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', market))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.stock.status').doesNotExist())
                .andExpect(jsonPath('$.stock.warehouseLocation').value(warehouse))
                .andExpect(jsonPath('$.stock.expectedDelivery').value(delivery))

        where:
        market  | warehouse                       | delivery
        'nl-NL' | 'Hoofdmagazijn EU - Nederland'  | '2-3 werkdagen'
        'de-DE' | 'Hauptlager EU - Deutschland'   | '2-3 Werktage'
        'pl-PL' | 'Magazyn glowny UE - Polska'    | '2-3 dni robocze'
    }

    def 'rejects an unsupported market locale'() {
        expect:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', 'en-US'))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.title').value('Invalid Request'))
    }

    /** Confirms that a blank market is rejected with an RFC 7807 response. */
    def 'rejects blank market with a problem detail response'() {
        when:
        def result = mockMvc.perform(get('/api/v1/products/P-1')
                .param('market', ' '))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath('$.title').value('Invalid Request'))
                .andExpect(jsonPath('$.status').value(400))
                .andReturn()

        then:
        result.response.status == 400
    }

    def 'returns service unavailable when catalog circuit breaker is already open'() {
        given:
        FAILING_UPSTREAMS.add('catalog')
        CircuitBreaker catalogCircuitBreaker = circuitBreakerRegistry.circuitBreaker('productCatalog')

        when:
        10.times {
            mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                    .andExpect(status().isServiceUnavailable())
        }
        int catalogCallsWhenOpened = UPSTREAM_CALL_COUNTS.get('catalog').get()

        then:
        catalogCircuitBreaker.state == CircuitBreaker.State.OPEN

        when:
        mockMvc.perform(get('/api/v1/products/P-1').param('market', 'nl-NL'))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath('$.title').value('Service Unavailable'))

        then:
        catalogCircuitBreaker.state == CircuitBreaker.State.OPEN
        UPSTREAM_CALL_COUNTS.get('catalog').get() == catalogCallsWhenOpened
    }

    private static void handleUpstreamRequest(HttpExchange exchange, String service) throws IOException {
        REQUEST_URIS.add(exchange.requestURI.toString())
        UPSTREAM_CALL_COUNTS.computeIfAbsent(service) { new AtomicInteger() }.incrementAndGet()
        if (FAILING_UPSTREAMS.contains(service)) {
            writeResponse(exchange, 503, '{"error":"unavailable"}')
            return
        }
        if (service == 'catalog' && exchange.requestURI.path.endsWith('/MISSING')) {
            writeResponse(exchange, 404, '{"error":"not found"}')
            return
        }
        writeJsonResponse(exchange, responseFor(service, exchange.requestURI.path, exchange.requestURI.query))
    }

    private static void writeJsonResponse(HttpExchange exchange, String body) throws IOException {
        writeResponse(exchange, 200, body)
    }

    private static void writeResponse(HttpExchange exchange, int status, String body) throws IOException {
        byte[] response = body.getBytes(StandardCharsets.UTF_8)
        exchange.responseHeaders.set('Content-Type', 'application/json')
        exchange.sendResponseHeaders(status, response.length)
        exchange.responseBody.withCloseable { it.write(response) }
    }

    private static String responseFor(String service, String path, String query) {
        def market = query?.split('&')
                ?.find { it.startsWith('market=') }
                ?.substring('market='.length())
        def segments = path.split('/')
        def identifier = segments[-1]
        if (service == 'catalog') {
            def translations = [
                    'nl-NL': ['Tractor V-snaar', 'Zware aandrijfriem', 'Lager', 'Stalen kogellager'],
                    'de-DE': ['Trapezriemen', 'Hochleistungs-Getrieberiemen', 'Lager', 'Stahllager mit Kugeln'],
                    'pl-PL': ['Pas klinowy', 'Wytrzymały pasek przekładniowy', 'Łożysko', 'Stalowe łożysko kulkowe']
            ]
            def index = identifier == 'P-2' ? 2 : 0
            def localized = translations[market] ?: translations['nl-NL']
            def name = localized[index]
            def description = localized[index + 1]
            def specs = identifier == 'P-2' ? '{"weight":"0.4kg","material":"Steel"}' : '{"weight":"1.2kg","material":"Rubber"}'
            def image = identifier == 'P-2' ? 'bearing2.jpg' : 'belt1.jpg'
            return """{"id":"${identifier}","name":"${name}","description":"${description}","specs":${specs},"images":["http://images.kramp.com/${image}"]}"""
        }
        if (service == 'pricing') {
            def customerId = query?.split('&')?.find { it.startsWith('customerId=') }?.substring('customerId='.length())
            def discountEur = customerId == 'TEST-CUSTOMER-100042' ? 10.00 : customerId == 'TEST-CUSTOMER-200017' ? 5.00 : 0.00
            def rate = market == 'pl-PL' ? 4.30 : 1.00
            def basePriceEur = identifier == 'P-2' ? '12.50' : '45.99'
            def basePrice = new BigDecimal(basePriceEur).multiply(new BigDecimal(rate.toString())).setScale(2, RoundingMode.HALF_UP)
            def discount = new BigDecimal(discountEur.toString()).multiply(new BigDecimal(rate.toString())).setScale(2, RoundingMode.HALF_UP)
            def currency = market == 'pl-PL' ? 'PLN' : 'EUR'
            return """{"basePrice":${basePrice},"customerDiscount":${discount},"finalPrice":${basePrice.subtract(discount)},"currency":"${currency}"}"""
        }
        if (service == 'availability') {
            def availability = [
                    'nl-NL': ['Hoofdmagazijn EU - Nederland', '2-3 werkdagen'],
                    'de-DE': ['Hauptlager EU - Deutschland', '2-3 Werktage'],
                    'pl-PL': ['Magazyn glowny UE - Polska', '2-3 dni robocze']
            ]
            def (warehouse, delivery) = availability[market] ?: availability['nl-NL']
            def stockLevel = identifier == 'P-2' ? 0 : 12
            return """{"stockLevel":${stockLevel},"warehouseLocation":"${warehouse}","expectedDelivery":"${delivery}"}"""
        }
        identifier == 'customer' ?
                '{"segment":"STANDARD","preferences":{}}' :
                identifier == 'TEST-CUSTOMER-200017' ?
                        '{"segment":"B2C_STANDARD","preferences":{"showPricesWithVat":false,"preferredShippingMethod":"STANDARD","orderApprovalRequired":false,"communicationChannel":"EMAIL"}}' :
                        '{"segment":"B2B_PREMIUM","preferences":{"showPricesWithVat":true,"preferredShippingMethod":"EXPRESS","orderApprovalRequired":false,"communicationChannel":"EMAIL"}}'
    }
}
