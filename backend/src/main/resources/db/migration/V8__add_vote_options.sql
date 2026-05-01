CREATE TABLE vote_options (
    id         UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id    UUID         NOT NULL REFERENCES votes (id) ON DELETE CASCADE,
    title      VARCHAR(255) NOT NULL,
    position   INT          NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (vote_id, title)
);

CREATE INDEX idx_vote_options_vote_id ON vote_options (vote_id);

ALTER TABLE draw_history ALTER COLUMN winner_email DROP NOT NULL;
ALTER TABLE draw_history ADD COLUMN winner_option_id    UUID         REFERENCES vote_options (id) ON DELETE SET NULL;
ALTER TABLE draw_history ADD COLUMN winner_option_title VARCHAR(255);
