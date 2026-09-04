package com.taskmanager.service;

import com.taskmanager.dto.CreateProjectDTO;
import com.taskmanager.dto.ProjectDTO;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.User;
import com.taskmanager.exception.DuplicateNameException;
import com.taskmanager.exception.ResourceNotFoundException;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Business logic for projects. The controller calls this; this calls the
 * repositories. Entities never leave this class — everything in and out is a DTO.
 *
 * <h2>Why {@code @Transactional(readOnly = true)} on the class</h2>
 * Every public method runs in a transaction. Read-only is the default here, and
 * the write methods override it with a plain {@code @Transactional}. Two effects:
 * <ul>
 *   <li>Hibernate skips <b>dirty checking</b> on read-only transactions (it
 *       won't scan loaded entities for changes to flush), and can hint the JDBC
 *       driver / DB that the connection is read-only. Cheaper reads.</li>
 *   <li>The persistence context (session) stays open for the whole method, so
 *       {@code toDTO} can walk a {@code LAZY} association — {@code p.getTasks()} —
 *       without a {@code LazyInitializationException}.</li>
 * </ul>
 *
 * <h2>Why {@code update} has no {@code save()}</h2>
 * {@code findById} returns a <b>managed</b> entity (attached to the session).
 * Inside the transaction, changing its fields is enough: on commit, Hibernate's
 * dirty checking detects the change and issues the {@code UPDATE} automatically.
 * Calling {@code save()} would work too but is redundant.
 */
@Service
@Transactional(readOnly = true)
public class ProjectService {

    /**
     * Owner for every project created through the API, until authentication
     * exists. Seeded by Flyway migration {@code V2__seed_dev_user.sql}.
     */
    private static final String DEV_USER_EMAIL = "dev@taskmanager.local";

    private final ProjectRepository repository;
    private final UserRepository userRepository;

    // Constructor injection (no @Autowired needed for a single constructor):
    // dependencies are explicit, final, and the class is trivial to unit-test.
    public ProjectService(ProjectRepository repository, UserRepository userRepository) {
        this.repository = repository;
        this.userRepository = userRepository;
    }

    /**
     * All projects, newest-or-not in insertion order, as DTOs.
     * <p>
     * {@code repository.findAll()} is overridden with {@code @EntityGraph} to
     * fetch {@code tasks} in the same query — without it, {@code toDTO} touching
     * {@code p.getTasks()} for each project is a classic N+1. See
     * {@code ProjectRepository} and {@code notas-tecnicas.md}, Dia 3, for the
     * measured query counts and the two other fixes considered.
     */
    public List<ProjectDTO> list() {
        return repository.findAll().stream()
                .map(this::toDTO)
                .toList();
    }

    /** One project by id, or 404. */
    public ProjectDTO get(Long id) {
        return toDTO(findOrThrow(id));
    }

    @Transactional
    public ProjectDTO create(CreateProjectDTO dto) {
        User owner = devUser();
        requireNameFree(dto.name(), owner.getId());

        Project project = new Project(dto.name(), dto.description());
        project.setUser(owner);

        // save() is required here: `project` is a brand-new, transient entity —
        // Hibernate only starts tracking it once it's persisted.
        return toDTO(repository.save(project));
    }

    @Transactional
    public ProjectDTO update(Long id, CreateProjectDTO dto) {
        Project project = findOrThrow(id);

        // Only check for a clash if the name actually changed.
        if (!project.getName().equals(dto.name())) {
            requireNameFree(dto.name(), project.getUser().getId());
        }

        project.setName(dto.name());
        project.setDescription(dto.description());

        // No save(): `project` is managed, dirty checking flushes the UPDATE on commit.
        return toDTO(project);
    }

    @Transactional
    public void delete(Long id) {
        // delete(entity) rather than deleteById(id) so a missing row is a clean
        // 404 instead of a silent no-op. Project's tasks go with it
        // (cascade = ALL + orphanRemoval on the entity).
        repository.delete(findOrThrow(id));
    }

    // --- helpers -------------------------------------------------------------

    private Project findOrThrow(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Project " + id + " not found"));
    }

    private User devUser() {
        return userRepository.findByEmail(DEV_USER_EMAIL)
                .orElseThrow(() -> new IllegalStateException(
                        "Seed user " + DEV_USER_EMAIL + " is missing — did migration V2 run?"));
    }

    private void requireNameFree(String name, Long ownerId) {
        if (repository.existsByNameAndUserId(name, ownerId)) {
            throw new DuplicateNameException("A project named \"" + name + "\" already exists");
        }
    }

    /**
     * Entity to DTO. Called only from inside a transaction, so {@code getTasks()}
     * (LAZY) can be traversed. When called from {@link #list()}, {@code tasks}
     * is already loaded by the {@code @EntityGraph} on {@code findAll()}, so
     * {@code .size()} is free; from {@link #get(Long)} (plain {@code findById})
     * it costs one extra SELECT for that single project — fine outside a loop.
     */
    private ProjectDTO toDTO(Project p) {
        return new ProjectDTO(
                p.getId(),
                p.getName(),
                p.getDescription(),
                p.getTasks().size(),
                p.getCreatedAt()
        );
    }
}
