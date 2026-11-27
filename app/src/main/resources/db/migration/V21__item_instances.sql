ALTER TABLE inventory_item
    ADD COLUMN identified BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN silvered BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN corrosion INTEGER NOT NULL DEFAULT 0 CHECK (corrosion >= 0),
    ADD COLUMN charges INTEGER CHECK (charges >= 0),
    ADD COLUMN attuned_to UUID REFERENCES player_character (id) ON DELETE SET NULL,
    ADD COLUMN ground_scene_id UUID REFERENCES scene (id) ON DELETE CASCADE,
    ADD COLUMN ground_x INTEGER,
    ADD COLUMN ground_y INTEGER;

ALTER TABLE inventory_item ALTER COLUMN player_character_id DROP NOT NULL;

ALTER TABLE inventory_item
    ADD CONSTRAINT inventory_item_one_place
        CHECK ((player_character_id IS NULL) <> (ground_scene_id IS NULL)),
    ADD CONSTRAINT inventory_item_ground_clean
        CHECK (ground_scene_id IS NOT NULL
               OR (ground_x IS NULL AND ground_y IS NULL)),
    ADD CONSTRAINT inventory_item_ground_unheld
        CHECK (ground_scene_id IS NULL
               OR (equipment_slot IS NULL AND attuned_to IS NULL)),
    ADD CONSTRAINT inventory_item_charged_single
        CHECK (charges IS NULL OR quantity = 1);

CREATE INDEX inventory_item_ground_idx ON inventory_item (ground_scene_id)
    WHERE ground_scene_id IS NOT NULL;
