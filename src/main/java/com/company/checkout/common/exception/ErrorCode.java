package com.company.checkout.common.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Single catalogue of every error this API can return.
 *
 * <p>Rules:
 * <ul>
 *   <li>Codes are a public contract — never reuse or renumber one; add a new entry instead.</li>
 *   <li>Format {@code CHK-<http status>-<seq>} makes the status obvious in dashboards.</li>
 *   <li>{@code title} is safe for clients; put details for engineers in the exception message / logs.</li>
 * </ul>
 */
public enum ErrorCode {

    // 4xx - caller problems
    VALIDATION_FAILED("CHK-400-001", HttpStatus.BAD_REQUEST, "Request validation failed"),
    MALFORMED_REQUEST("CHK-400-002", HttpStatus.BAD_REQUEST, "Malformed request"),
    UNSUPPORTED_CURRENCY("CHK-400-003", HttpStatus.BAD_REQUEST, "Unsupported currency"),
    CHECKOUT_NOT_FOUND("CHK-404-001", HttpStatus.NOT_FOUND, "Checkout not found"),
    INVALID_STATE_TRANSITION("CHK-409-001", HttpStatus.CONFLICT, "Checkout is not in a valid state for this operation"),
    CURRENCY_MISMATCH("CHK-422-001", HttpStatusCode.valueOf(422), "All line items must use the checkout currency"),
    PAYMENT_DECLINED("CHK-422-002", HttpStatusCode.valueOf(422), "Payment was declined"),

    // 5xx - our or downstream problems
    DOWNSTREAM_ERROR("CHK-502-001", HttpStatus.BAD_GATEWAY, "A dependent service returned an error"),
    DOWNSTREAM_UNAVAILABLE("CHK-503-001", HttpStatus.SERVICE_UNAVAILABLE, "A dependent service is unavailable"),
    DOWNSTREAM_TIMEOUT("CHK-504-001", HttpStatus.GATEWAY_TIMEOUT, "A dependent service did not respond in time"),
    INTERNAL_ERROR("CHK-500-001", HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

    private final String code;
    private final HttpStatusCode status;
    private final String title;

    ErrorCode(String code, HttpStatusCode status, String title) {
        this.code = code;
        this.status = status;
        this.title = title;
    }

    public String code() {
        return code;
    }

    public HttpStatusCode status() {
        return status;
    }

    public String title() {
        return title;
    }
}
