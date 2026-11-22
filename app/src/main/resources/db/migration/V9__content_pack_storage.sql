SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

CREATE TABLE content_pack (
    id UUID PRIMARY KEY,
    pack_key TEXT NOT NULL CHECK (pack_key ~ '^[a-z0-9][a-z0-9.-]{1,39}$'),
    version TEXT NOT NULL CHECK (version ~ '^[0-9]+[.][0-9]+[.][0-9]+$'),
    hash TEXT NOT NULL UNIQUE CHECK (hash ~ '^sha256:[a-f0-9]{64}$'),
    ruleset TEXT NOT NULL,
    manifest JSONB NOT NULL CHECK (
        jsonb_typeof(manifest) = 'object'
        AND (manifest ->> 'id') IS NOT DISTINCT FROM pack_key
        AND (manifest ->> 'version') IS NOT DISTINCT FROM version
        AND (manifest ->> 'ruleset') IS NOT DISTINCT FROM ruleset
    ),
    license TEXT GENERATED ALWAYS AS (manifest #>> '{license,id}') STORED NOT NULL,
    notice TEXT NOT NULL,
    warnings JSONB NOT NULL DEFAULT '[]' CHECK (jsonb_typeof(warnings) = 'array'),
    state TEXT NOT NULL DEFAULT 'loaded' CHECK (state IN ('loaded', 'archived')),
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    loaded_by UUID REFERENCES app_user (id) ON DELETE RESTRICT,
    UNIQUE (pack_key, version),
    UNIQUE (id, pack_key),
    UNIQUE (id, ruleset, license)
);

CREATE INDEX content_pack_loaded_by_idx ON content_pack (loaded_by);

CREATE TABLE content_definition (
    id UUID PRIMARY KEY,
    pack_id UUID NOT NULL,
    kind TEXT NOT NULL CHECK (kind IN (
        'class', 'subclass', 'species', 'subspecies', 'background', 'feature', 'feat',
        'resource', 'ruleTable', 'condition', 'spell', 'item', 'monster', 'hazard',
        'poison', 'affliction', 'afflictionTable'
    )),
    key TEXT NOT NULL,
    name TEXT NOT NULL CHECK (char_length(name) BETWEEN 1 AND 120),
    ruleset TEXT NOT NULL,
    license TEXT NOT NULL,
    hash TEXT NOT NULL CHECK (hash ~ '^sha256:[a-f0-9]{64}$'),
    overrides TEXT,
    body JSONB NOT NULL CHECK (jsonb_typeof(body) = 'object'),
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    loaded_by UUID REFERENCES app_user (id) ON DELETE RESTRICT,
    UNIQUE (pack_id, kind, key),
    CONSTRAINT content_definition_pack_fk FOREIGN KEY (pack_id, ruleset, license)
        REFERENCES content_pack (id, ruleset, license) ON DELETE CASCADE
);

CREATE INDEX content_definition_kind_key_idx ON content_definition (kind, key);
CREATE INDEX content_definition_kind_name_idx ON content_definition (kind, lower(name));
CREATE INDEX content_definition_loaded_by_idx ON content_definition (loaded_by);

CREATE TABLE content_pack_dependency (
    pack_id UUID NOT NULL REFERENCES content_pack (id) ON DELETE CASCADE,
    dependency_id UUID NOT NULL,
    loaded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    loaded_by UUID REFERENCES app_user (id) ON DELETE RESTRICT,
    PRIMARY KEY (pack_id, dependency_id),
    CHECK (pack_id <> dependency_id),
    CONSTRAINT content_pack_dependency_dependency_fk FOREIGN KEY (dependency_id)
        REFERENCES content_pack (id) ON DELETE RESTRICT
);

CREATE INDEX content_pack_dependency_dependency_idx ON content_pack_dependency (dependency_id);
CREATE INDEX content_pack_dependency_loaded_by_idx ON content_pack_dependency (loaded_by);

CREATE TABLE campaign_pack (
    campaign_id UUID NOT NULL REFERENCES campaign (id) ON DELETE CASCADE,
    pack_id UUID NOT NULL,
    pack_key TEXT NOT NULL,
    position BIGINT NOT NULL CHECK (position >= 0),
    enabled_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    enabled_by UUID REFERENCES app_user (id) ON DELETE RESTRICT,
    PRIMARY KEY (campaign_id, pack_id),
    CONSTRAINT campaign_pack_one_version UNIQUE (campaign_id, pack_key),
    CONSTRAINT campaign_pack_position UNIQUE (campaign_id, position) DEFERRABLE INITIALLY IMMEDIATE,
    CONSTRAINT campaign_pack_pack_fk FOREIGN KEY (pack_id, pack_key)
        REFERENCES content_pack (id, pack_key) ON DELETE RESTRICT
);

CREATE INDEX campaign_pack_pack_idx ON campaign_pack (pack_id);
CREATE INDEX campaign_pack_enabled_by_idx ON campaign_pack (enabled_by);

CREATE FUNCTION reject_content_definition_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'content definition is immutable' USING ERRCODE = '23514';
END;
$$;

CREATE TRIGGER content_definition_immutable
    BEFORE UPDATE ON content_definition
    FOR EACH ROW EXECUTE FUNCTION reject_content_definition_update();

CREATE FUNCTION reject_content_pack_update() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    -- The generated license is checked by the immutable manifest that supplies it.
    IF (to_jsonb(NEW) - 'state' - 'license') IS DISTINCT FROM
       (to_jsonb(OLD) - 'state' - 'license') THEN
        RAISE EXCEPTION 'content pack is immutable except for state' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER content_pack_immutable
    BEFORE UPDATE ON content_pack
    FOR EACH ROW EXECUTE FUNCTION reject_content_pack_update();

CREATE FUNCTION reject_independent_content_delete() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF EXISTS (SELECT 1 FROM content_pack WHERE id = OLD.pack_id) THEN
        RAISE EXCEPTION 'loaded content can only be deleted with its pack' USING ERRCODE = '23514';
    END IF;
    RETURN OLD;
END;
$$;

CREATE TRIGGER content_definition_delete_guard
    BEFORE DELETE ON content_definition
    FOR EACH ROW EXECUTE FUNCTION reject_independent_content_delete();

CREATE TRIGGER content_dependency_immutable
    BEFORE UPDATE ON content_pack_dependency
    FOR EACH ROW EXECUTE FUNCTION reject_content_definition_update();

CREATE TRIGGER content_dependency_delete_guard
    BEFORE DELETE ON content_pack_dependency
    FOR EACH ROW EXECUTE FUNCTION reject_independent_content_delete();
