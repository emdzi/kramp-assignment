package com.kramp.assignment.mock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Boots the upstream mock server application. */
@SpringBootApplication
public class MockServerApplication {

    /**
     * Starts the mock server.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(MockServerApplication.class, args);
    }

}
