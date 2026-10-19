package com.company.checkout.checkout.api;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.Currency;
import java.util.List;

import com.company.checkout.checkout.domain.Checkout;
import com.company.checkout.checkout.domain.CheckoutId;
import com.company.checkout.checkout.domain.LineItem;
import com.company.checkout.checkout.domain.Money;
import com.company.checkout.checkout.service.CheckoutService;
import com.company.checkout.common.config.FilterConfig;
import com.company.checkout.common.exception.ErrorCode;
import com.company.checkout.common.exception.ResourceNotFoundException;
import com.company.checkout.common.web.ApiHeaders;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-slice test: controller + validation + GlobalExceptionHandler + filters, service mocked.
 * Verifies the HTTP contract (status codes, headers, error body shape) without starting a server.
 */
@WebMvcTest(CheckoutController.class)
@Import(FilterConfig.class)
class CheckoutControllerTest {

    private static final Currency USD = Currency.getInstance("USD");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CheckoutService checkoutService;

    @Test
    void createReturns201WithLocationAndEchoesCorrelationId() throws Exception {
        Checkout checkout = Checkout.create("cust-1", "john@example.com", USD,
                List.of(new LineItem("SKU-1", "Shirt", 2, Money.of(new BigDecimal("10.00"), USD))),
                Clock.systemUTC());
        given(checkoutService.createCheckout(anyString(), anyString(), any(), anyList())).willReturn(checkout);

        mockMvc.perform(post("/api/v1/checkouts")
                        .header(ApiHeaders.X_CORRELATION_ID, "test-corr-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerId": "cust-1",
                                  "customerEmail": "john@example.com",
                                  "currency": "USD",
                                  "items": [
                                    {"sku": "SKU-1", "name": "Shirt", "quantity": 2, "unitPrice": 10.00, "currency": "USD"}
                                  ]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", startsWith("http://localhost/api/v1/checkouts/chk_")))
                .andExpect(header().string(ApiHeaders.X_CORRELATION_ID, "test-corr-123"))
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.total.value").value("20.00"))
                .andExpect(jsonPath("$.total.currency").value("USD"));
    }

    @Test
    void invalidBodyReturnsProblemDetailWithAllFieldErrors() throws Exception {
        mockMvc.perform(post("/api/v1/checkouts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customerId": "", "customerEmail": "not-an-email", "currency": "usd", "items": []}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(header().exists(ApiHeaders.X_CORRELATION_ID)) // generated when caller sent none
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.code()))
                .andExpect(jsonPath("$.correlationId").exists())
                .andExpect(jsonPath("$.errors[*].field", hasItem("customerEmail")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("currency")))
                .andExpect(jsonPath("$.errors[*].field", hasItem("items")));
    }

    @Test
    void unknownCheckoutReturns404ProblemDetail() throws Exception {
        String id = "chk_" + "a".repeat(32);
        given(checkoutService.getCheckout(CheckoutId.of(id)))
                .willThrow(new ResourceNotFoundException(ErrorCode.CHECKOUT_NOT_FOUND, "Checkout", id));

        mockMvc.perform(get("/api/v1/checkouts/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("CHK-404-001"))
                .andExpect(jsonPath("$.instance").value("/api/v1/checkouts/" + id))
                .andExpect(jsonPath("$.type").value("urn:problem-type:checkout:chk-404-001"));
    }

    @Test
    void malformedCheckoutIdIsRejectedBeforeReachingTheService() throws Exception {
        mockMvc.perform(get("/api/v1/checkouts/{id}", "DROP TABLE"))
                .andExpect(status().isBadRequest());
    }
}
