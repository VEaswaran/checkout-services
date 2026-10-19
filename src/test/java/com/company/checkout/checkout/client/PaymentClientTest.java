package com.company.checkout.checkout.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import com.company.checkout.checkout.client.dto.PaymentAuthorizationRequest;
import com.company.checkout.checkout.client.dto.PaymentAuthorizationResponse;
import com.company.checkout.common.client.CorrelationHeaderExchangeFilter;
import com.company.checkout.common.config.DownstreamProperties;
import com.company.checkout.common.exception.DownstreamServiceException;
import com.company.checkout.common.exception.ErrorCode;
import com.company.checkout.common.logging.MdcKeys;
import com.company.checkout.common.web.ApiHeaders;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Tests the client's resilience behaviour with a stubbed {@link ExchangeFunction} — no network,
 * no extra libraries. For full HTTP-level tests add WireMock or OkHttp MockWebServer.
 */
class PaymentClientTest {

    private static final DownstreamProperties PROPERTIES = new DownstreamProperties(new DownstreamProperties.Endpoint(
            "http://payment.test", Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(5),
            10, Duration.ofSeconds(1), Duration.ofSeconds(30),
            new DownstreamProperties.Retry(2, Duration.ofMillis(1))));

    private static final PaymentAuthorizationRequest REQUEST =
            new PaymentAuthorizationRequest("chk_1", new BigDecimal("20.00"), "USD", "tok_ok");

    private final List<ClientRequest> sentRequests = new ArrayList<>();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void returnsBodyAndForwardsCorrelationAndIdempotencyHeaders() {
        MDC.put(MdcKeys.CORRELATION_ID, "corr-42");
        PaymentClient client = clientReturning(() -> json(HttpStatus.OK,
                "{\"paymentId\":\"pay_1\",\"status\":\"AUTHORIZED\",\"newField\":\"ignored\"}"));

        PaymentAuthorizationResponse response = client.authorize(REQUEST);

        assertThat(response.authorized()).isTrue();
        assertThat(response.paymentId()).isEqualTo("pay_1");
        ClientRequest sent = sentRequests.getFirst();
        assertThat(sent.headers().getFirst(ApiHeaders.X_CORRELATION_ID)).isEqualTo("corr-42");
        assertThat(sent.headers().getFirst(ApiHeaders.IDEMPOTENCY_KEY)).isEqualTo("chk_1");
    }

    @Test
    void retriesTransient503ThenFailsWithServiceUnavailable() {
        PaymentClient client = clientReturning(() -> json(HttpStatus.SERVICE_UNAVAILABLE, "{\"error\":\"down\"}"));

        assertThatThrownBy(() -> client.authorize(REQUEST))
                .isInstanceOfSatisfying(DownstreamServiceException.class, ex -> {
                    assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOWNSTREAM_UNAVAILABLE);
                    assertThat(ex.downstreamStatus()).isEqualTo(503);
                });
        assertThat(sentRequests).hasSize(3); // 1 attempt + 2 retries
    }

    @Test
    void doesNotRetryClientErrors() {
        PaymentClient client = clientReturning(() -> json(HttpStatus.BAD_REQUEST, "{\"error\":\"bad\"}"));

        assertThatThrownBy(() -> client.authorize(REQUEST))
                .isInstanceOfSatisfying(DownstreamServiceException.class,
                        ex -> assertThat(ex.errorCode()).isEqualTo(ErrorCode.DOWNSTREAM_ERROR));
        assertThat(sentRequests).hasSize(1);
    }

    @Test
    void recoversWhenRetrySucceeds() {
        AtomicInteger calls = new AtomicInteger();
        PaymentClient client = clientReturning(() -> calls.incrementAndGet() == 1
                ? json(HttpStatus.BAD_GATEWAY, "")
                : json(HttpStatus.OK, "{\"paymentId\":\"pay_2\",\"status\":\"AUTHORIZED\"}"));

        assertThat(client.authorize(REQUEST).paymentId()).isEqualTo("pay_2");
        assertThat(sentRequests).hasSize(2);
    }

    private PaymentClient clientReturning(java.util.function.Supplier<ClientResponse> responder) {
        ExchangeFunction exchange = request -> {
            sentRequests.add(request);
            return Mono.fromSupplier(responder);
        };
        WebClient webClient = WebClient.builder()
                .baseUrl(PROPERTIES.payment().baseUrl())
                .exchangeFunction(exchange)
                .filter(new CorrelationHeaderExchangeFilter())
                .build();
        return new PaymentClient(webClient, PROPERTIES);
    }

    private static ClientResponse json(HttpStatus status, String body) {
        return ClientResponse.create(status)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build();
    }
}
