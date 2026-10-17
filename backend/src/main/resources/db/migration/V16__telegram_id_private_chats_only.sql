-- The bot used to identify users by chat id, so /link sent in a group stored the group's id
-- (Telegram group/channel ids are negative), letting every member of that group act as the user.
-- Drop such links — affected users simply /link again in a private chat — and forbid them from now on.
UPDATE users SET telegram_id = NULL, telegram_name = NULL WHERE telegram_id <= 0;

ALTER TABLE users ADD CONSTRAINT chk_users_telegram_id_positive CHECK (telegram_id > 0);
