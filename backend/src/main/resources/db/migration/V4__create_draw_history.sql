CREATE TABLE draw_history (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id              UUID         NOT NULL REFERENCES votes (id) ON DELETE CASCADE,
    winner_email         VARCHAR(255) NOT NULL,
    winner_display_name  VARCHAR(100),
    round                INT          NOT NULL,
    drawn_at             TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_draw_history_vote_id       ON draw_history (vote_id);
CREATE INDEX idx_draw_history_vote_round    ON draw_history (vote_id, round);

-- Used by FAIR_ROTATION to find who hasn't won yet in the current round
CREATE INDEX idx_draw_history_winner_email  ON draw_history (vote_id, winner_email, round);
