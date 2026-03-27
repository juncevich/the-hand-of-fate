CREATE TYPE vote_mode   AS ENUM ('SIMPLE', 'FAIR_ROTATION');
CREATE TYPE vote_status AS ENUM ('PENDING', 'DRAWN', 'CLOSED');

CREATE TABLE votes (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    title         VARCHAR(255) NOT NULL,
    description   TEXT,
    creator_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    mode          vote_mode    NOT NULL DEFAULT 'SIMPLE',
    status        vote_status  NOT NULL DEFAULT 'PENDING',
    current_round INT          NOT NULL DEFAULT 1,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_votes_creator_id ON votes (creator_id);
CREATE INDEX idx_votes_status     ON votes (status);
