SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE affliction (
    id UUID PRIMARY KEY,
    combatant_id UUID NOT NULL REFERENCES combatant (id) ON DELETE CASCADE,
    kind TEXT NOT NULL CHECK (kind IN ('MADNESS_SHORT', 'MADNESS_LONG', 'MADNESS_INDEFINITE', 'DISEASE', 'POISON')),
    state TEXT NOT NULL CHECK (state IN ('ACTIVE', 'SUPPRESSED', 'ENDED')),
    definition_key TEXT NOT NULL,
    source TEXT NOT NULL,
    started_at_clock BIGINT NOT NULL CHECK (started_at_clock >= 0),
    ends_at_clock BIGINT CHECK (ends_at_clock > started_at_clock),
    ended_at_clock BIGINT CHECK (ended_at_clock >= started_at_clock),
    end_reason TEXT,
    progress BIGINT NOT NULL DEFAULT 0 CHECK (progress >= 0),
    caused_by UUID REFERENCES affliction (id) ON DELETE SET NULL,
    CHECK (kind NOT IN ('MADNESS_SHORT', 'MADNESS_LONG') OR ends_at_clock IS NOT NULL),
    CHECK ((state = 'ENDED') = (ended_at_clock IS NOT NULL AND end_reason IS NOT NULL))
);
CREATE INDEX affliction_combatant_idx ON affliction (combatant_id);
CREATE INDEX affliction_expiry_idx ON affliction (state, ends_at_clock) WHERE state <> 'ENDED';
CREATE INDEX affliction_caused_by_idx ON affliction (caused_by);
