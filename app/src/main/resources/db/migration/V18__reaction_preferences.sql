SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE character_reaction_preference (
    character_id UUID NOT NULL REFERENCES player_character (id) ON DELETE CASCADE,
    feature_key TEXT NOT NULL CHECK (char_length(feature_key) BETWEEN 1 AND 120),
    PRIMARY KEY (character_id, feature_key)
);
