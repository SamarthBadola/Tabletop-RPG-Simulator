SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- Timed effects are created under the campaign clock lock. Gaps carry no meaning.
CREATE SEQUENCE clock_effect_creation_order AS BIGINT START WITH 1;

-- Existing saved timers have no known campaign-wide creation order.
ALTER TABLE player_character ADD COLUMN stable_recovery_order BIGINT NOT NULL DEFAULT 0;
