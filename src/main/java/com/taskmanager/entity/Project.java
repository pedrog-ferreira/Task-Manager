package com.taskmanager.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "projects")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Constructor for application code. The owner ({@code user}) is set
     * afterwards with the setter, and {@code tasks}/{@code createdAt} are
     * managed by JPA. JPA itself uses the protected no-args constructor.
     */
    public Project(String name, String description) {
        this.name = name;
        this.description = description;
    }

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Task> tasks = new ArrayList<>();

    /**
     * Free-form labels, stored in a separate {@code project_tags} table
     * (one row per tag) rather than a column on {@code projects}.
     * <p>
     * Not wired into the API yet. It exists so this entity has a <b>second</b>
     * collection: {@code List} without {@code @OrderColumn} is a Hibernate
     * "bag", and fetch-joining two bags of the same root in one query is
     * exactly what triggers {@code MultipleBagFetchException} — see
     * {@code ProjectRepository#findAllFetchingTasksAndTags} and
     * {@code notas-tecnicas.md}, Dia 3.
     */
    @ElementCollection
    @CollectionTable(name = "project_tags", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "tag", nullable = false, length = 50)
    private List<String> tags = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
