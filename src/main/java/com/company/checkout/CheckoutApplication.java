package com.company.checkout;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point of the checkout service.
 *
 * <p>Package layout (package-by-feature, with a small shared kernel):
 * <pre>
 * com.company.checkout
 * ├── common            shared, feature-agnostic building blocks
 * │   ├── client        WebClient base class + exchange filters (logging, correlation)
 * │   ├── config        Spring configuration and typed properties
 * │   ├── exception     ErrorCode catalogue, exception hierarchy, global handler
 * │   ├── filter        servlet filters (correlation id, API event log)
 * │   ├── logging       MDC keys, structured log field names, Reactor MDC bridge
 * │   ├── util          small stateless helpers (masking, ids)
 * │   └── web           HTTP header constants
 * ├── checkout          one feature = one package; copy this shape for new features
 * │   ├── api           controller + request/response DTOs + mapper (HTTP contract)
 * │   ├── domain        aggregate, value objects, invariants (no Spring, no HTTP)
 * │   ├── service       use-case orchestration, transactions, logging of business events
 * │   ├── repository    persistence port + adapter
 * │   └── client        downstream service adapters (WebClient)
 * └── stub              local-profile fakes of downstream services
 * </pre>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class CheckoutApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckoutApplication.class, args);
    }
}
