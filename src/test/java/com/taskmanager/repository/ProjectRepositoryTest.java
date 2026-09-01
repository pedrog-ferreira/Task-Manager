package com.taskmanager.repository;

import com.taskmanager.entity.Project;
import com.taskmanager.entity.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static com.taskmanager.support.TestEntities.project;
import static com.taskmanager.support.TestEntities.user;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Data-layer slice test for {@link ProjectRepository}.
 * <p>
 * {@code @DataJpaTest} boots only the JPA layer (fast) and wraps each test in a
 * transaction that is <b>rolled back</b> at the end — the assertions below never
 * touch data outside the test, and nothing is left in the DB afterwards.
 * <p>
 * {@code replace = NONE} keeps the real PostgreSQL from docker-compose instead of
 * swapping in an embedded H2 (see {@code src/test/resources/application.yml}).
 * The queries here filter by a freshly-created user's id, so the Flyway-seeded
 * "dev" user and any dev data can't interfere — no manual cleanup needed.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProjectRepositoryTest {

    @Autowired
    private ProjectRepository repository;

    @Autowired
    private TestEntityManager em;

    @Test
    void findsProjectsOfAUser() {
        User owner = em.persist(user("owner@mail.com"));
        User someoneElse = em.persist(user("other@mail.com"));
        em.persist(project("Project A", owner));
        em.persist(project("Project B", owner));
        em.persist(project("Not mine", someoneElse));
        em.flush();

        List<Project> result = repository.findByUserId(owner.getId());

        assertThat(result)
                .hasSize(2)
                .extracting(Project::getName)
                .containsExactlyInAnyOrder("Project A", "Project B");
    }

    @Test
    void detectsADuplicateNameForTheSameUser() {
        User owner = em.persist(user("dup@mail.com"));
        em.persist(project("Interview prep", owner));
        em.flush();

        assertThat(repository.existsByNameAndUserId("Interview prep", owner.getId())).isTrue();
        assertThat(repository.existsByNameAndUserId("Something else", owner.getId())).isFalse();
    }

    @Test
    void sameNameForADifferentUserIsNotADuplicate() {
        User owner = em.persist(user("a@mail.com"));
        User other = em.persist(user("b@mail.com"));
        em.persist(project("Shared name", owner));
        em.flush();

        assertThat(repository.existsByNameAndUserId("Shared name", other.getId())).isFalse();
    }
}
