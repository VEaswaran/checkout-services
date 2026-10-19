package com.company.checkout.checkout.api.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * API request contract. Validation belongs on the DTO (syntactic: present, format, size);
 * business rules (e.g. currency consistency) belong in the domain.
 */
public record CreateCheckoutRequest(
        @NotBlank @Size(max = 64) String customerId,
        @NotBlank @Email @Size(max = 254) String customerEmail,
        @NotBlank @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO-4217 code, e.g. USD") String currency,
        @NotEmpty @Size(max = 100) List<@Valid LineItemRequest> items) {

    /** Hide PII if this record is ever logged or printed in a test failure. */
    @Override
    public String toString() {
        return "CreateCheckoutRequest[customerId=%s, currency=%s, items=%d]"
                .formatted(customerId, currency, items == null ? 0 : items.size());
    }
}
