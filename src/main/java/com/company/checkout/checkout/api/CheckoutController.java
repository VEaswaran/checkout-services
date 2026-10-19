package com.company.checkout.checkout.api;

import java.net.URI;

import com.company.checkout.checkout.api.dto.CheckoutResponse;
import com.company.checkout.checkout.api.dto.ConfirmCheckoutRequest;
import com.company.checkout.checkout.api.dto.CreateCheckoutRequest;
import com.company.checkout.checkout.domain.Checkout;
import com.company.checkout.checkout.domain.CheckoutId;
import com.company.checkout.checkout.service.CheckoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * Thin HTTP adapter: validate -> map to domain -> call service -> map to response.
 * No business logic, no try/catch (the GlobalExceptionHandler owns error responses), no logging of
 * request/response (ApiEventLoggingFilter owns that).
 *
 * <p>Versioned in the path ({@code /api/v1}) so breaking changes can ship as v2 side by side.
 */
@RestController
@RequestMapping(path = "/api/v1/checkouts", produces = MediaType.APPLICATION_JSON_VALUE)
public class CheckoutController {

    private static final String CHECKOUT_ID_REGEX = "^chk_[a-f0-9]{32}$";

    private final CheckoutService checkoutService;

    public CheckoutController(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<CheckoutResponse> create(@Valid @RequestBody CreateCheckoutRequest request) {
        Checkout checkout = checkoutService.createCheckout(
                request.customerId(),
                request.customerEmail(),
                CheckoutApiMapper.currency(request.currency()),
                CheckoutApiMapper.toDomain(request.items()));

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(checkout.id().value())
                .toUri();
        return ResponseEntity.created(location).body(CheckoutApiMapper.toResponse(checkout));
    }

    @GetMapping("/{checkoutId}")
    public CheckoutResponse get(@PathVariable @Pattern(regexp = CHECKOUT_ID_REGEX) String checkoutId) {
        return CheckoutApiMapper.toResponse(checkoutService.getCheckout(CheckoutId.of(checkoutId)));
    }

    @PostMapping(path = "/{checkoutId}/confirm", consumes = MediaType.APPLICATION_JSON_VALUE)
    public CheckoutResponse confirm(@PathVariable @Pattern(regexp = CHECKOUT_ID_REGEX) String checkoutId,
                                    @Valid @RequestBody ConfirmCheckoutRequest request) {
        Checkout checkout = checkoutService.confirmCheckout(CheckoutId.of(checkoutId), request.paymentToken());
        return CheckoutApiMapper.toResponse(checkout);
    }
}
