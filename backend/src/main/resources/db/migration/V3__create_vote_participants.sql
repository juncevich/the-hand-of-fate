CREATE TABLE vote_participants (
    id           UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id      UUID         NOT NULL REFERENCES votes (id) ON DELETE CASCADE,
    email        VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    added_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    UNIQUE (vote_id, email)
);

CREATE INDEX idx_vote_participants_vote_id ON vote_participants (vote_id);
CREATE INDEX idx_vote_participants_email   ON vote_participants (email);
