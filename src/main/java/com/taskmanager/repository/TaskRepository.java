package com.taskmanager.repository;

import com.taskmanager.entity.Status;
import com.taskmanager.entity.Task;
import org.springframework.data.jpa.repository.JpaRepository;

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
}
