package com.company.checkout.checkout.client.dto;

import java.math.BigDecimal;

import com.company.checkout.common.util.MaskingUtil;

/**
 * Wire contract of the payment service (owned by them, mirrored here).
 * Keep downstream DTOs inside the client package — they must never leak into the domain or our API.
 */
public record PaymentAuthorizationRequest(
        String merchantReference,
        BigDecimal amount,
        String currency,
        String paymentToken) {

    /** Records print every field by default — mask secrets so an accidental log line stays safe. */
    @Override
    public String toString() {
        return "PaymentAuthorizationRequest[merchantReference=%s, amount=%s, currency=%s, paymentToken=%s]"
                .formatted(merchantReference, amount, currency, MaskingUtil.maskToken(paymentToken));
    }
}
