package com.company.checkout.common.exception;

import java.util.Map;

/**
 * Root of the service's exception hierarchy. Always unchecked, always carries an {@link ErrorCode}.
 *
 * <ul>
 *   <li>{@code getMessage()} - detail for engineers; goes to logs only.</li>
 *   <li>{@code clientDetail} - optional, safe-to-expose detail returned in the ProblemDetail body.</li>
 *   <li>{@code logContext} - extra key/values attached to the error log line (ids, statuses).
 *       Never put secrets or PII here.</li>
 * </ul>
 */
public abstract class ApplicationException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String clientDetail;
    private final Map<String, Object> logContext;

    protected ApplicationException(ErrorCode errorCode, String message, String clientDetail,
                                   Map<String, Object> logContext, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.clientDetail = clientDetail;
        this.logContext = logContext == null ? Map.of() : Map.copyOf(logContext);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String clientDetail() {
        return clientDetail;
    }

    public Map<String, Object> logContext() {
        return logContext;
    }
}
