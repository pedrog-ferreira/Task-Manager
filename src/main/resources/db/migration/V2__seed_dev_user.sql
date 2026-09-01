-- Development user.
--
-- There is no authentication yet (Spring Security comes later), but Project.user
-- is NOT NULL. Until the owner can come from the authenticated principal,
-- ProjectService attaches every project it creates to this fixed user.
--
-- `role` defaults to 'USER' and `created_at` defaults to now(), so only the three
-- required columns are listed here.
INSERT INTO users (email, password, name)
VALUES ('dev@taskmanager.local', 'not-a-real-password', 'Dev User');
