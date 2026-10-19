package com.company.checkout.common.client;

import java.util.Map;

import com.company.checkout.common.logging.MdcKeys;
import com.company.checkout.common.logging.ReactorMdc;
import com.company.checkout.common.web.ApiHeaders;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import reactor.core.publisher.Mono;

/**
 * Forwards the business {@code X-Correlation-Id} to downstream services.
 * The W3C {@code traceparent} header is added separately by Micrometer's WebClient observation.
 *
 * <p>Reads from the Reactor context (not the thread's MDC) because on retries this runs on a
 * timer thread where MDC is empty.
 */
public final class CorrelationHeaderExchangeFilter implements ExchangeFilterFunction {

    @Override
    public Mono<ClientResponse> filter(ClientRequest request, ExchangeFunction next) {
        return Mono.deferContextual(context -> {
            Map<String, String> mdc = ReactorMdc.from(context);
            String correlationId = mdc.get(MdcKeys.CORRELATION_ID);
            if (correlationId == null || request.headers().containsHeader(ApiHeaders.X_CORRELATION_ID)) {
                return next.exchange(request);
            }
            ClientRequest withCorrelation = ClientRequest.from(request)
                    .header(ApiHeaders.X_CORRELATION_ID, correlationId)
                    .build();
            return next.exchange(withCorrelation);
        });
    }
}
