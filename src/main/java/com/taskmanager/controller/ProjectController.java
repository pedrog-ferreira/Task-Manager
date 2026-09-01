package com.taskmanager.controller;

import com.taskmanager.dto.CreateProjectDTO;
import com.taskmanager.dto.ProjectDTO;
import com.taskmanager.service.ProjectService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * HTTP entry point for projects. Thin on purpose: it maps requests to service
 * calls and lets the framework handle the rest.
 * <ul>
 *   <li>{@code @RestController} = {@code @Controller} + {@code @ResponseBody} —
 *       return values are serialized to JSON by Jackson.</li>
 *   <li>{@code @RequestMapping("/api/projects")} — common path prefix for every
 *       handler below.</li>
 *   <li>No {@code try/catch}: exceptions from the service are handled centrally
 *       by {@code GlobalExceptionHandler}.</li>
 *   <li>Default response status is 200; {@code @ResponseStatus} overrides it
 *       where REST convention wants 201 / 204.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/projects")
public class ProjectController {

    private final ProjectService service;

    public ProjectController(ProjectService service) {
        this.service = service;
    }

    /** GET /api/projects → 200 with the list. */
    @GetMapping
    public List<ProjectDTO> list() {
        return service.list();
    }

    /** GET /api/projects/{id} → 200, or 404 if it doesn't exist. */
    @GetMapping("/{id}")
    public ProjectDTO get(@PathVariable Long id) {
        return service.get(id);
    }

    /**
     * POST /api/projects → 201 Created with the new project.
     * {@code @Valid} runs the bean-validation rules on {@link CreateProjectDTO}
     * before the method body; a failure short-circuits to a 400.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjectDTO create(@Valid @RequestBody CreateProjectDTO dto) {
        return service.create(dto);
    }

    /** PUT /api/projects/{id} → 200 with the updated project, or 404. */
    @PutMapping("/{id}")
    public ProjectDTO update(@PathVariable Long id, @Valid @RequestBody CreateProjectDTO dto) {
        return service.update(id, dto);
    }

    /**
     * DELETE /api/projects/{id} → 204 No Content (empty body), or 404.
     * {@code void} + 204 is the REST convention for a successful delete.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
