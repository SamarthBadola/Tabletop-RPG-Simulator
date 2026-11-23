SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

ALTER TABLE player_character
    ADD COLUMN exhaustion BIGINT NOT NULL DEFAULT 0 CHECK (exhaustion BETWEEN 0 AND 6),
    ADD COLUMN condition_effects JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(condition_effects) = 'array');
