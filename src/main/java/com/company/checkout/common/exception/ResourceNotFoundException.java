package com.company.checkout.common.exception;

import java.util.Map;

/** A requested resource does not exist (maps to 404). */
public class ResourceNotFoundException extends ApplicationException {

    public ResourceNotFoundException(ErrorCode errorCode, String resourceType, String resourceId) {
        super(errorCode,
                resourceType + " not found: " + resourceId,
                resourceType + " '" + resourceId + "' was not found",
                Map.of("resource.type", resourceType, "resource.id", resourceId),
                null);
    }
}
