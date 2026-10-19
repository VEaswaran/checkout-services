package com.company.checkout.stub;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import com.company.checkout.checkout.client.dto.PaymentAuthorizationRequest;
import com.company.checkout.checkout.client.dto.PaymentAuthorizationResponse;
import com.company.checkout.common.web.ApiHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * LOCAL PROFILE ONLY. Fakes the payment service so the full flow (inbound API -> WebClient ->
 * "downstream") runs on a laptop, and you can watch the same traceId / correlationId appear on both
 * sides of the call in the logs.
 *
 * <ul>
 *   <li>{@code paymentToken = tok_decline}     -> 200 DECLINED (business failure, 422 to our caller)</li>
 *   <li>{@code paymentToken = tok_unavailable} -> 503 every time (shows retries + 503 mapping)</li>
 *   <li>amount &gt; 10000                       -> DECLINED (limit exceeded)</li>
 *   <li>anything else                          -> AUTHORIZED</li>
 * </ul>
 */
@Profile("local")
@RestController
@RequestMapping("/stub/payment-service/v1/payments")
class PaymentServiceStubController {

    private static final Logger log = LoggerFactory.getLogger(PaymentServiceStubController.class);
    private static final BigDecimal LIMIT = new BigDecimal("10000");

    @PostMapping("/authorizations")
    ResponseEntity<?> authorize(@RequestHeader(name = ApiHeaders.IDEMPOTENCY_KEY, required = false) String idempotencyKey,
                                @RequestBody PaymentAuthorizationRequest request) {
        log.info("[STUB payment-service] authorize {} idempotencyKey={}", request, idempotencyKey);

        if ("tok_unavailable".equals(request.paymentToken())) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", "maintenance"));
        }
        if ("tok_decline".equals(request.paymentToken())) {
            return ResponseEntity.ok(new PaymentAuthorizationResponse(null, "DECLINED", "insufficient_funds"));
        }
        if (request.amount().compareTo(LIMIT) > 0) {
            return ResponseEntity.ok(new PaymentAuthorizationResponse(null, "DECLINED", "limit_exceeded"));
        }
        return ResponseEntity.ok(new PaymentAuthorizationResponse("pay_" + UUID.randomUUID(), "AUTHORIZED", null));
    }
}
