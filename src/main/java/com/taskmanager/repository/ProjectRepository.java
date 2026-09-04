package com.taskmanager.repository;

import com.taskmanager.entity.Project;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

/**
 * Data access for {@link Project}. Basic CRUD via {@link JpaRepository}.
 * <p>
 * The methods below {@code existsByNameAndUserId} are not used by
 * {@code ProjectService} — they exist to compare the three standard fixes for
 * the N+1 on {@code Project.tasks}, plus two methods that provoke errors on
 * purpose. See {@code notas-tecnicas.md}, Dia 3, for the measured query counts.
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

    // --- N+1: the baseline (do nothing) --------------------------------------

    /**
     * Plain {@code select p from Project p} — {@code tasks} stays LAZY. Kept
     * under its own name only because {@link #findAll()} below is overridden
     * with an {@code @EntityGraph}; this method reproduces the original N+1
     * baseline for comparison (1 query here + 1 per project when
     * {@code ProjectService.toDTO} touches {@code p.getTasks()}).
     */
    @Query("select p from Project p")
    List<Project> findAllLazy();

    // --- Fix 1: JOIN FETCH ----------------------------------------------------

    /**
     * Same rows as {@link #findAll()}, but {@code tasks} comes back in the same
     * query via an explicit {@code JOIN FETCH}. {@code distinct} matters here:
     * a fetch join is a SQL join, so without it a project with 15 tasks comes
     * back as 15 duplicate {@code Project} rows before Hibernate dedupes them
     * in memory (it does — Hibernate deduplicates fetch-joined roots — but
     * skipping {@code distinct} still returns duplicates for a plain JPQL
     * {@code select p}; keeping it here is intentional and cheap for this
     * data size).
     */
    @Query("select distinct p from Project p left join fetch p.tasks")
    List<Project> findAllWithTasksJoinFetch();

    /**
     * The same query as {@link #findAllWithTasksJoinFetch()}, but paged — kept
     * separate on purpose, to provoke {@code HHH90003004} rather than fix it.
     * <p>
     * Hibernate cannot apply {@code LIMIT}/{@code OFFSET} in SQL when the query
     * fetch-joins a collection: the join multiplies rows (1 per task), so a
     * SQL-level {@code LIMIT} would cut a project's tasks in half instead of
     * limiting projects. Hibernate's workaround is to load <b>every</b> row and
     * paginate in memory — logging {@code HHH90003004: firstResult/maxResults
     * specified with collection fetch; applying in memory}. For a real dataset
     * this defeats the point of pagination (you still pay for the full table).
     * Left broken on purpose — see notas-tecnicas.md, Dia 3.
     */
    @Query("select distinct p from Project p left join fetch p.tasks")
    Page<Project> findAllWithTasksJoinFetchPaged(Pageable pageable);

    // --- Fix 2: @EntityGraph ---------------------------------------------------

    /**
     * Overrides {@link JpaRepository#findAll()} to eagerly load {@code tasks}
     * via an {@code @EntityGraph} instead of JPQL. Same SQL as
     * {@link #findAllWithTasksJoinFetch()} (Hibernate turns the graph into the
     * same outer join), but declarative: no query string to write, and the
     * graph is reusable on other derived/query methods just by adding the
     * annotation.
     */
    @Override
    @EntityGraph(attributePaths = "tasks")
    List<Project> findAll();

    // --- Fix 3: DTO projection ---------------------------------------------

    /**
     * One row per project — id, name, and a task {@code COUNT}, computed by the
     * database via {@code GROUP BY}. No {@code Project} or {@code Task} entity
     * is ever materialized: this is a closed interface projection, backed by a
     * single scalar query. Fastest of the three fixes, but the result isn't
     * managed — you can't mutate it and rely on dirty checking to persist a
     * change.
     */
    @Query("""
            select p.id as id, p.name as name, count(t) as taskCount
            from Project p left join p.tasks t
            group by p.id, p.name
            """)
    List<ProjectTaskCountView> findAllTaskCounts();

    /** Backing projection for {@link #findAllTaskCounts()}. */
    interface ProjectTaskCountView {
        Long getId();
        String getName();
        Long getTaskCount();
    }

    // --- Provoke MultipleBagFetchException -----------------------------------

    /**
     * Fetch-joins {@code tasks} <b>and</b> {@code tags} in the same query — two
     * collections without {@code @OrderColumn} are both "bags" to Hibernate,
     * and fetching two bags of the same root at once is ambiguous: the SQL
     * cross-joins them (15 tasks × 2 tags = 30 rows per project), so Hibernate
     * cannot tell which joined row belongs to which bag element. It refuses at
     * query-plan time with {@code MultipleBagFetchException} rather than
     * silently returning a cross product.
     * <p>
     * No fix attempted here on purpose — this is the bridge to Dia 4
     * ({@code @BatchSize} / separate queries per collection). See
     * notas-tecnicas.md.
     */
    @Query("select distinct p from Project p left join fetch p.tasks left join fetch p.tags")
    List<Project> findAllFetchingTasksAndTags();
}
