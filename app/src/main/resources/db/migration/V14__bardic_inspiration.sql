SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

ALTER TABLE player_character ADD COLUMN bardic_inspiration JSONB;
