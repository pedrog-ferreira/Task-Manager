package com.taskmanager.service;

import com.taskmanager.dto.CreateTaskDTO;
import com.taskmanager.dto.TaskDTO;
import com.taskmanager.entity.Project;
import com.taskmanager.entity.Status;
import com.taskmanager.entity.Task;
import com.taskmanager.exception.BusinessRuleException;
import com.taskmanager.exception.ResourceNotFoundException;
import com.taskmanager.repository.ProjectRepository;
import com.taskmanager.repository.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Business logic for tasks. Same shape as {@code ProjectService}: the controller
 * calls this, this calls the repositories, entities never leave the class.
 *
 * <h2>Two dependencies, injected by constructor</h2>
 * {@link TaskRepository} for tasks, {@link ProjectRepository} to resolve and
 * validate the parent project referenced by {@code CreateTaskDTO.projectId()}.
 *
 * <h2>Where the rules live</h2>
 * Annotations on the DTO check the request's <i>shape</i>. Rules that depend on
 * current state — "a new task starts PENDING", "a DONE task can't be reopened" —
 * live here, in the service. Keeping the two apart is deliberate.
 *
 * <h2>{@code @Transactional(readOnly = true)} on the class</h2>
 * See {@code ProjectService} for the full reasoning. Short version: cheaper
 * reads, and the session stays open so {@code toDTO} can walk {@code task.project}.
 * The write methods override it with a plain {@code @Transactional}.
 */
@Service
@Transactional(readOnly = true)
public class TaskService {

    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;

    public TaskService(TaskRepository taskRepository, ProjectRepository projectRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
    }

    /**
     * Tasks of a project, as DTOs. 404 if the project doesn't exist (an empty
     * list would wrongly suggest "project with no tasks").
     * <p>
     * Uses {@code findAllByProjectId}, whose {@code @EntityGraph} loads each
     * task's project in the same query — {@code toDTO} reads
     * {@code project.getName()}, so without it this would be an N+1.
     */
    public List<TaskDTO> listByProject(Long projectId) {
        if (!projectRepository.existsById(projectId)) {
            throw new ResourceNotFoundException("Project " + projectId + " not found");
        }
        return taskRepository.findAllByProjectId(projectId).stream()
                .map(this::toDTO)
                .toList();
    }

    @Transactional
    public TaskDTO create(CreateTaskDTO dto) {
        Project project = projectRepository.findById(dto.projectId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Project " + dto.projectId() + " not found"));

        Task task = new Task(dto.title(), project);   // project = the owning side
        task.setDescription(dto.description());
        task.setPriority(dto.priority());
        task.setDueDate(dto.dueDate());
        task.setStatus(Status.PENDING);               // server-imposed, never from the client

        // save() is required: `task` is a brand-new, transient entity.
        return toDTO(taskRepository.save(task));
    }

    /**
     * Move a task to {@code newStatus}. The one rule: a {@code DONE} task can't be
     * moved back — that's a {@link BusinessRuleException} → 409.
     */
    @Transactional
    public TaskDTO changeStatus(Long id, Status newStatus) {
        Task task = findOrThrow(id);

        if (task.getStatus() == Status.DONE && newStatus != Status.DONE) {
            throw new BusinessRuleException("A completed task cannot be reopened");
        }

        task.setStatus(newStatus);
        // No save(): `task` is managed, dirty checking flushes the UPDATE on commit.
        return toDTO(task);
    }

    @Transactional
    public void delete(Long id) {
        // delete(entity) after findOrThrow so a missing id is a clean 404, not a no-op.
        taskRepository.delete(findOrThrow(id));
    }

    // --- helpers -------------------------------------------------------------

    private Task findOrThrow(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Task " + id + " not found"));
    }

    /**
     * Entity to DTO. Called only from inside a transaction, so {@code getProject()}
     * (LAZY) can be traversed. {@code overdue} is computed here, not stored.
     */
    private TaskDTO toDTO(Task t) {
        boolean overdue = t.getDueDate() != null
                && t.getDueDate().isBefore(LocalDate.now())
                && t.getStatus() != Status.DONE;

        return new TaskDTO(
                t.getId(),
                t.getTitle(),
                t.getDescription(),
                t.getStatus(),
                t.getPriority(),
                t.getDueDate(),
                overdue,
                t.getProject().getId(),
                t.getProject().getName()
        );
    }
}
