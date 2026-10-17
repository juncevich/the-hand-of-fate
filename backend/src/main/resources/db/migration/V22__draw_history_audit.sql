-- Every draw records what it was drawn from, so its winner can be recomputed: a SecureRandom seed
-- (32 bytes, hex) and the candidates in canonical order (participant emails or option ids, sorted).
-- The winner is candidates[WinnerPicker.index(seed, cardinality(candidates))]. NULL for older draws.
ALTER TABLE draw_history ADD COLUMN seed VARCHAR(64);
ALTER TABLE draw_history ADD COLUMN candidates TEXT[];

ALTER TABLE draw_history
    ADD CONSTRAINT chk_draw_history_audit CHECK ((seed IS NULL) = (candidates IS NULL));
