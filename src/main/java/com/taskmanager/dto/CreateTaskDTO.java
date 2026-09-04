package com.taskmanager.dto;

import com.taskmanager.entity.Priority;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Input DTO for creating a task.
 * <p>
 * Two decisions worth noting:
 * <ul>
 *   <li>{@code projectId} is a {@code Long}, not a {@code Project}. The client
 *       sends an id; the service resolves the association. The API never accepts
 *       a whole entity graph.</li>
 *   <li>There is no {@code status} field. A new task is always born
 *       {@code PENDING} — that is a business rule the server imposes, not
 *       something the client gets to choose.</li>
 * </ul>
 * The {@code jakarta.validation} annotations only run when the controller
 * parameter is annotated with {@code @Valid}; a violation becomes a
 * {@link org.springframework.web.bind.MethodArgumentNotValidException}, which the
 * {@code GlobalExceptionHandler} turns into a 400.
 */
public record CreateTaskDTO(

        @NotBlank(message = "Title is required")
        @Size(max = 150, message = "Title must be at most 150 characters")
        String title,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,

        @NotNull(message = "Priority is required")
        Priority priority,

        @FutureOrPresent(message = "Due date cannot be in the past")
        LocalDate dueDate,

        @NotNull(message = "Project is required")
        Long projectId
) {
}
