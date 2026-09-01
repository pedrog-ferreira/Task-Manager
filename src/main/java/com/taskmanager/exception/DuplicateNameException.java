package com.taskmanager.exception;

/**
 * Thrown when creating/renaming a project to a name the same owner already uses.
 * Mapped to HTTP 409 (Conflict) by the {@code GlobalExceptionHandler}.
 * <p>
 * This is the piece that gives {@code ProjectRepository.existsByNameAndUserId}
 * a purpose. It goes beyond the original sketch, but a declared repository
 * method that nothing calls is a smell.
 */
public class DuplicateNameException extends RuntimeException {

    public DuplicateNameException(String message) {
        super(message);
    }
}
