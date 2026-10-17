-- Refresh-token rotation keeps the rotated token (revoked_at set) instead of deleting it, and
-- groups all tokens of one login session under family_id. A rotated token presented again shortly
-- after rotation is a concurrent refresh (e.g. two browser tabs) and is still served; presented
-- later it was copied, and the whole family is revoked. Existing tokens each start their own family.
ALTER TABLE refresh_tokens ADD COLUMN family_id UUID;
UPDATE refresh_tokens SET family_id = id;
ALTER TABLE refresh_tokens ALTER COLUMN family_id SET NOT NULL;

ALTER TABLE refresh_tokens ADD COLUMN revoked_at TIMESTAMPTZ;

CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);
