SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- Coins are held per denomination (FR-EQP-014). The existing copper column keeps its meaning
-- as the number of copper pieces, so every old row stays valid: its value was all copper.
ALTER TABLE player_character
    ADD COLUMN silver BIGINT NOT NULL DEFAULT 0 CHECK (silver >= 0),
    ADD COLUMN electrum BIGINT NOT NULL DEFAULT 0 CHECK (electrum >= 0),
    ADD COLUMN gold BIGINT NOT NULL DEFAULT 0 CHECK (gold >= 0),
    ADD COLUMN platinum BIGINT NOT NULL DEFAULT 0 CHECK (platinum >= 0);
