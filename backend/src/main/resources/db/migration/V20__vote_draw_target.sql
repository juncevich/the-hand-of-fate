-- What a vote draws from used to be implicit: options if it had any, participants otherwise, so a
-- single added option silently changed the vote's meaning. It is now chosen at creation and fixed.
-- Existing votes keep the behaviour they had.
CREATE TYPE draw_target AS ENUM ('PARTICIPANTS', 'OPTIONS');

ALTER TABLE votes ADD COLUMN draw_target draw_target NOT NULL DEFAULT 'PARTICIPANTS';

UPDATE votes v SET draw_target = 'OPTIONS'
WHERE EXISTS (SELECT 1 FROM vote_options o WHERE o.vote_id = v.id);
