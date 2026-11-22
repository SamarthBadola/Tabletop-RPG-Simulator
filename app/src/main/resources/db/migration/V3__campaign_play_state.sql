SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE game_clock (
    campaign_id UUID PRIMARY KEY REFERENCES campaign (id) ON DELETE CASCADE,
    game_seconds BIGINT NOT NULL DEFAULT 0 CONSTRAINT clock_ok CHECK (game_seconds >= 0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0)
);

CREATE TABLE game_session (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    number BIGINT NOT NULL CHECK (number > 0),
    status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'PAUSED', 'ENDED')),
    started_at TIMESTAMPTZ NOT NULL,
    ended_at TIMESTAMPTZ,
    CHECK ((status = 'ENDED') = (ended_at IS NOT NULL)),
    UNIQUE (campaign_id, number),
    UNIQUE (campaign_id, id)
);

CREATE UNIQUE INDEX one_open_session ON game_session (campaign_id)
    WHERE status IN ('ACTIVE', 'PAUSED');

CREATE TABLE scene (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    session_id UUID,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
    notes TEXT CHECK (char_length(notes) <= 2000),
    ambient_light TEXT NOT NULL CHECK (ambient_light IN ('BRIGHT', 'DIM', 'DARK')),
    active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL,
    FOREIGN KEY (campaign_id, session_id) REFERENCES game_session (campaign_id, id),
    CHECK (NOT active OR session_id IS NOT NULL)
);

CREATE INDEX scene_session_idx ON scene (campaign_id, session_id);
CREATE UNIQUE INDEX one_active_scene ON scene (campaign_id) WHERE active;
