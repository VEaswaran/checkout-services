package com.company.checkout.checkout.api;

import java.util.Currency;
import java.util.List;
import java.util.Map;

import com.company.checkout.checkout.api.dto.CheckoutResponse;
import com.company.checkout.checkout.api.dto.LineItemRequest;
import com.company.checkout.checkout.domain.Checkout;
import com.company.checkout.checkout.domain.LineItem;
import com.company.checkout.checkout.domain.Money;
import com.company.checkout.common.exception.BusinessRuleException;
import com.company.checkout.common.exception.ErrorCode;

/**
 * DTO <-> domain mapping in one stateless place. Hand-written on purpose: explicit, debuggable,
 * zero magic. (MapStruct is a fine choice once mappings get large.)
 */
final class CheckoutApiMapper {

    private CheckoutApiMapper() {
    }

    static List<LineItem> toDomain(List<LineItemRequest> items) {
        return items.stream()
                .map(i -> new LineItem(i.sku(), i.name(), i.quantity(),
                        Money.of(i.unitPrice(), currency(i.currency()))))
                .toList();
    }

    static CheckoutResponse toResponse(Checkout checkout) {
        return new CheckoutResponse(
                checkout.id().value(),
                checkout.status().name(),
                checkout.customerId(),
                checkout.items().stream()
                        .map(i -> new CheckoutResponse.Item(i.sku(), i.name(), i.quantity(),
                                amount(i.unitPrice()), amount(i.lineTotal())))
                        .toList(),
                amount(checkout.total()),
                checkout.paymentReference().orElse(null),
                checkout.createdAt(),
                checkout.updatedAt());
    }

    /** Turns an unknown ISO code into a clean 400 instead of an IllegalArgumentException (500). */
    static Currency currency(String code) {
        try {
            return Currency.getInstance(code);
        } catch (IllegalArgumentException ex) {
            throw new BusinessRuleException(ErrorCode.UNSUPPORTED_CURRENCY, "Unknown currency " + code,
                    "Currency '" + code + "' is not supported", Map.of("currency", String.valueOf(code)));
        }
    }

    private static CheckoutResponse.Amount amount(Money money) {
        return new CheckoutResponse.Amount(money.amount().toPlainString(), money.currency().getCurrencyCode());
    }
}
