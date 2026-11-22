SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE campaign (
    id UUID PRIMARY KEY,
    name TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
    settings_mode TEXT NOT NULL DEFAULT 'DEFAULTS_ONLY'
        CHECK (settings_mode IN ('CONFIGURE_NOW', 'ASK_AS_NEEDED', 'DEFAULTS_ONLY')),
    created_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE campaign_member (
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES app_user (id),
    role TEXT NOT NULL CHECK (role IN ('DM', 'PLAYER')),
    joined_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (campaign_id, user_id)
);

CREATE INDEX campaign_member_user_idx ON campaign_member (user_id, campaign_id);

CREATE TABLE invite (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    code_hash BYTEA NOT NULL UNIQUE CHECK (octet_length(code_hash) = 32),
    created_by UUID NOT NULL REFERENCES app_user (id),
    created_at TIMESTAMPTZ NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL CHECK (expires_at > created_at),
    max_uses BIGINT NOT NULL CHECK (max_uses BETWEEN 1 AND 20),
    uses_left BIGINT NOT NULL CHECK (uses_left BETWEEN 0 AND max_uses),
    revoked_at TIMESTAMPTZ
);

CREATE INDEX invite_campaign_idx ON invite (campaign_id);
CREATE INDEX invite_creator_idx ON invite (created_by);

CREATE TABLE form_token (
    token UUID PRIMARY KEY,
    session_hash BYTEA NOT NULL CHECK (octet_length(session_hash) = 32),
    purpose TEXT NOT NULL,
    issued_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    request_hash BYTEA,
    result_location TEXT,
    CHECK (
        (consumed_at IS NULL AND request_hash IS NULL AND result_location IS NULL)
        OR (consumed_at IS NOT NULL AND request_hash IS NOT NULL AND octet_length(request_hash) = 32)
    )
);

CREATE INDEX form_token_issued_idx ON form_token (issued_at);
CREATE INDEX form_token_consumed_idx ON form_token (consumed_at) WHERE consumed_at IS NOT NULL;
