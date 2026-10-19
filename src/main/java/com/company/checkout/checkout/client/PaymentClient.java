package com.company.checkout.checkout.client;

import com.company.checkout.checkout.client.dto.PaymentAuthorizationRequest;
import com.company.checkout.checkout.client.dto.PaymentAuthorizationResponse;
import com.company.checkout.common.client.AbstractDownstreamClient;
import com.company.checkout.common.config.DownstreamProperties;
import com.company.checkout.common.config.WebClientConfig;
import com.company.checkout.common.web.ApiHeaders;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Adapter for the payment service. Pattern for every downstream client:
 * <ol>
 *   <li>extend {@link AbstractDownstreamClient} (timeouts, retry, error mapping, logging come for free)</li>
 *   <li>inject the service-specific {@link WebClient} by qualifier</li>
 *   <li>one public method per remote operation, returning downstream DTOs</li>
 * </ol>
 */
@Component
public class PaymentClient extends AbstractDownstreamClient {

    private static final String AUTHORIZE = "authorizePayment";

    private final WebClient webClient;

    public PaymentClient(@Qualifier("paymentWebClient") WebClient webClient, DownstreamProperties properties) {
        super(WebClientConfig.PAYMENT_SERVICE, properties.payment());
        this.webClient = webClient;
    }

    /**
     * POST is not idempotent by nature; sending the checkout id as {@code Idempotency-Key} lets the
     * payment service de-duplicate, which is what makes {@code retryable = true} safe here.
     */
    public PaymentAuthorizationResponse authorize(PaymentAuthorizationRequest request) {
        Mono<PaymentAuthorizationResponse> call = webClient.post()
                .uri("/v1/payments/authorizations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(ApiHeaders.IDEMPOTENCY_KEY, request.merchantReference())
                .bodyValue(request)
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> mapErrorResponse(AUTHORIZE, response))
                .bodyToMono(PaymentAuthorizationResponse.class);
        return execute(AUTHORIZE, call, true);
    }
}
