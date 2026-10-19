package com.company.checkout.checkout.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Currency;
import java.util.List;

import com.company.checkout.common.exception.BusinessRuleException;
import com.company.checkout.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

/** Pure unit tests: no Spring context, millisecond-fast. Most business tests should look like this. */
class CheckoutTest {

    private static final Currency USD = Currency.getInstance("USD");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-19T12:00:00Z"), ZoneOffset.UTC);

    @Test
    void computesTotalFromLineItems() {
        Checkout checkout = Checkout.create("cust-1", "a@b.com", USD, List.of(
                new LineItem("SKU-1", "Shirt", 2, Money.of(new BigDecimal("19.99"), USD)),
                new LineItem("SKU-2", "Socks", 1, Money.of(new BigDecimal("5.00"), USD))), CLOCK);

        assertThat(checkout.total()).isEqualTo(Money.of(new BigDecimal("44.98"), USD));
        assertThat(checkout.status()).isEqualTo(CheckoutStatus.CREATED);
        assertThat(checkout.createdAt()).isEqualTo(Instant.parse("2026-10-19T12:00:00Z"));
        assertThat(checkout.id().value()).startsWith("chk_");
    }

    @Test
    void rejectsLineItemsInAnotherCurrency() {
        List<LineItem> items = List.of(new LineItem("SKU-1", "Shirt", 1,
                Money.of(BigDecimal.TEN, Currency.getInstance("EUR"))));

        assertThatThrownBy(() -> Checkout.create("cust-1", "a@b.com", USD, items, CLOCK))
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.CURRENCY_MISMATCH);
    }

    @Test
    void confirmedCheckoutCannotBeConfirmedAgain() {
        Checkout checkout = Checkout.create("cust-1", "a@b.com", USD,
                List.of(new LineItem("SKU-1", "Shirt", 1, Money.of(BigDecimal.ONE, USD))), CLOCK);
        checkout.confirmPayment("pay_1", CLOCK);

        assertThat(checkout.status()).isEqualTo(CheckoutStatus.CONFIRMED);
        assertThat(checkout.paymentReference()).contains("pay_1");
        assertThatThrownBy(checkout::ensureCanBeConfirmed)
                .isInstanceOf(BusinessRuleException.class)
                .extracting(ex -> ((BusinessRuleException) ex).errorCode())
                .isEqualTo(ErrorCode.INVALID_STATE_TRANSITION);
    }

    @Test
    void failedPaymentCanBeRetried() {
        Checkout checkout = Checkout.create("cust-1", "a@b.com", USD,
                List.of(new LineItem("SKU-1", "Shirt", 1, Money.of(BigDecimal.ONE, USD))), CLOCK);
        checkout.markPaymentFailed(CLOCK);
        checkout.confirmPayment("pay_2", CLOCK);

        assertThat(checkout.status()).isEqualTo(CheckoutStatus.CONFIRMED);
    }

    @Test
    void moneyIsNormalisedToCurrencyScale() {
        assertThat(Money.of(new BigDecimal("10.005"), USD).amount()).isEqualByComparingTo("10.00"); // HALF_EVEN
        assertThat(Money.of(new BigDecimal("10"), Currency.getInstance("JPY")).amount().scale()).isZero();
    }
}
