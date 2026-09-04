package com.taskmanager.exception;

/**
 * Thrown by the service layer when a request is well-formed but breaks a
 * domain rule the annotations can't express (e.g. "a completed task cannot be
 * reopened"). Mapped to HTTP 409 (Conflict) by the {@code GlobalExceptionHandler}.
 * <p>
 * Bean-validation ({@code @NotBlank}, {@code @Size}, ...) checks <i>shape</i>;
 * this is for state-dependent rules that only the service can evaluate.
 */
public class BusinessRuleException extends RuntimeException {

    public BusinessRuleException(String message) {
        super(message);
    }
}
