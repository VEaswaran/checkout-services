package com.company.checkout.common.logging;

/**
 * Field names for structured (key/value) log events.
 *
 * <p>Names follow the Elastic Common Schema (ECS) where a standard field exists, so Kibana and
 * Dynatrace map them without custom parsing. Use them with the SLF4J fluent API:
 * <pre>{@code
 * log.atInfo()
 *    .addKeyValue(LogFields.EVENT_ACTION, ApiEventAction.REQUEST_RECEIVED.value())
 *    .addKeyValue(LogFields.HTTP_METHOD, "POST")
 *    .log("API request received");
 * }</pre>
 */
public final class LogFields {

    // ECS standard fields
    public static final String EVENT_ACTION = "event.action";
    public static final String EVENT_OUTCOME = "event.outcome";
    /** ECS defines event.duration in nanoseconds. */
    public static final String EVENT_DURATION_NS = "event.duration";
    public static final String HTTP_METHOD = "http.request.method";
    public static final String HTTP_STATUS = "http.response.status_code";
    public static final String URL_PATH = "url.path";
    public static final String URL_FULL = "url.full";
    public static final String CLIENT_ADDRESS = "client.address";
    public static final String ERROR_TYPE = "error.type";
    public static final String ERROR_CODE = "error.code";

    // Service-specific fields (namespaced to avoid ECS collisions)
    public static final String API_RECEIVED_AT = "api.received_at";
    public static final String API_COMPLETED_AT = "api.completed_at";
    public static final String API_DURATION_MS = "api.duration_ms";
    public static final String DOWNSTREAM_SERVICE = "downstream.service";
    public static final String DOWNSTREAM_OPERATION = "downstream.operation";
    public static final String DOWNSTREAM_STARTED_AT = "downstream.started_at";
    public static final String DOWNSTREAM_DURATION_MS = "downstream.duration_ms";
    public static final String CHECKOUT_ID = "checkout.id";
    public static final String CHECKOUT_STATUS = "checkout.status";
    public static final String CHECKOUT_TOTAL = "checkout.total";
    public static final String CUSTOMER_EMAIL_MASKED = "customer.email_masked";

    private LogFields() {
    }
}
