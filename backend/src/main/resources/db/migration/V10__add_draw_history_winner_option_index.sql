-- Used by FAIR_ROTATION to find which options haven't won yet in the current round
CREATE INDEX idx_draw_history_winner_option ON draw_history (vote_id, winner_option_id, round);
