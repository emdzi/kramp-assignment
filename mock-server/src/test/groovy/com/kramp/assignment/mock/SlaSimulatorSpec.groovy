package com.kramp.assignment.mock

import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.DoubleSupplier

class SlaSimulatorSpec extends Specification {

    @Unroll
    def 'random latency stays within the configured plus or minus 20 percent range for random value #random'() {
        given:
        def sleeps = []
        def values = [random, 0.0d].iterator()
        def simulator = new SlaSimulator({ values.next() } as java.util.function.DoubleSupplier,
                { long milliseconds -> sleeps << milliseconds } as SlaSimulator.Sleeper)

        when:
        simulator.simulate(50, 0.999)

        then:
        sleeps == [expectedLatency]

        where:
        random | expectedLatency
        0.0d   | 40L
        0.5d   | 50L
        0.999d | 59L
    }

    def 'simulates an outage when the random failure sample exceeds the reliability'() {
        given:
        def sleeps = []
        def values = [0.0d, 0.9991d].iterator()
        def simulator = new SlaSimulator({ values.next() } as DoubleSupplier,
                { long milliseconds -> sleeps << milliseconds } as SlaSimulator.Sleeper)

        when:
        simulator.simulate(50, 0.999)

        then:
        def exception = thrown(ResponseStatusException)
        exception.statusCode == HttpStatus.SERVICE_UNAVAILABLE
        sleeps == [40L]
    }

    def 'restores interruption and reports an unavailable upstream'() {
        given:
        def randomCalls = 0
        def simulator = new SlaSimulator(
                { randomCalls++; 0.5d } as java.util.function.DoubleSupplier,
                { long milliseconds -> throw new InterruptedException() } as SlaSimulator.Sleeper)

        when:
        simulator.simulate(50, 0.999)

        then:
        def exception = thrown(ResponseStatusException)
        exception.statusCode == HttpStatus.SERVICE_UNAVAILABLE
        Thread.currentThread().isInterrupted()
        randomCalls == 1

        cleanup:
        Thread.interrupted()
    }
}
