package com.onemount.archetype.common.error;

/**
 * Thrown by services when a requested entity does not exist. Mapped to HTTP 404 by
 * {@link GlobalExceptionHandler}.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException("%s with id %s not found".formatted(resource, id));
    }
}
