package com.company.checkout.checkout.service;

import java.time.Clock;
import java.util.Currency;
import java.util.List;
import java.util.Map;

import com.company.checkout.checkout.client.PaymentClient;
import com.company.checkout.checkout.client.dto.PaymentAuthorizationRequest;
import com.company.checkout.checkout.client.dto.PaymentAuthorizationResponse;
import com.company.checkout.checkout.domain.Checkout;
import com.company.checkout.checkout.domain.CheckoutId;
import com.company.checkout.checkout.domain.LineItem;
import com.company.checkout.checkout.repository.CheckoutRepository;
import com.company.checkout.common.exception.BusinessRuleException;
import com.company.checkout.common.exception.ErrorCode;
import com.company.checkout.common.exception.ResourceNotFoundException;
import com.company.checkout.common.logging.LogFields;
import com.company.checkout.common.logging.MdcKeys;
import com.company.checkout.common.util.MaskingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

/**
 * Use-case orchestration: load aggregate -> call collaborators -> let the aggregate change state ->
 * persist. Business rules live in {@link Checkout}; this class coordinates and logs business events.
 *
 * <p>Logging guidelines demonstrated:
 * <ul>
 *   <li>INFO for business milestones (created, confirmed, declined) — one line each, structured.</li>
 *   <li>DEBUG for diagnostics; never log inside tight loops at INFO.</li>
 *   <li>Don't log-and-rethrow: exceptions are logged once by the GlobalExceptionHandler.</li>
 *   <li>Mask PII ({@link MaskingUtil}); never log payment tokens.</li>
 *   <li>Put the business id in MDC ({@code checkoutId}) so downstream-call logs carry it too.</li>
 * </ul>
 */
@Service
public class CheckoutService {

    private static final Logger log = LoggerFactory.getLogger(CheckoutService.class);

    private final CheckoutRepository repository;
    private final PaymentClient paymentClient;
    private final Clock clock;

    public CheckoutService(CheckoutRepository repository, PaymentClient paymentClient, Clock clock) {
        this.repository = repository;
        this.paymentClient = paymentClient;
        this.clock = clock;
    }

    @SuppressWarnings("try")
    public Checkout createCheckout(String customerId, String customerEmail, Currency currency, List<LineItem> items) {
        Checkout checkout = repository.save(Checkout.create(customerId, customerEmail, currency, items, clock));

        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.CHECKOUT_ID, checkout.id().value())) {
            log.atInfo()
                    .addKeyValue(LogFields.CHECKOUT_ID, checkout.id().value())
                    .addKeyValue(LogFields.CHECKOUT_STATUS, checkout.status().name())
                    .addKeyValue(LogFields.CHECKOUT_TOTAL, checkout.total().toString())
                    .addKeyValue(LogFields.CUSTOMER_EMAIL_MASKED, MaskingUtil.maskEmail(customerEmail))
                    .log("Checkout created with {} item(s), total {}", checkout.items().size(), checkout.total());
        }
        return checkout;
    }

    public Checkout getCheckout(CheckoutId id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ErrorCode.CHECKOUT_NOT_FOUND, "Checkout", id.value()));
    }

    @SuppressWarnings("try")
    public Checkout confirmCheckout(CheckoutId id, String paymentToken) {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(MdcKeys.CHECKOUT_ID, id.value())) {
            Checkout checkout = getCheckout(id);
            checkout.ensureCanBeConfirmed(); // fail fast — don't charge for an unconfirmable checkout

            log.debug("Requesting payment authorization for {}", checkout.total());
            PaymentAuthorizationResponse payment = paymentClient.authorize(new PaymentAuthorizationRequest(
                    checkout.id().value(),
                    checkout.total().amount(),
                    checkout.currency().getCurrencyCode(),
                    paymentToken));

            if (!payment.authorized()) {
                checkout.markPaymentFailed(clock);
                repository.save(checkout);
                log.atInfo()
                        .addKeyValue(LogFields.CHECKOUT_ID, id.value())
                        .addKeyValue(LogFields.CHECKOUT_STATUS, checkout.status().name())
                        .addKeyValue("payment.decline_reason", payment.declineReason())
                        .log("Payment declined");
                throw new BusinessRuleException(ErrorCode.PAYMENT_DECLINED,
                        "Payment declined for " + id + ": " + payment.declineReason(),
                        "The payment was declined. Please use a different payment method.",
                        Map.of("checkout.id", id.value()));
            }

            checkout.confirmPayment(payment.paymentId(), clock);
            repository.save(checkout);
            log.atInfo()
                    .addKeyValue(LogFields.CHECKOUT_ID, id.value())
                    .addKeyValue(LogFields.CHECKOUT_STATUS, checkout.status().name())
                    .addKeyValue("payment.id", payment.paymentId())
                    .log("Checkout confirmed");
            return checkout;
        }
    }
}
