package com.taskmanager.repository;

import com.taskmanager.entity.Project;
import com.taskmanager.entity.Status;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static com.taskmanager.support.TestEntities.project;
import static com.taskmanager.support.TestEntities.task;
import static com.taskmanager.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data-layer slice test: {@code @DataJpaTest} boots only the EntityManager,
 * the repositories and a transaction — no web, no services. Each test runs in
 * a transaction that is rolled back at the end, so it leaves no data behind.
 * <p>
 * {@code replace = NONE} stops Spring from swapping the datasource for an
 * embedded H2: the tests use the local PostgreSQL from docker-compose (see
 * {@code src/test/resources/application.yml}). Flyway applies the migrations
 * and Hibernate validates the entities against that schema, as in production.
 * <p>
 * Prerequisite: {@code docker compose up -d}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class TaskRepositoryTest {

    @Autowired
    private TaskRepository repository;

    @Autowired
    private TestEntityManager em;

    private Project projectA;
    private Project projectB;

    @BeforeEach
    void setUp() {
        // The DB is shared with dev; start from a known state for the tests that
        // count/filter by status. All of this is undone by the rollback at the
        // end of the test, so it does not affect dev data.
        em.getEntityManager().createQuery("delete from Task").executeUpdate();
        em.getEntityManager().createQuery("delete from Project").executeUpdate();
        em.getEntityManager().createQuery("delete from User").executeUpdate();

        User user = em.persist(user("ana@example.com"));
        projectA = em.persist(project("Project A", user));
        projectB = em.persist(project("Project B", user));

        em.persist(task("Write tests", Status.IN_PROGRESS, projectA));
        em.persist(task("Review PR", Status.IN_PROGRESS, projectA));
        em.persist(task("Deploy", Status.DONE, projectA));
        em.persist(task("Backlog item", Status.PENDING, projectB));

        // Flush the INSERTs and clear the first-level cache: the queries below
        // hit the DB for real, they do not return instances already in memory.
        em.flush();
        em.clear();
    }

    @Test
    void findsTasksByStatus() {
        List<Task> inProgress = repository.findByStatus(Status.IN_PROGRESS);

        assertThat(inProgress)
                .hasSize(2)
                .extracting(Task::getTitle)
                .containsExactlyInAnyOrder("Write tests", "Review PR");
    }

    @Test
    void filtersTasksByProject() {
        assertThat(repository.findByProjectId(projectA.getId())).hasSize(3);
        assertThat(repository.findByProjectId(projectB.getId())).hasSize(1);
    }

    @Test
    void filtersByProjectAndStatusTogether() {
        List<Task> doneInProjectA =
                repository.findByProjectIdAndStatus(projectA.getId(), Status.DONE);

        assertThat(doneInProjectA)
                .singleElement()
                .extracting(Task::getTitle)
                .isEqualTo("Deploy");
    }

    @Test
    void countsTasksByStatus() {
        assertThat(repository.countByStatus(Status.IN_PROGRESS)).isEqualTo(2);
        assertThat(repository.countByStatus(Status.PENDING)).isEqualTo(1);
        assertThat(repository.countByStatus(Status.DONE)).isEqualTo(1);
    }
}
