package com.taskmanager.repository;

import com.taskmanager.entity.Status;
import com.taskmanager.entity.Task;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

/**
 * Data access for {@link Task}. Only name-derived query methods here — no
 * business rules (those live in the service). Basic CRUD (save, findById,
 * findAll, deleteById, count, ...) comes from {@link JpaRepository}.
 */
public interface TaskRepository extends JpaRepository<Task, Long> {

    /** Tasks of a project. Resolves to {@code task.project.id}. */
    List<Task> findByProjectId(Long projectId);

    /** Tasks in a given status, across all projects. */
    List<Task> findByStatus(Status status);

    /** Tasks of a project filtered by status. */
    List<Task> findByProjectIdAndStatus(Long projectId, Status status);

    /** How many tasks are in a given status (SQL COUNT, does not load rows). */
    long countByStatus(Status status);

    /** How many tasks of a project are in a given status (SQL COUNT). */
    long countByProjectIdAndStatus(Long projectId, Status status);

    /**
     * Tasks in {@code status} whose due date is before {@code date} — the query
     * behind an "overdue tasks" view (call it with {@code Status.DONE} excluded
     * and {@code LocalDate.now()}).
     */
    List<Task> findByStatusAndDueDateBefore(Status status, LocalDate date);

    /**
     * Same rows as {@link #findByProjectId(Long)}, but the {@code project}
     * association is fetched in the same query via {@code @EntityGraph}.
     * <p>
     * Without it, mapping N tasks to {@code TaskDTO} (which reads
     * {@code task.getProject().getName()}) fires 1 query for the list + N for the
     * projects — the classic N+1. The service uses this method for the list
     * endpoint; {@link #findByProjectId(Long)} stays as the un-eager baseline so
     * the difference can be seen with {@code show-sql} on.
     */
    @EntityGraph(attributePaths = "project")
    List<Task> findAllByProjectId(Long projectId);
}
