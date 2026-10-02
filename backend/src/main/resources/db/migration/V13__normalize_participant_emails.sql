-- Merge aliases before applying canonical email addresses, preserving the earliest participant.
WITH aliases AS (
    SELECT id, row_number() OVER (
        PARTITION BY vote_id, lower(btrim(email)) ORDER BY added_at, id
    ) AS alias_number
    FROM vote_participants
)
DELETE FROM vote_participants p USING aliases a
WHERE p.id = a.id AND a.alias_number > 1;

UPDATE vote_participants SET email = lower(btrim(email))
WHERE email <> lower(btrim(email));

-- Fair rotation must match historical wins against canonical participant addresses.
UPDATE draw_history SET winner_email = lower(btrim(winner_email))
WHERE winner_email IS NOT NULL AND winner_email <> lower(btrim(winner_email));
