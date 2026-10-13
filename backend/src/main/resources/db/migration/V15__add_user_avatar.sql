-- When the user's avatar was last replaced; NULL means the user has no avatar.
-- The image itself lives in object storage under a per-user key (avatars/<user id>.png),
-- and this timestamp doubles as the cache-busting version of the avatar URL.
ALTER TABLE users ADD COLUMN avatar_updated_at TIMESTAMPTZ;
