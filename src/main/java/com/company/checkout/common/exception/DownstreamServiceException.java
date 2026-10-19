package com.company.checkout.common.exception;

import java.util.HashMap;
import java.util.Map;

/**
 * A call to another service failed. Carries enough context to debug from a single log line
 * (service, operation, downstream status) while exposing nothing internal to API clients.
 */
public class DownstreamServiceException extends ApplicationException {

    private final String serviceName;
    private final String operation;
    private final Integer downstreamStatus;
    private final boolean retryable;

    public DownstreamServiceException(ErrorCode errorCode, String serviceName, String operation,
                                      Integer downstreamStatus, boolean retryable, String message,
                                      Throwable cause) {
        super(errorCode,
                "[%s.%s] %s".formatted(serviceName, operation, message),
                null, // never leak downstream details to our clients
                logContext(serviceName, operation, downstreamStatus),
                cause);
        this.serviceName = serviceName;
        this.operation = operation;
        this.downstreamStatus = downstreamStatus;
        this.retryable = retryable;
    }

    private static Map<String, Object> logContext(String serviceName, String operation, Integer status) {
        Map<String, Object> context = new HashMap<>();
        context.put("downstream.service", serviceName);
        context.put("downstream.operation", operation);
        if (status != null) {
            context.put("downstream.status_code", status);
        }
        return context;
    }

    public String serviceName() {
        return serviceName;
    }

    public String operation() {
        return operation;
    }

    public Integer downstreamStatus() {
        return downstreamStatus;
    }

    /** True for failures worth retrying (5xx gateway errors, timeouts, connection errors). */
    public boolean retryable() {
        return retryable;
    }
}
