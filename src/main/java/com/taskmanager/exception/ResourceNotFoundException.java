package com.taskmanager.exception;

/**
 * Thrown by the service layer when a lookup by id finds nothing.
 * <p>
 * Extends {@link RuntimeException} (unchecked) on purpose: it does not need to be
 * declared or caught along the call chain — it bubbles straight up to the
 * {@code GlobalExceptionHandler}, which maps it to HTTP 404.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
