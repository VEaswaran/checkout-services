package com.company.checkout.common.logging;

/**
 * Catalogue of the technical events the service emits. Querying {@code event.action} in
 * Kibana / Dynatrace gives you the exact timestamp of each hop of a request:
 *
 * <pre>
 * api.request.received      -> request entered the service
 * downstream.call.started   -> outbound call left the service
 * downstream.call.completed -> outbound call returned (with status + duration)
 * downstream.call.failed    -> outbound call errored (timeout, connection refused, ...)
 * api.response.sent         -> response left the service (with status + duration)
 * </pre>
 */
public enum ApiEventAction {

    REQUEST_RECEIVED("api.request.received"),
    RESPONSE_SENT("api.response.sent"),
    DOWNSTREAM_STARTED("downstream.call.started"),
    DOWNSTREAM_COMPLETED("downstream.call.completed"),
    DOWNSTREAM_FAILED("downstream.call.failed"),
    DOWNSTREAM_CANCELLED("downstream.call.cancelled");

    private final String value;

    ApiEventAction(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
