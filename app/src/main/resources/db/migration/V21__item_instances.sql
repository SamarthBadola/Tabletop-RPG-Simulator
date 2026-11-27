SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- New columns are empty or constant defaults, so no table rewrite is needed. The two foreign keys
-- point at new, all-null columns, so there is nothing to scan.
ALTER TABLE inventory_item
    ADD COLUMN identified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN silvered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN corrosion BIGINT NOT NULL DEFAULT 0 CHECK (corrosion >= 0),
    ADD COLUMN charges BIGINT CHECK (charges >= 0),
    -- squawk-ignore adding-foreign-key-constraint
    ADD COLUMN attuned_to UUID REFERENCES player_character (id) ON DELETE SET NULL,
    -- squawk-ignore adding-foreign-key-constraint
    ADD COLUMN ground_scene_id UUID REFERENCES scene (id) ON DELETE CASCADE,
    ADD COLUMN ground_x BIGINT,
    ADD COLUMN ground_y BIGINT;

-- An item now sits either on a character or on the ground of a scene.
-- squawk-ignore ban-drop-not-null
ALTER TABLE inventory_item ALTER COLUMN player_character_id DROP NOT NULL;

-- Existing rows all belong to a character, so they already satisfy these rules.
ALTER TABLE inventory_item
    ADD CONSTRAINT inventory_item_one_place
        CHECK ((player_character_id IS NULL) <> (ground_scene_id IS NULL)) NOT VALID,
    ADD CONSTRAINT inventory_item_ground_clean
        CHECK (ground_scene_id IS NOT NULL
               OR (ground_x IS NULL AND ground_y IS NULL)) NOT VALID,
    ADD CONSTRAINT inventory_item_ground_unheld
        CHECK (ground_scene_id IS NULL
               OR (equipment_slot IS NULL AND attuned_to IS NULL)) NOT VALID,
    ADD CONSTRAINT inventory_item_charged_single
        CHECK (charges IS NULL OR quantity = 1) NOT VALID;

-- The index is on a new column that is all null, so building it is instant. CONCURRENTLY cannot run
-- inside the migration transaction.
-- squawk-ignore require-concurrent-index-creation
CREATE INDEX inventory_item_ground_idx ON inventory_item (ground_scene_id)
    WHERE ground_scene_id IS NOT NULL;
