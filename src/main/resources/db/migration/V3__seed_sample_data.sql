-- Sample data for the N+1 exercise (see notas-tecnicas.md, "Dia 3").
--
-- 30 projects × 15 tasks for the dev user (seeded by V2). With 2-3 rows the
-- N+1 problem is invisible — 1 extra query is noise. At this size, the
-- difference between "1 query" and "1 + N queries" shows up immediately in
-- the Hibernate statistics log (see application.yml, profile dev).
--
-- generate_series is Postgres-specific (this project targets Postgres only,
-- so that's fine) and keeps this migration to a few lines instead of 450
-- hand-written INSERTs.
INSERT INTO projects (name, description, user_id, created_at)
SELECT
    'Sample project ' || gs,
    'Seeded for the N+1 exercise — project #' || gs,
    (SELECT id FROM users WHERE email = 'dev@taskmanager.local'),
    CURRENT_TIMESTAMP
FROM generate_series(1, 30) AS gs;

INSERT INTO tasks (title, description, status, priority, project_id, created_at)
SELECT
    'Sample task ' || t,
    'Seeded for the N+1 exercise',
    (ARRAY['PENDING', 'IN_PROGRESS', 'DONE'])[1 + (t % 3)],
    (ARRAY['LOW', 'MEDIUM', 'HIGH'])[1 + (t % 3)],
    p.id,
    CURRENT_TIMESTAMP
FROM projects p
CROSS JOIN generate_series(1, 15) AS t
WHERE p.name LIKE 'Sample project %';
