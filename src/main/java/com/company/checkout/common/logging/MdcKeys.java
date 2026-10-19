package com.company.checkout.common.logging;

/**
 * MDC keys. Every value placed here appears on every log line of the request
 * (plain-text pattern locally, a top-level JSON field in ECS/ELK).
 *
 * <p>{@code traceId}/{@code spanId} are written by Micrometer Tracing — never put them yourself.
 */
public final class MdcKeys {

    public static final String CORRELATION_ID = "correlationId";
    public static final String CLIENT_ID = "clientId";
    public static final String CHECKOUT_ID = "checkoutId";

    /** Populated by Micrometer Tracing (read-only for application code). */
    public static final String TRACE_ID = "traceId";
    /** Populated by Micrometer Tracing (read-only for application code). */
    public static final String SPAN_ID = "spanId";

    private MdcKeys() {
    }
}
