package com.company.checkout.common.config;

import java.time.Duration;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed, validated settings for every downstream service. The app fails fast at startup when a
 * value is missing — far better than discovering a null base URL on the first checkout.
 *
 * <pre>
 * checkout.downstream.payment.base-url=https://payment.internal
 * checkout.downstream.payment.connect-timeout=2s
 * </pre>
 *
 * Add a new downstream service by adding a field of type {@link Endpoint}.
 */
@Validated
@ConfigurationProperties(prefix = "checkout.downstream")
public record DownstreamProperties(@Valid @NotNull Endpoint payment) {

    public record Endpoint(
            @NotBlank String baseUrl,
            /* TCP connect timeout. */
            @NotNull Duration connectTimeout,
            /* Max wait for the response after the request is sent (Netty response timeout). */
            @NotNull Duration responseTimeout,
            /* Hard ceiling for one logical call including retries. */
            @NotNull Duration overallTimeout,
            @Min(1) int maxConnections,
            /* How long a request may wait for a free pooled connection. */
            @NotNull Duration pendingAcquireTimeout,
            /* Close idle connections before load balancers silently drop them. */
            @NotNull Duration maxIdleTime,
            @Valid @NotNull Retry retry) {
    }

    public record Retry(@Min(0) @Max(5) int maxAttempts, @NotNull Duration initialBackoff) {
    }
}
