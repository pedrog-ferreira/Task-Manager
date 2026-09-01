package com.taskmanager.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Input DTO for creating and updating a project (the same shape works for both:
 * PUT replaces the mutable fields).
 * <p>
 * A {@code record} gives us an immutable carrier with a canonical constructor,
 * accessors ({@code name()}, {@code description()}), {@code equals}/{@code hashCode}
 * and {@code toString} for free — exactly what a DTO needs.
 * <p>
 * The {@code jakarta.validation} annotations are checked by Spring when the
 * controller parameter is annotated with {@code @Valid}; a violation becomes a
 * {@link org.springframework.web.bind.MethodArgumentNotValidException}, which the
 * {@code GlobalExceptionHandler} turns into a 400 response.
 */
public record CreateProjectDTO(

        @NotBlank(message = "Name is required")
        @Size(max = 100, message = "Name must be at most 100 characters")
        String name,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description
) {
}
