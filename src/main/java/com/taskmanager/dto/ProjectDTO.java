package com.taskmanager.dto;

import java.time.LocalDateTime;

/**
 * Output DTO for a project — what the API returns.
 * <p>
 * Deliberately does <b>not</b> expose the owner ({@code user}) nor the task
 * list. Instead it exposes {@code taskCount}. Two reasons:
 * <ul>
 *   <li><b>No {@code LazyInitializationException}:</b> the entity's
 *       {@code tasks}/{@code user} are {@code LAZY}. If we returned the entity
 *       and Jackson tried to serialize them <i>after</i> the transaction closed,
 *       it would blow up. The DTO is built inside the transaction, so the count
 *       is resolved while the session is still open.</li>
 *   <li><b>Don't ship the whole database:</b> a project could have thousands of
 *       tasks. The list view needs a number, not every row.</li>
 * </ul>
 */
public record ProjectDTO(
        Long id,
        String name,
        String description,
        int taskCount,
        LocalDateTime createdAt
) {
}
