package com.company.checkout.checkout.api.dto;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * API response contract — deliberately separate from the domain object so internal refactors
 * never break consumers. Money is serialised as a string to avoid floating-point surprises in
 * JavaScript clients.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckoutResponse(
        String checkoutId,
        String status,
        String customerId,
        List<Item> items,
        Amount total,
        String paymentReference,
        Instant createdAt,
        Instant updatedAt) {

    public record Item(String sku, String name, int quantity, Amount unitPrice, Amount lineTotal) {
    }

    public record Amount(String value, String currency) {
    }
}
