-- Backs Project.tags (@ElementCollection). One row per tag — no surrogate id,
-- the natural key is (project_id, tag), same pattern Hibernate expects for an
-- element collection table.
CREATE TABLE project_tags (
    project_id  BIGINT      NOT NULL,
    tag         VARCHAR(50) NOT NULL,

    PRIMARY KEY (project_id, tag),

    CONSTRAINT fk_project_tags_project
        FOREIGN KEY (project_id) REFERENCES projects (id)
        ON DELETE CASCADE
);

-- A couple of tags per seeded project (see V3), so `tags` isn't an always-empty
-- collection when the N+1 exercise fetch-joins it.
INSERT INTO project_tags (project_id, tag)
SELECT p.id, 'backend'
FROM projects p
WHERE p.name LIKE 'Sample project %';

INSERT INTO project_tags (project_id, tag)
SELECT p.id, 'sample-data'
FROM projects p
WHERE p.name LIKE 'Sample project %';
