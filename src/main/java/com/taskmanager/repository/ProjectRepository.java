package com.taskmanager.repository;

import com.taskmanager.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Data access for {@link Project}. Basic CRUD via {@link JpaRepository}.
 */
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /** Projects of a user. Resolves to {@code project.user.id}. */
    List<Project> findByUserId(Long userId);

    /**
     * Whether the user already has a project with this name. Emitted as a
     * {@code SELECT ... EXISTS} — no entity is loaded. Used to reject duplicate
     * names on create/update.
     */
    boolean existsByNameAndUserId(String name, Long userId);
}
