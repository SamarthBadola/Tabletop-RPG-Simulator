SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE pending_reaction (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    encounter_id UUID UNIQUE REFERENCES encounter (id) ON DELETE CASCADE,
    scene_id UUID UNIQUE REFERENCES scene (id) ON DELETE CASCADE,
    opened_by_action_id UUID NOT NULL,
    point TEXT NOT NULL,
    -- The architecture specifies SMALLINT. The depth check caps values at 6.
    -- squawk-ignore prefer-bigint-over-smallint
    depth SMALLINT NOT NULL,
    offers JSONB NOT NULL,
    continuation JSONB NOT NULL,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT pending_reaction_one_owner CHECK ((encounter_id IS NULL) <> (scene_id IS NULL)),
    CONSTRAINT pending_reaction_depth CHECK (depth BETWEEN 1 AND 6),
    CONSTRAINT pending_reaction_point CHECK (point IN (
        'ON_MOVE_STEP', 'BEFORE_ATTACK_ROLL', 'AFTER_ATTACK_ROLL', 'AFTER_HIT',
        'AFTER_DAMAGE_ROLL', 'AFTER_SAVE_ROLL', 'AFTER_SAVE_FAIL', 'AFTER_CHECK_ROLL',
        'ON_CAST', 'AFTER_DAMAGE_TAKEN', 'ON_READIED_TRIGGER'
    ))
);
CREATE INDEX pending_reaction_campaign_idx ON pending_reaction (campaign_id);
