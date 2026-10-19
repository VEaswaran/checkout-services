package com.company.checkout.checkout.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

import com.company.checkout.common.exception.BusinessRuleException;
import com.company.checkout.common.exception.ErrorCode;

/**
 * Immutable money value object.
 *
 * <ul>
 *   <li>Never use {@code double} for money — always {@link BigDecimal}.</li>
 *   <li>Scale is normalised to the currency's minor units (USD 2, JPY 0) with banker's rounding.</li>
 *   <li>Arithmetic across currencies is a programming error and is rejected.</li>
 * </ul>
 */
public record Money(BigDecimal amount, Currency currency) {

    public Money {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(currency, "currency");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("Money amount must not be negative: " + amount);
        }
        amount = amount.setScale(currency.getDefaultFractionDigits(), RoundingMode.HALF_EVEN);
    }

    public static Money of(BigDecimal amount, Currency currency) {
        return new Money(amount, currency);
    }

    public static Money zero(Currency currency) {
        return new Money(BigDecimal.ZERO, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    public Money times(int quantity) {
        if (quantity < 0) {
            throw new IllegalArgumentException("quantity must not be negative");
        }
        return new Money(amount.multiply(BigDecimal.valueOf(quantity)), currency);
    }

    public boolean sameCurrencyAs(Money other) {
        return currency.equals(other.currency);
    }

    private void requireSameCurrency(Money other) {
        if (!sameCurrencyAs(other)) {
            throw new BusinessRuleException(ErrorCode.CURRENCY_MISMATCH,
                    "Currency mismatch: " + currency + " vs " + other.currency);
        }
    }

    @Override
    public String toString() {
        return amount.toPlainString() + " " + currency.getCurrencyCode();
    }
}
