SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE campaign_action (
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    action_id UUID NOT NULL,
    actor_user_id UUID NOT NULL REFERENCES app_user (id),
    request_hash BYTEA NOT NULL CHECK (octet_length(request_hash) = 32),
    kind TEXT NOT NULL CHECK (kind IN (
        'SETUP', 'SESSION_START', 'SESSION_PAUSE', 'SESSION_RESUME', 'SESSION_END',
        'CLOCK_ADVANCE', 'INVITE_REVOKE', 'SCENE_CREATE', 'SCENE_EDIT', 'SCENE_ACTIVATE',
        'SETTING_DECIDE', 'SETTING_RESET', 'SETTING_MODE'
    )),
    result JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (campaign_id, action_id)
);

CREATE INDEX campaign_action_actor_idx ON campaign_action (actor_user_id);

CREATE TABLE campaign_event (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    seq BIGINT NOT NULL CHECK (seq > 0),
    action_id UUID NOT NULL,
    type TEXT NOT NULL CHECK (type IN (
        'SessionStarted', 'SessionPaused', 'SessionResumed', 'SessionEnded', 'ClockAdvanced',
        'InviteRevoked', 'SceneCreated', 'SceneEdited', 'SceneActivated',
        'SettingDecided', 'SettingModeChanged'
    )),
    game_time_seconds BIGINT NOT NULL CHECK (game_time_seconds >= 0),
    audience TEXT NOT NULL CHECK (audience IN ('DM', 'ALL', 'PLAYERS')),
    player_ids UUID[] NOT NULL DEFAULT '{}',
    payload JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (campaign_id, seq),
    FOREIGN KEY (campaign_id, action_id) REFERENCES campaign_action (campaign_id, action_id)
        ON DELETE CASCADE,
    CHECK ((audience = 'PLAYERS') = (cardinality(player_ids) > 0))
);

CREATE INDEX campaign_event_action_idx ON campaign_event (campaign_id, action_id);

CREATE TABLE campaign_setting (
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    key TEXT NOT NULL CHECK (char_length(key) BETWEEN 1 AND 64),
    value JSONB NOT NULL,
    source TEXT NOT NULL CHECK (source IN ('WIZARD', 'PROMPT', 'PANEL')),
    decided_by UUID NOT NULL REFERENCES app_user (id),
    decided_at TIMESTAMPTZ NOT NULL,
    decided_game_time BIGINT NOT NULL CHECK (decided_game_time >= 0),
    PRIMARY KEY (campaign_id, key)
);

CREATE INDEX campaign_setting_decider_idx ON campaign_setting (decided_by);
