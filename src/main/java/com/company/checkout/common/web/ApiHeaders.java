package com.company.checkout.common.web;

/**
 * HTTP header names used across the service. Keep them in one place so inbound filters,
 * outbound WebClient filters and API docs never drift apart.
 */
public final class ApiHeaders {

    /** Business correlation id; accepted from the caller or generated, echoed on every response and sent downstream. */
    public static final String X_CORRELATION_ID = "X-Correlation-Id";

    /** Identifies the calling application (web, mobile, partner). Logged, never trusted for authorization. */
    public static final String X_CLIENT_ID = "X-Client-Id";

    /** Distributed trace id (W3C) echoed back so support teams can jump straight to the trace in Dynatrace/ELK. */
    public static final String X_TRACE_ID = "X-Trace-Id";

    /** Makes retried non-idempotent calls (POST) safe on the downstream side. */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private ApiHeaders() {
    }
}
