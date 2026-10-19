package com.company.checkout.common.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Inject {@link Clock} instead of calling {@code Instant.now()} in business code:
 * it makes time-dependent logic deterministic in tests ({@code Clock.fixed(...)}).
 */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
