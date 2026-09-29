package com.kramp.assignment.aggregator.infrastructure.adapter.outbound.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;

import java.time.Duration;

/** Configures shared HTTP timeouts for outbound upstream requests. */
@Configuration(proxyBeanMethods = false)
class UpstreamRestClientConfiguration {

    /**
     * Creates a REST client customizer with configured connect and read timeouts.
     *
     * @param connectTimeout maximum time to establish a connection
     * @param readTimeout maximum time to wait for response data
     * @return customizer applying the timeout settings
     */
    @Bean
    RestClientCustomizer upstreamRestClientCustomizer(
            @Value("${app.upstream.http.connect-timeout}") Duration connectTimeout,
            @Value("${app.upstream.http.read-timeout}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        return restClientBuilder -> restClientBuilder.requestFactory(requestFactory);
    }
}
