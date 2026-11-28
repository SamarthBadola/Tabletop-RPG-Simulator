SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- Monster state (Ruleset Part G). The corrosion and destruction limits stay in the engine.
ALTER TABLE combatant
    ADD COLUMN form TEXT CHECK (form IS NULL OR char_length(btrim(form)) BETWEEN 1 AND 40),
    ADD COLUMN legendary_points BIGINT NOT NULL DEFAULT 0 CHECK (legendary_points >= 0),
    -- squawk-ignore adding-foreign-key-constraint
    ADD COLUMN controller_user_id UUID REFERENCES app_user (id),
    ADD COLUMN created BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN regen_suppressed BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN dying_by_trait BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN in_sunlight BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE encounter
    ADD COLUMN in_lair BOOLEAN NOT NULL DEFAULT FALSE;

-- Corrosion is stored as a penalty that is never positive.
ALTER TABLE inventory_item
    ADD COLUMN corrosion BIGINT NOT NULL DEFAULT 0 CHECK (corrosion <= 0);

-- The previous list is a strict subset of the replacement check.
ALTER TABLE pending_reaction
    DROP CONSTRAINT pending_reaction_point,
    ADD CONSTRAINT pending_reaction_point CHECK (point IN (
        'ON_MOVE_STEP', 'BEFORE_ATTACK_ROLL', 'AFTER_ATTACK_ROLL', 'AFTER_HIT',
        'AFTER_DAMAGE_ROLL', 'AFTER_SAVE_ROLL', 'AFTER_SAVE_FAIL', 'AFTER_CHECK_ROLL',
        'ON_CAST', 'AFTER_DAMAGE_TAKEN', 'ON_READIED_TRIGGER', 'ON_TURN_END'
    )) NOT VALID;
