-- Options added after creation used to get position Int.MAX_VALUE (ordered by created_at among
-- themselves), which made reordering impossible. Renumber every vote's options 0..n-1 in their
-- current order and keep positions unique per vote; deferred, so a reorder may swap positions
-- within one transaction.
WITH ordered AS (
    SELECT id, row_number() OVER (PARTITION BY vote_id ORDER BY position, created_at, id) - 1 AS pos
    FROM vote_options
)
UPDATE vote_options o SET position = ordered.pos FROM ordered WHERE o.id = ordered.id;

ALTER TABLE vote_options
    ADD CONSTRAINT uq_vote_options_vote_position UNIQUE (vote_id, position) DEFERRABLE INITIALLY DEFERRED;
