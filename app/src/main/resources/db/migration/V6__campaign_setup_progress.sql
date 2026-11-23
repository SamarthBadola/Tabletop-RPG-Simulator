SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

ALTER TABLE campaign
    ADD COLUMN setup_step BIGINT NOT NULL DEFAULT 1 CHECK (setup_step BETWEEN 1 AND 6);
