package com.taskmanager.repository;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.Status;
import com.taskmanager.entity.User;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static com.taskmanager.support.TestEntities.project;
import static com.taskmanager.support.TestEntities.task;
import static com.taskmanager.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Measures the N+1 on {@code Project.tasks} instead of just describing it, and
 * pins the two errors that come up trying to page/fetch it wrong — so removing
 * the {@code @EntityGraph} (or "fixing" the pagination) makes a test fail
 * instead of just a bigger number in a log somewhere.
 * <p>
 * Numbers here are for a small, fast dataset (4 projects × 3 tasks); the
 * headline numbers in {@code notas-tecnicas.md} (Dia 3) come from running the
 * app for real against the Flyway-seeded 30 × 15 sample data
 * ({@code V3__seed_sample_data.sql}) and reading the Hibernate statistics log.
 * <p>
 * {@code @DataJpaTest} + {@code replace = NONE}: same pattern as
 * {@code TaskRepositoryTest} — real PostgreSQL, own dataset rebuilt in
 * {@code @BeforeEach} so the counts below are exact, rolled back after each
 * test. Prerequisite: {@code docker compose up -d}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectNPlusOneTest {

    private static final int PROJECT_COUNT = 4;
    private static final int TASKS_PER_PROJECT = 3;

    @Autowired
    private ProjectRepository repository;

    @Autowired
    private TestEntityManager em;

    @Autowired
    private EntityManagerFactory emf;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        // Shared DB with dev (see TaskRepositoryTest) — start from a known,
        // small state so the query counts below are exact, not "30-ish".
        em.getEntityManager().createQuery("delete from Task").executeUpdate();
        em.getEntityManager().createQuery("delete from Project").executeUpdate();
        em.getEntityManager().createQuery("delete from User").executeUpdate();

        User owner = em.persist(user("n-plus-one@example.com"));
        for (int p = 1; p <= PROJECT_COUNT; p++) {
            Project seededProject = em.persist(project("Project " + p, owner));
            for (int t = 1; t <= TASKS_PER_PROJECT; t++) {
                em.persist(task("Task " + p + "." + t, Status.PENDING, seededProject));
            }
        }

        // Flush the INSERTs, then clear the 1st-level cache: the measurements
        // below must hit the DB for real, not reuse instances already in memory.
        em.flush();
        em.clear();

        statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();
    }

    @Test
    void baselineIsOneQueryForTheListPlusOnePerProject() {
        List<Project> projects = repository.findAllLazy();
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);

        // This is exactly what ProjectService.toDTO does: touch the LAZY
        // collection while mapping to a DTO.
        projects.forEach(p -> p.getTasks().size());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1 + PROJECT_COUNT);
    }

    @Test
    void fix1_joinFetchIsOneQuery() {
        List<Project> projects = repository.findAllWithTasksJoinFetch();
        projects.forEach(p -> p.getTasks().size()); // already loaded, no extra SELECT

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(projects).hasSize(PROJECT_COUNT)
                .allSatisfy(p -> assertThat(p.getTasks()).hasSize(TASKS_PER_PROJECT));
    }

    @Test
    void fix2_entityGraphOverridingFindAllIsOneQuery() {
        List<Project> projects = repository.findAll(); // @EntityGraph override
        projects.forEach(p -> p.getTasks().size());

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        assertThat(projects).hasSize(PROJECT_COUNT)
                .allSatisfy(p -> assertThat(p.getTasks()).hasSize(TASKS_PER_PROJECT));
    }

    @Test
    void fix3_dtoProjectionIsOneQueryAndNeverMaterializesAnEntity() {
        List<ProjectRepository.ProjectTaskCountView> rows = repository.findAllTaskCounts();

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
        // Nothing here is a Project — no dirty checking, no lazy proxies to trip
        // over. Confirmed structurally, not just asserted: entityLoadCount stays 0.
        assertThat(statistics.getEntityLoadCount()).isZero();
        assertThat(rows).hasSize(PROJECT_COUNT)
                .allSatisfy(r -> assertThat(r.getTaskCount()).isEqualTo((long) TASKS_PER_PROJECT));
    }

    @Test
    void pagingAJoinFetchLogsHHH90003004() {
        // The warning is logged by org.hibernate.query.QueryLogging, under this
        // category — found by decompiling hibernate-core, not documented anywhere
        // obvious. This is the exact thing worth having hit once.
        Logger queryLogger = (Logger) LoggerFactory.getLogger("org.hibernate.orm.query");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        queryLogger.addAppender(appender);

        try {
            repository.findAllWithTasksJoinFetchPaged(PageRequest.of(0, 1));
        } finally {
            queryLogger.detachAppender(appender);
        }

        assertThat(appender.list)
                .as("Hibernate cannot apply LIMIT/OFFSET in SQL to a collection-fetch query")
                .anyMatch(event -> event.getFormattedMessage().contains("HHH90003004"));

        // Page needs a total, so Spring Data issues 2 statements: the content
        // query and a separate `count(distinct ...)`. The content query itself
        // has no LIMIT, though — it loads all PROJECT_COUNT projects (and all
        // their tasks) regardless of the page size requested, then slices the
        // list in memory. That's the actual cost of the warning.
        assertThat(statistics.getPrepareStatementCount()).isEqualTo(2);
        assertThat(statistics.getEntityLoadCount()).isEqualTo(PROJECT_COUNT + PROJECT_COUNT * TASKS_PER_PROJECT);
    }

    @Test
    void fetchJoiningTwoCollectionsAtOnceThrowsMultipleBagFetchException() {
        // tasks and tags are both Hibernate "bags" (List, no @OrderColumn).
        // Fetch-joining both at once cross-joins them in SQL — Hibernate
        // refuses rather than hand back a cross product silently.
        assertThatThrownBy(() -> repository.findAllFetchingTasksAndTags())
                .hasStackTraceContaining("MultipleBagFetchException");
    }
}
