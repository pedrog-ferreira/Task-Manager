package com.taskmanager.controller;

import com.taskmanager.dto.CreateTaskDTO;
import com.taskmanager.dto.TaskDTO;
import com.taskmanager.entity.Status;
import com.taskmanager.service.TaskService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for tasks. Thin, like {@code ProjectController} — no
 * {@code try/catch}; exceptions are handled by {@code GlobalExceptionHandler}.
 *
 * <h2>Two REST choices worth defending</h2>
 * <ul>
 *   <li><b>Nested resource</b> for the list: {@code GET /api/projects/{id}/tasks}
 *       says tasks belong to a project, which is more expressive than
 *       {@code /api/tasks?projectId=1}. The other operations act on a task
 *       directly, so they sit under {@code /api/tasks}. The class prefix is just
 *       {@code /api} because the paths diverge.</li>
 *   <li><b>PATCH, not PUT</b>, for the status change: PUT replaces the whole
 *       resource; changing only the status is a partial update.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class TaskController {

    private final TaskService service;

    public TaskController(TaskService service) {
        this.service = service;
    }

    /** GET /api/projects/{projectId}/tasks → 200 with the list, or 404 if the project is unknown. */
    @GetMapping("/projects/{projectId}/tasks")
    public List<TaskDTO> listByProject(@PathVariable Long projectId) {
        return service.listByProject(projectId);
    }

    /**
     * POST /api/tasks → 201 Created with the new task.
     * {@code @Valid} runs the bean-validation rules on {@link CreateTaskDTO}
     * before the method body; a failure short-circuits to a 400.
     */
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TaskDTO create(@Valid @RequestBody CreateTaskDTO dto) {
        return service.create(dto);
    }

    /**
     * PATCH /api/tasks/{id}/status?status=DONE → 200 with the updated task.
     * 404 if the task is unknown, 409 if it's DONE and the target isn't.
     */
    @PatchMapping("/tasks/{id}/status")
    public TaskDTO changeStatus(@PathVariable Long id, @RequestParam Status status) {
        return service.changeStatus(id, status);
    }

    /** DELETE /api/tasks/{id} → 204 No Content (empty body), or 404. */
    @DeleteMapping("/tasks/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
