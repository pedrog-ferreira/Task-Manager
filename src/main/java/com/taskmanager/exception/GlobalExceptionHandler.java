package com.taskmanager.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * One place that turns exceptions thrown anywhere in the controllers/services
 * into consistent HTTP responses.
 * <p>
 * {@code @RestControllerAdvice} = {@code @ControllerAdvice} + {@code @ResponseBody}:
 * it registers these handlers for <i>every</i> controller, and the return values
 * are serialized to JSON (not treated as view names).
 * <p>
 * Without this class: a {@link ResourceNotFoundException} would surface as a
 * generic 500, and a validation error as Spring's verbose default body.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 404 — the service looked something up by id and it wasn't there.
     * Body: {@code {"status": 404, "message": "..."}}.
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("status", 404, "message", ex.getMessage()));
    }

    /**
     * 409 — unique-ish business rule violated (here: duplicate project name for
     * the same owner).
     */
    @ExceptionHandler(DuplicateNameException.class)
    public ResponseEntity<Map<String, Object>> conflict(DuplicateNameException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "message", ex.getMessage()));
    }

    /**
     * 409 — a state-dependent domain rule was broken (e.g. reopening a completed
     * task). The request was valid; the current state doesn't allow it.
     */
    @ExceptionHandler(BusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> businessRule(BusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("status", 409, "message", ex.getMessage()));
    }

    /**
     * 400 — the request body couldn't be parsed into the target type. The common
     * case: an enum value that doesn't exist (e.g. {@code "priority": "URGENT"}).
     * Jackson fails before the controller runs; without this handler Spring would
     * return a 500, but the fault is the client's, so 400 is correct.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> notReadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(Map.of("status", 400, "message", "Malformed or unreadable request body"));
    }

    /**
     * 400 — {@code @Valid} on a {@code @RequestBody} failed. Spring collects all
     * field errors into the exception's {@code BindingResult}; we flatten them
     * into a {@code {field: message}} map so the client knows exactly what to fix.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(errors);
    }
}
