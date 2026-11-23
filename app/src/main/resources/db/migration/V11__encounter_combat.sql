SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- Existing action kinds are a strict subset of the replacement check.
ALTER TABLE campaign_action
    DROP CONSTRAINT campaign_action_kind_check,
    ADD CONSTRAINT campaign_action_kind_check CHECK (kind IN (
        'SETUP', 'SESSION_START', 'SESSION_PAUSE', 'SESSION_RESUME', 'SESSION_END',
        'CLOCK_ADVANCE', 'INVITE_REVOKE', 'SCENE_CREATE', 'SCENE_EDIT', 'SCENE_ACTIVATE',
        'SETTING_DECIDE', 'SETTING_RESET', 'SETTING_MODE', 'CHARACTER_ROLL', 'CHARACTER_CREATE',
        'CHARACTER_EDIT', 'CHARACTER_LEVEL_GRANT', 'CHARACTER_LEVEL_UP', 'PACK_UPGRADE',
        'ENCOUNTER_CREATE'
    )) NOT VALID;

ALTER TABLE player_character ADD COLUMN stable_recovery_at BIGINT;

CREATE TABLE encounter (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
    state TEXT NOT NULL CHECK (state IN ('SETUP', 'INITIATIVE', 'ACTIVE', 'FINISHED')),
    round BIGINT NOT NULL CHECK (round >= 0),
    acting_id UUID,
    budget JSONB NOT NULL,
    version BIGINT NOT NULL CHECK (version >= 0)
);
CREATE INDEX encounter_campaign_idx ON encounter (campaign_id, id);
CREATE UNIQUE INDEX encounter_one_running_idx ON encounter (campaign_id)
    WHERE state IN ('INITIATIVE', 'ACTIVE');

CREATE TABLE combatant (
    id UUID PRIMARY KEY,
    encounter_id UUID NOT NULL REFERENCES encounter (id) ON DELETE CASCADE,
    character_id UUID REFERENCES player_character (id) ON DELETE CASCADE,
    monster_key TEXT,
    ordinal BIGINT NOT NULL CHECK (ordinal > 0),
    initiative BIGINT,
    tie_order BIGINT,
    tactics JSONB NOT NULL,
    npc_vitals JSONB,
    CHECK (num_nonnulls(character_id, monster_key) = 1),
    CHECK ((character_id IS NULL) = (npc_vitals IS NOT NULL)),
    UNIQUE (encounter_id, ordinal),
    UNIQUE (encounter_id, character_id)
);
CREATE INDEX combatant_character_idx ON combatant (character_id);

CREATE TABLE encounter_action (
    encounter_id UUID NOT NULL REFERENCES encounter (id) ON DELETE CASCADE,
    action_id UUID NOT NULL,
    actor_id UUID NOT NULL REFERENCES app_user (id),
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash) = 32),
    result JSONB NOT NULL,
    version BIGINT NOT NULL CHECK (version > 0),
    settings_read JSONB NOT NULL,
    PRIMARY KEY (encounter_id, action_id)
);
CREATE INDEX encounter_action_actor_idx ON encounter_action (actor_id);

CREATE TABLE combat_event (
    id UUID PRIMARY KEY,
    seq BIGINT GENERATED ALWAYS AS IDENTITY,
    encounter_id UUID NOT NULL,
    action_id UUID NOT NULL,
    game_time_seconds BIGINT NOT NULL CHECK (game_time_seconds >= 0),
    round BIGINT NOT NULL CHECK (round >= 0),
    type TEXT NOT NULL,
    audience TEXT NOT NULL CHECK (audience IN ('DM', 'ALL', 'PLAYERS')),
    player_ids UUID[] NOT NULL DEFAULT '{}',
    payload JSONB NOT NULL,
    CHECK ((audience = 'PLAYERS') = (cardinality(player_ids) > 0)),
    FOREIGN KEY (encounter_id, action_id)
        REFERENCES encounter_action (encounter_id, action_id) ON DELETE CASCADE
);
CREATE INDEX combat_event_action_idx ON combat_event (encounter_id, action_id);
CREATE UNIQUE INDEX combat_event_order_idx ON combat_event (encounter_id, seq);
