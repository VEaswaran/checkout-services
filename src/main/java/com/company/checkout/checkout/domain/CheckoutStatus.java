package com.company.checkout.checkout.domain;

import java.util.EnumSet;
import java.util.Set;

/**
 * Checkout lifecycle as an explicit state machine. Allowed transitions live next to the states,
 * so an illegal move (e.g. CONFIRMED -> CANCELLED) is impossible to code by accident.
 *
 * <pre>
 * CREATED ──confirm ok──▶ CONFIRMED
 *    │  ╲──declined────▶ PAYMENT_FAILED ──retry ok──▶ CONFIRMED
 *    └──cancel──▶ CANCELLED ◀──cancel── PAYMENT_FAILED
 * </pre>
 */
public enum CheckoutStatus {

    CREATED,
    PAYMENT_FAILED,
    CONFIRMED,
    CANCELLED;

    public Set<CheckoutStatus> allowedNext() {
        return switch (this) {
            case CREATED, PAYMENT_FAILED -> EnumSet.of(CONFIRMED, PAYMENT_FAILED, CANCELLED);
            case CONFIRMED, CANCELLED -> EnumSet.noneOf(CheckoutStatus.class);
        };
    }

    public boolean canTransitionTo(CheckoutStatus next) {
        return allowedNext().contains(next);
    }

    public boolean isTerminal() {
        return allowedNext().isEmpty();
    }
}
