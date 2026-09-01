package com.taskmanager.support;

import com.taskmanager.entity.Project;
import com.taskmanager.entity.Status;
import com.taskmanager.entity.Task;
import com.taskmanager.entity.User;

/**
 * Factory helpers so tests don't repeat the boilerplate of filling required
 * fields. Uses the public constructors the entities expose for the service layer.
 */
public final class TestEntities {

    private TestEntities() {
    }

    /** A user with the given email and a throwaway password/name. */
    public static User user(String email) {
        return new User(email, "test-password", "Test User");
    }

    /** A project owned by {@code owner}, with no description. */
    public static Project project(String name, User owner) {
        Project project = new Project(name, null);
        project.setUser(owner);
        return project;
    }

    /** A task in the given status, attached to {@code project}. */
    public static Task task(String title, Status status, Project project) {
        Task task = new Task(title, project);
        task.setStatus(status);
        return task;
    }
}
