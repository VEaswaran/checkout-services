package com.company.checkout.checkout.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import com.company.checkout.common.exception.BusinessRuleException;
import com.company.checkout.common.exception.ErrorCode;

/**
 * Checkout aggregate root.
 *
 * <p>Design rules shown here:
 * <ul>
 *   <li>No public setters — state changes only through intention-revealing methods
 *       ({@link #confirmPayment}, {@link #markPaymentFailed}, {@link #cancel}).</li>
 *   <li>Invariants are enforced inside the aggregate, not in the service or controller.</li>
 *   <li>No Spring, JSON or HTTP annotations: the domain does not know how it is exposed or stored.</li>
 *   <li>Time comes from an injected {@link Clock} — deterministic tests.</li>
 * </ul>
 */
public final class Checkout {

    private final CheckoutId id;
    private final String customerId;
    private final String customerEmail;
    private final Currency currency;
    private final List<LineItem> items;
    private final Money total;
    private final Instant createdAt;
    private CheckoutStatus status;
    private String paymentReference;
    private Instant updatedAt;

    private Checkout(CheckoutId id, String customerId, String customerEmail, Currency currency,
                     List<LineItem> items, Instant createdAt) {
        this.id = id;
        this.customerId = customerId;
        this.customerEmail = customerEmail;
        this.currency = currency;
        this.items = List.copyOf(items);
        this.total = items.stream().map(LineItem::lineTotal).reduce(Money.zero(currency), Money::plus);
        this.status = CheckoutStatus.CREATED;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /** Factory: the only way to create a valid checkout. */
    public static Checkout create(String customerId, String customerEmail, Currency currency,
                                  List<LineItem> items, Clock clock) {
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(currency, "currency");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("A checkout needs at least one line item");
        }
        items.stream()
                .filter(item -> !item.unitPrice().currency().equals(currency))
                .findFirst()
                .ifPresent(item -> {
                    throw new BusinessRuleException(ErrorCode.CURRENCY_MISMATCH,
                            "Line item " + item.sku() + " is priced in " + item.unitPrice().currency()
                                    + " but checkout currency is " + currency,
                            "Line item '" + item.sku() + "' must be priced in " + currency.getCurrencyCode(),
                            Map.of("sku", item.sku()));
                });
        return new Checkout(CheckoutId.newId(), customerId, customerEmail, currency, items, Instant.now(clock));
    }

    public void confirmPayment(String paymentReference, Clock clock) {
        Objects.requireNonNull(paymentReference, "paymentReference");
        transitionTo(CheckoutStatus.CONFIRMED, clock);
        this.paymentReference = paymentReference;
    }

    public void markPaymentFailed(Clock clock) {
        transitionTo(CheckoutStatus.PAYMENT_FAILED, clock);
    }

    public void cancel(Clock clock) {
        transitionTo(CheckoutStatus.CANCELLED, clock);
    }

    /** Fail fast before calling the payment service for a checkout that can't be confirmed. */
    public void ensureCanBeConfirmed() {
        if (!status.canTransitionTo(CheckoutStatus.CONFIRMED)) {
            throw invalidTransition(CheckoutStatus.CONFIRMED);
        }
    }

    private void transitionTo(CheckoutStatus next, Clock clock) {
        if (!status.canTransitionTo(next)) {
            throw invalidTransition(next);
        }
        this.status = next;
        this.updatedAt = Instant.now(clock);
    }

    private BusinessRuleException invalidTransition(CheckoutStatus next) {
        return new BusinessRuleException(ErrorCode.INVALID_STATE_TRANSITION,
                "Checkout " + id + " cannot move from " + status + " to " + next,
                "Checkout is " + status + " and cannot be " + next.name().toLowerCase(java.util.Locale.ROOT),
                Map.of("checkout.id", id.value(), "checkout.status", status.name()));
    }

    public CheckoutId id() {
        return id;
    }

    public String customerId() {
        return customerId;
    }

    public String customerEmail() {
        return customerEmail;
    }

    public Currency currency() {
        return currency;
    }

    public List<LineItem> items() {
        return items;
    }

    public Money total() {
        return total;
    }

    public CheckoutStatus status() {
        return status;
    }

    public Optional<String> paymentReference() {
        return Optional.ofNullable(paymentReference);
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
