package com.kramp.assignment.aggregator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots the product aggregation Spring application. */
@SpringBootApplication
public class AggregatorServiceApplication {

    /**
     * Starts the application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(AggregatorServiceApplication.class, args);
    }

}
