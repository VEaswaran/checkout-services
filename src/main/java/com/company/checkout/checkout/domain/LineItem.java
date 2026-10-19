package com.company.checkout.checkout.domain;

import java.util.Objects;

/** One product line in a checkout. Immutable; validates its own invariants. */
public record LineItem(String sku, String name, int quantity, Money unitPrice) {

    public LineItem {
        Objects.requireNonNull(sku, "sku");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(unitPrice, "unitPrice");
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be positive, was " + quantity);
        }
    }

    public Money lineTotal() {
        return unitPrice.times(quantity);
    }
}
