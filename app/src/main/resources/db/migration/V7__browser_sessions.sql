SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE spring_session (
    primary_id TEXT PRIMARY KEY CHECK (char_length(primary_id) = 36),
    session_id TEXT NOT NULL UNIQUE CHECK (char_length(session_id) = 36),
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval BIGINT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name TEXT CHECK (char_length(principal_name) <= 100)
);

CREATE INDEX spring_session_expiry_idx ON spring_session (expiry_time);
CREATE INDEX spring_session_principal_idx ON spring_session (principal_name);

CREATE TABLE spring_session_attributes (
    session_primary_id TEXT NOT NULL REFERENCES spring_session (primary_id) ON DELETE CASCADE,
    attribute_name TEXT NOT NULL CHECK (char_length(attribute_name) <= 200),
    attribute_bytes BYTEA NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name)
);
