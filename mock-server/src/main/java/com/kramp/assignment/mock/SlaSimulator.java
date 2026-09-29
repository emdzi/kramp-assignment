package com.kramp.assignment.mock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

/** Applies deterministic-configurable latency and availability behavior to mock upstreams. */
@Component
public class SlaSimulator {

    private final DoubleSupplier randomValue;
    private final Sleeper sleeper;

    /** Creates a simulator using real random values and thread sleep. */
    public SlaSimulator() {
        this(() -> ThreadLocalRandom.current().nextDouble(), Thread::sleep);
    }

    SlaSimulator(DoubleSupplier randomValue, Sleeper sleeper) {
        this.randomValue = randomValue;
        this.sleeper = sleeper;
    }

    /**
     * Delays the response by a randomized amount and may simulate an upstream outage.
     *
     * @param typicalLatencyMs typical response latency in milliseconds
     * @param reliability probability of a successful response, from zero to one
     */
    public void simulate(int typicalLatencyMs, double reliability) {
        long actualLatency = (long) (typicalLatencyMs * (0.8 + randomValue.getAsDouble() * 0.4));
        try {
            sleeper.sleep(actualLatency);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Mock upstream request interrupted", e);
        }

        if (randomValue.getAsDouble() > reliability) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Simulated upstream failure");
        }
    }

    @FunctionalInterface
    interface Sleeper {
        void sleep(long milliseconds) throws InterruptedException;
    }
}
