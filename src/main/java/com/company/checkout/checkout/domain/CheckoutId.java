package com.company.checkout.checkout.domain;

import java.util.Objects;

import com.company.checkout.common.util.IdGenerator;

/**
 * Strongly-typed id. Prevents passing a customerId where a checkoutId is expected — a bug the
 * compiler catches for free once ids stop being plain Strings.
 */
public record CheckoutId(String value) {

    public CheckoutId {
        Objects.requireNonNull(value, "value");
        if (value.isBlank()) {
            throw new IllegalArgumentException("CheckoutId must not be blank");
        }
    }

    public static CheckoutId newId() {
        return new CheckoutId(IdGenerator.checkoutId());
    }

    public static CheckoutId of(String value) {
        return new CheckoutId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
