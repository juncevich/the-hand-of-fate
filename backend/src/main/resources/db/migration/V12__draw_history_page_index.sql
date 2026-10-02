CREATE INDEX idx_draw_history_vote_drawn_id
    ON draw_history (vote_id, drawn_at DESC, id DESC);
