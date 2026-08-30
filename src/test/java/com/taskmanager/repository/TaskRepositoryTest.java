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

import java.time.LocalDate;
import java.util.List;

import static com.taskmanager.support.TestEntities.newInstance;
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

    private Project project;
    private Project otherProject;

    @BeforeEach
    void setUp() {
        // The DB is shared with dev; start from a known state for the tests that
        // count/filter by status. All of this is undone by the rollback at the
        // end of the test, so it does not affect dev data.
        em.getEntityManager().createQuery("delete from Task").executeUpdate();
        em.getEntityManager().createQuery("delete from Project").executeUpdate();
        em.getEntityManager().createQuery("delete from User").executeUpdate();

        User user = newInstance(User.class);
        user.setEmail("ana@example.com");
        user.setPassword("irrelevant");
        user.setName("Ana");
        em.persist(user);

        project = newInstance(Project.class);
        project.setName("Project A");
        project.setUser(user);
        em.persist(project);

        otherProject = newInstance(Project.class);
        otherProject.setName("Project B");
        otherProject.setUser(user);
        em.persist(otherProject);

        persistTask("Write tests", Status.IN_PROGRESS, project);
        persistTask("Review PR", Status.IN_PROGRESS, project);
        persistTask("Deploy", Status.DONE, project);
        persistTask("Backlog item", Status.PENDING, otherProject);

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
        assertThat(repository.findByProjectId(project.getId())).hasSize(3);
        assertThat(repository.findByProjectId(otherProject.getId())).hasSize(1);
    }

    @Test
    void filtersByProjectAndStatusTogether() {
        List<Task> doneInProjectA =
                repository.findByProjectIdAndStatus(project.getId(), Status.DONE);

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

    private void persistTask(String title, Status status, Project owner) {
        Task task = newInstance(Task.class);
        task.setTitle(title);
        task.setStatus(status);
        task.setProject(owner);
        task.setDueDate(LocalDate.now().plusDays(7));
        em.persist(task);
    }
}
