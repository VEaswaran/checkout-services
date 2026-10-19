package com.company.checkout.common.config;

import com.company.checkout.common.client.CorrelationHeaderExchangeFilter;
import com.company.checkout.common.client.DownstreamLoggingExchangeFilter;
import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * One {@link WebClient} per downstream service, each with its own connection pool and timeouts so a
 * slow dependency cannot starve calls to the others (bulkhead).
 *
 * <p>Always start from the auto-configured {@link WebClient.Builder}: it carries the Micrometer
 * observation that propagates the W3C {@code traceparent} header and records
 * {@code http.client.requests} metrics. {@code WebClient.create()} would silently lose both.
 */
@Configuration(proxyBeanMethods = false)
public class WebClientConfig {

    public static final String PAYMENT_SERVICE = "payment-service";

    @Bean
    WebClient paymentWebClient(WebClient.Builder builder, DownstreamProperties properties) {
        return buildClient(builder, PAYMENT_SERVICE, properties.payment());
    }

    static WebClient buildClient(WebClient.Builder builder, String serviceName,
                                 DownstreamProperties.Endpoint endpoint) {
        ConnectionProvider pool = ConnectionProvider.builder(serviceName)
                .maxConnections(endpoint.maxConnections())
                .pendingAcquireTimeout(endpoint.pendingAcquireTimeout())
                .maxIdleTime(endpoint.maxIdleTime())
                .evictInBackground(endpoint.maxIdleTime())
                .metrics(true)
                .build();

        HttpClient httpClient = HttpClient.create(pool)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(endpoint.connectTimeout().toMillis()))
                .responseTimeout(endpoint.responseTimeout());

        return builder.clone() // the injected builder is shared; never mutate it directly
                .baseUrl(endpoint.baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .filter(new CorrelationHeaderExchangeFilter())
                .filter(new DownstreamLoggingExchangeFilter(serviceName))
                .build();
    }
}
