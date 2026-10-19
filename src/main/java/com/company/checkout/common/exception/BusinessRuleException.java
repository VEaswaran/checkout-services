package com.company.checkout.common.exception;

import java.util.Map;

/**
 * A domain invariant or business rule was violated (409/422). Thrown from the domain model and
 * services; the message is written for engineers, {@code clientDetail} for API consumers.
 */
public class BusinessRuleException extends ApplicationException {

    public BusinessRuleException(ErrorCode errorCode, String message) {
        this(errorCode, message, message, Map.of());
    }

    public BusinessRuleException(ErrorCode errorCode, String message, String clientDetail,
                                 Map<String, Object> logContext) {
        super(errorCode, message, clientDetail, logContext, null);
    }
}
