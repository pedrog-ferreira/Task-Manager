package com.taskmanager.repository;

import com.taskmanager.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Data access for {@link User}. Basic CRUD via {@link JpaRepository}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /** User by email (UNIQUE column) — used on login and account creation. */
    Optional<User> findByEmail(String email);

    /** Checks existence without loading the entity (SELECT 1 ... LIMIT 1). */
    boolean existsByEmail(String email);
}
