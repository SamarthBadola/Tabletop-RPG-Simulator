SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- Rogue, Bard and first-level Paladin have no fighting style. The sheet accepts a missing style.
-- squawk-ignore ban-drop-not-null
ALTER TABLE player_character ALTER COLUMN fighting_style_key DROP NOT NULL;
ALTER TABLE pc_proficiency
    DROP CONSTRAINT pc_proficiency_kind_check,
    ADD CONSTRAINT pc_proficiency_kind_check
        CHECK (kind IN ('skill', 'tool', 'save', 'weapon', 'armor', 'language', 'expertise')) NOT VALID;
