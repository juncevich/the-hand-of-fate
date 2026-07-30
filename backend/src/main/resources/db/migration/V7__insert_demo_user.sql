-- Demo user (admin@admin.com / admin) for local development only.
-- Gated behind the Flyway placeholder `seedDemoUser`, which defaults to "false"
-- (see application.yml) and is set to "true" only under the dev/test profiles.
-- This keeps the well-known credentials out of any production database.
DO
$$
    BEGIN
        IF '${seedDemoUser}' = 'true' THEN
            INSERT INTO users (id, email, password_hash, display_name)
            SELECT gen_random_uuid(),
                   'admin@admin.com',
                   '$2b$10$b6Nr3I5UyNHsuE.Xfd6vIeHXjF8Kw1Be.RjnrRw/GVlrC38lhAW.K',
                   'admin'
            WHERE NOT EXISTS (SELECT 1 FROM users WHERE email = 'admin@admin.com');
        END IF;
    END
$$;
