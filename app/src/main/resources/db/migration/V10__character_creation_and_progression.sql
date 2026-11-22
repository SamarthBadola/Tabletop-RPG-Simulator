SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- The previous list is a strict subset, so existing rows already satisfy this check.
ALTER TABLE campaign_action
    DROP CONSTRAINT campaign_action_kind_check,
    ADD CONSTRAINT campaign_action_kind_check CHECK (kind IN (
        'SETUP', 'SESSION_START', 'SESSION_PAUSE', 'SESSION_RESUME', 'SESSION_END',
        'CLOCK_ADVANCE', 'INVITE_REVOKE', 'SCENE_CREATE', 'SCENE_EDIT', 'SCENE_ACTIVATE',
        'SETTING_DECIDE', 'SETTING_RESET', 'SETTING_MODE', 'CHARACTER_ROLL', 'CHARACTER_CREATE',
        'CHARACTER_EDIT', 'CHARACTER_LEVEL_GRANT', 'CHARACTER_LEVEL_UP', 'PACK_UPGRADE'
    )) NOT VALID;

ALTER TABLE campaign_event
    DROP CONSTRAINT campaign_event_type_check,
    ADD CONSTRAINT campaign_event_type_check CHECK (type IN (
        'SessionStarted', 'SessionPaused', 'SessionResumed', 'SessionEnded', 'ClockAdvanced',
        'InviteRevoked', 'SceneCreated', 'SceneEdited', 'SceneActivated', 'SettingDecided',
        'SettingModeChanged', 'AbilityScoresRolled', 'CharacterCreated', 'CharacterEdited',
        'CharacterLevelGranted', 'CharacterLeveledUp', 'ContentPackUpgraded'
    )) NOT VALID;

CREATE TABLE player_character (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    owner_id UUID NOT NULL,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
    species_key TEXT NOT NULL,
    background_key TEXT,
    fighting_style_key TEXT NOT NULL,
    xp BIGINT NOT NULL DEFAULT 0 CHECK (xp >= 0),
    hp_current BIGINT NOT NULL CHECK (hp_current >= 0),
    hp_temp BIGINT NOT NULL DEFAULT 0 CHECK (hp_temp >= 0),
    death_successes BIGINT NOT NULL DEFAULT 0 CHECK (death_successes BETWEEN 0 AND 3),
    death_failures BIGINT NOT NULL DEFAULT 0 CHECK (death_failures BETWEEN 0 AND 3),
    copper BIGINT NOT NULL DEFAULT 0 CHECK (copper >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (campaign_id, owner_id) REFERENCES campaign_member (campaign_id, user_id)
        DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX player_character_campaign_owner_idx ON player_character (campaign_id, owner_id);

CREATE TABLE pc_ability (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    ability TEXT NOT NULL CHECK (ability IN ('STR', 'DEX', 'CON', 'INT', 'WIS', 'CHA')),
    score BIGINT NOT NULL CHECK (score BETWEEN 1 AND 30),
    PRIMARY KEY (player_character_id, ability)
);

CREATE TABLE pc_class (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    class_key TEXT NOT NULL,
    subclass_key TEXT,
    level BIGINT NOT NULL CHECK (level BETWEEN 1 AND 20),
    PRIMARY KEY (player_character_id, class_key)
);

CREATE TABLE pc_class_level (
    player_character_id UUID NOT NULL,
    class_key TEXT NOT NULL,
    class_level BIGINT NOT NULL CHECK (class_level BETWEEN 1 AND 20),
    character_level BIGINT NOT NULL CHECK (character_level BETWEEN 1 AND 20),
    raw_hit_points BIGINT NOT NULL CHECK (raw_hit_points BETWEEN 1 AND 20),
    PRIMARY KEY (player_character_id, class_key, class_level),
    UNIQUE (player_character_id, character_level),
    FOREIGN KEY (player_character_id, class_key) REFERENCES pc_class (player_character_id, class_key)
        ON DELETE CASCADE
);

CREATE TABLE pc_hit_dice (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    die_size BIGINT NOT NULL CHECK (die_size IN (6, 8, 10, 12)),
    spent BIGINT NOT NULL DEFAULT 0 CHECK (spent BETWEEN 0 AND 20),
    PRIMARY KEY (player_character_id, die_size)
);

CREATE TABLE pc_asi (
    player_character_id UUID NOT NULL,
    class_key TEXT NOT NULL,
    class_level BIGINT NOT NULL,
    increases JSONB NOT NULL CHECK (jsonb_typeof(increases) = 'object'),
    feat_key TEXT,
    PRIMARY KEY (player_character_id, class_key, class_level),
    FOREIGN KEY (player_character_id, class_key, class_level)
        REFERENCES pc_class_level (player_character_id, class_key, class_level) ON DELETE CASCADE
);

CREATE TABLE pc_proficiency (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('skill', 'tool', 'save', 'weapon', 'armor', 'language')),
    key TEXT NOT NULL,
    source TEXT NOT NULL,
    PRIMARY KEY (player_character_id, kind, key, source)
);

CREATE TABLE pc_resource (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    resource_key TEXT NOT NULL,
    used BIGINT NOT NULL DEFAULT 0 CHECK (used >= 0),
    PRIMARY KEY (player_character_id, resource_key)
);

CREATE TABLE pc_condition (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    condition_key TEXT NOT NULL,
    PRIMARY KEY (player_character_id, condition_key)
);

CREATE TABLE pc_slot (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    slot_level BIGINT NOT NULL CHECK (slot_level BETWEEN 1 AND 9),
    used BIGINT NOT NULL DEFAULT 0 CHECK (used >= 0),
    PRIMARY KEY (player_character_id, slot_level)
);

CREATE TABLE pc_spell (
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    class_key TEXT NOT NULL,
    spell_key TEXT NOT NULL,
    prepared BOOLEAN NOT NULL DEFAULT FALSE,
    PRIMARY KEY (player_character_id, class_key, spell_key),
    FOREIGN KEY (player_character_id, class_key) REFERENCES pc_class (player_character_id, class_key)
        ON DELETE CASCADE
);

CREATE TABLE inventory_item (
    id UUID PRIMARY KEY,
    player_character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    item_key TEXT NOT NULL,
    quantity BIGINT NOT NULL CHECK (quantity > 0),
    equipment_slot TEXT CHECK (equipment_slot IN ('ARMOR', 'MAIN_HAND', 'OFF_HAND', 'BOTH_HANDS')),
    UNIQUE (player_character_id, equipment_slot)
);
CREATE INDEX inventory_item_character_idx ON inventory_item (player_character_id);

CREATE TABLE pc_level_grant (
    player_character_id UUID PRIMARY KEY,
    class_key TEXT NOT NULL,
    granted_by UUID NOT NULL REFERENCES app_user (id),
    granted_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (player_character_id, class_key) REFERENCES pc_class (player_character_id, class_key)
        ON DELETE CASCADE
);
CREATE INDEX pc_level_grant_granted_by_idx ON pc_level_grant (granted_by);

CREATE TABLE rolled_set (
    campaign_id UUID NOT NULL,
    user_id UUID NOT NULL,
    method TEXT NOT NULL CHECK (method IN ('ROLL_4D6_DROP_LOWEST', 'ROLL_3D6')),
    values_json JSONB NOT NULL CHECK (
        jsonb_typeof(values_json) = 'array' AND jsonb_array_length(values_json) = 6
    ),
    PRIMARY KEY (campaign_id, user_id),
    FOREIGN KEY (campaign_id, user_id) REFERENCES campaign_member (campaign_id, user_id)
        ON DELETE CASCADE
);
