-- Each of these duplicates the leading column(s) of an index PostgreSQL already maintains,
-- so it only costs writes and space:
--   users (email)                 ← UNIQUE (email)
--   users (telegram_id) partial   ← UNIQUE (telegram_id); NULLs are not indexed lookups anyway
--   vote_participants (vote_id)   ← UNIQUE (vote_id, email)
--   vote_options (vote_id)        ← UNIQUE (vote_id, title)
--   draw_history (vote_id)        ← idx_draw_history_vote_round (vote_id, round) and others
DROP INDEX IF EXISTS idx_users_email;
DROP INDEX IF EXISTS idx_users_telegram_id;
DROP INDEX IF EXISTS idx_vote_participants_vote_id;
DROP INDEX IF EXISTS idx_vote_options_vote_id;
DROP INDEX IF EXISTS idx_draw_history_vote_id;
