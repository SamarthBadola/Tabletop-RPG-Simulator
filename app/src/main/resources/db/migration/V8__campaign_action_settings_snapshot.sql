SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

ALTER TABLE campaign_action
    ADD COLUMN settings_read JSONB NOT NULL DEFAULT '{}'::JSONB
    CHECK (jsonb_typeof(settings_read) = 'object');
