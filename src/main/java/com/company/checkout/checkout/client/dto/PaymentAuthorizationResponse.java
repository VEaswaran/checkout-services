package com.company.checkout.checkout.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payment service response. {@code ignoreUnknown} keeps us working when they add fields
 * (tolerant reader) — a must for independently deployed services.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentAuthorizationResponse(String paymentId, String status, String declineReason) {

    public static final String AUTHORIZED = "AUTHORIZED";

    public boolean authorized() {
        return AUTHORIZED.equalsIgnoreCase(status);
    }
}
