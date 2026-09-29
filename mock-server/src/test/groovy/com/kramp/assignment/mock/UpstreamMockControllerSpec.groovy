package com.kramp.assignment.mock

import com.kramp.assignment.mock.controller.UpstreamMockController
import com.kramp.assignment.mock.controller.UpstreamMockData
import org.springframework.http.HttpStatus
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class UpstreamMockControllerSpec extends Specification {

    def 'returns representative upstream data and uses the required SLA configuration'() {
        given:
        def simulator = Mock(SlaSimulator)
        def controller = newController(simulator)

        when:
        def catalog = controller.getCatalog('P-1', 'nl-NL')
        def pricing = controller.getPricing('P-1', 'nl-NL', 'TEST-CUSTOMER-100042')
        def availability = controller.getAvailability('P-1', 'nl-NL')
        def customer = controller.getCustomer('TEST-CUSTOMER-100042')

        then:
        1 * simulator.simulate(50, 0.999)
        1 * simulator.simulate(80, 0.995)
        1 * simulator.simulate(100, 0.98)
        1 * simulator.simulate(60, 0.99)

        and:
        catalog.id() == 'P-1'
        catalog.name()
        catalog.description()
        catalog.specs()
        catalog.images()
        pricing.basePrice()
        pricing.customerDiscount()
        pricing.finalPrice()
        pricing.basePrice() == new BigDecimal('45.99')
        pricing.customerDiscount() == new BigDecimal('10.00')
        pricing.finalPrice() == new BigDecimal('35.99')
        pricing.currency() == 'EUR'
        availability.stockLevel() > 0
        availability.warehouseLocation()
        availability.expectedDelivery()
        customer.segment() == 'B2B_PREMIUM'
        customer.preferences().showPricesWithVat()
        customer.preferences().preferredShippingMethod() == 'EXPRESS'
        !customer.preferences().orderApprovalRequired()
        customer.preferences().communicationChannel() == 'EMAIL'
    }

    def 'localizes catalog text and uses PLN for the Polish market'() {
        given:
        def simulator = Mock(SlaSimulator)
        def controller = newController(simulator)

        when:
        def catalog = controller.getCatalog('P-1', 'pl-PL')
        def pricing = controller.getPricing('P-1', 'pl-PL', 'TEST-CUSTOMER-100042')
        def availability = controller.getAvailability('P-1', 'pl-PL')

        then:
        1 * simulator.simulate(50, 0.999)
        1 * simulator.simulate(80, 0.995)
        1 * simulator.simulate(100, 0.98)
        catalog.name() == 'Pas klinowy'
        catalog.description() == 'Wytrzymały pasek przekładniowy'
        pricing.basePrice() == new BigDecimal('197.76')
        pricing.customerDiscount() == new BigDecimal('43.00')
        pricing.finalPrice() == new BigDecimal('154.76')
        pricing.currency() == 'PLN'
        availability.warehouseLocation() == 'Magazyn glowny UE - Polska'
        availability.expectedDelivery() == '2-3 dni robocze'
    }

    def 'returns the standard customer segment without preferences when no ID is provided'() {
        given:
        def simulator = Mock(SlaSimulator)
        def controller = newController(simulator)

        when:
        def customer = controller.getCustomer(null)

        then:
        1 * simulator.simulate(60, 0.99)
        customer.segment() == 'STANDARD'
        customer.preferences().showPricesWithVat() == null
        customer.preferences().preferredShippingMethod() == null
        customer.preferences().orderApprovalRequired() == null
        customer.preferences().communicationChannel() == null
    }

    def 'returns a personalized segment and preferences when a customer ID is provided'() {
        given:
        def simulator = Mock(SlaSimulator)
        def controller = newController(simulator)

        when:
        def customer = controller.getCustomer('TEST-CUSTOMER-100042')

        then:
        1 * simulator.simulate(60, 0.99)
        customer.segment() == 'B2B_PREMIUM'
        customer.preferences().preferredShippingMethod() == 'EXPRESS'
    }

    def 'uses a separate discount and customer profile for the second sample customer'() {
        given:
        def simulator = Mock(SlaSimulator)
        def controller = newController(simulator)

        when:
        def pricing = controller.getPricing('P-1', 'nl-NL', 'TEST-CUSTOMER-200017')
        def customer = controller.getCustomer('TEST-CUSTOMER-200017')

        then:
        1 * simulator.simulate(80, 0.995)
        1 * simulator.simulate(60, 0.99)
        pricing.customerDiscount() == new BigDecimal('5.00')
        pricing.finalPrice() == new BigDecimal('40.99')
        customer.segment() == 'B2C_STANDARD'
        customer.preferences().preferredShippingMethod() == 'STANDARD'
    }

    def 'returns a price and known zero stock for a known product'() {
        given:
        def simulator = Mock(SlaSimulator)
        def controller = newController(simulator)

        when:
        def pricing = controller.getPricing('P-2', 'nl-NL', null)
        def availability = controller.getAvailability('P-2', 'nl-NL')

        then:
        1 * simulator.simulate(80, 0.995)
        1 * simulator.simulate(100, 0.98)
        pricing.finalPrice() == new BigDecimal('12.50')
        availability.stockLevel() == 0
    }

    def 'returns HTTP 503 when an upstream failure is simulated'() {
        given:
        def simulator = Mock(SlaSimulator)
        def mockMvc = MockMvcBuilders.standaloneSetup(newController(simulator)).build()

        when:
        def response = mockMvc.perform(get('/api/v1/upstream/catalog/P-1').param('market', 'nl-NL'))
                .andExpect(status().isServiceUnavailable())
                .andReturn()

        then:
        1 * simulator.simulate(50, 0.999) >> {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    'Simulated upstream failure')
        }
        response.response.status == 503
    }

    def 'exposes all upstream data sources over REST with optional customer profiles'() {
        given:
        def simulator = Mock(SlaSimulator)
        def mockMvc = MockMvcBuilders.standaloneSetup(newController(simulator)).build()

        when:
        mockMvc.perform(get('/api/v1/upstream/catalog/P-1').param('market', 'pl-PL'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.id').value('P-1'))
                .andExpect(jsonPath('$.name').value('Pas klinowy'))
        mockMvc.perform(get('/api/v1/upstream/pricing/P-1')
                .param('market', 'pl-PL')
                .param('customerId', 'TEST-CUSTOMER-100042'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.currency').value('PLN'))
                .andExpect(jsonPath('$.basePrice').value(197.76))
        mockMvc.perform(get('/api/v1/upstream/availability/P-1').param('market', 'pl-PL'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.warehouseLocation').value('Magazyn glowny UE - Polska'))
        mockMvc.perform(get('/api/v1/upstream/customer'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.segment').value('STANDARD'))
                .andExpect(jsonPath('$.preferences').isEmpty())
        mockMvc.perform(get('/api/v1/upstream/customer/TEST-CUSTOMER-100042'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.segment').value('B2B_PREMIUM'))
                .andExpect(jsonPath('$.preferences.showPricesWithVat').value(true))
                .andExpect(jsonPath('$.preferences.preferredShippingMethod').value('EXPRESS'))
        mockMvc.perform(get('/api/v1/upstream/pricing/P-1')
                .param('market', 'nl-NL')
                .param('customerId', 'TEST-CUSTOMER-200017'))
                .andExpect(status().isOk())
                .andExpect(jsonPath('$.customerDiscount').value(5.0))
        mockMvc.perform(get('/api/v1/upstream/catalog/UNKNOWN').param('market', 'nl-NL'))
                .andExpect(status().isNotFound())

        then:
        1 * simulator.simulate(50, 0.999)
        1 * simulator.simulate(80, 0.995)
        1 * simulator.simulate(100, 0.98)
        2 * simulator.simulate(60, 0.99)
        1 * simulator.simulate(80, 0.995)
        1 * simulator.simulate(50, 0.999)
    }

    private static UpstreamMockController newController(SlaSimulator simulator) {
        new UpstreamMockController(simulator, new UpstreamMockData())
    }
}
