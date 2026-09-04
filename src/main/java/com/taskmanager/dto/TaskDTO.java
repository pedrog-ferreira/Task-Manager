package com.taskmanager.dto;

import com.taskmanager.entity.Priority;
import com.taskmanager.entity.Status;

import java.time.LocalDate;

/**
 * Output DTO for a task — what the API returns.
 * <p>
 * {@code overdue} is <b>derived</b>, not a column: it is {@code true} when the
 * task has a due date in the past and is not {@code DONE}. DTOs can — and should —
 * expose computed data that saves the frontend the work.
 * <p>
 * The parent project is flattened to {@code projectId} + {@code projectName}
 * rather than a nested object, so the response stays small and there is no lazy
 * association for Jackson to trip over.
 */
public record TaskDTO(
        Long id,
        String title,
        String description,
        Status status,
        Priority priority,
        LocalDate dueDate,
        boolean overdue,
        Long projectId,
        String projectName
) {
}
