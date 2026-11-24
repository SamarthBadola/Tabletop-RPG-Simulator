SET LOCAL lock_timeout = '2s';
SET LOCAL statement_timeout = '10s';

-- The previous lists are strict subsets of the replacement checks.
ALTER TABLE campaign_action
    DROP CONSTRAINT campaign_action_kind_check,
    ADD CONSTRAINT campaign_action_kind_check CHECK (kind IN (
        'SETUP', 'SESSION_START', 'SESSION_PAUSE', 'SESSION_RESUME', 'SESSION_END',
        'CLOCK_ADVANCE', 'INVITE_REVOKE', 'SCENE_CREATE', 'SCENE_EDIT', 'SCENE_ACTIVATE',
        'SETTING_DECIDE', 'SETTING_RESET', 'SETTING_MODE', 'CHARACTER_ROLL', 'CHARACTER_CREATE',
        'CHARACTER_EDIT', 'CHARACTER_LEVEL_GRANT', 'CHARACTER_LEVEL_UP', 'PACK_UPGRADE',
        'ENCOUNTER_CREATE', 'SCENE_TEST', 'SCENE_REACTION', 'REST'
    )) NOT VALID;
ALTER TABLE campaign_event
    DROP CONSTRAINT campaign_event_type_check,
    ADD CONSTRAINT campaign_event_type_check CHECK (type IN (
        'SessionStarted', 'SessionPaused', 'SessionResumed', 'SessionEnded', 'ClockAdvanced',
        'InviteRevoked', 'SceneCreated', 'SceneEdited', 'SceneActivated', 'SettingDecided',
        'SettingModeChanged', 'AbilityScoresRolled', 'CharacterCreated', 'CharacterEdited',
        'CharacterLevelGranted', 'CharacterLeveledUp', 'ContentPackUpgraded',
        'SceneTestResolved', 'ReactionOpened', 'ActionCancelled',
        'RestStarted', 'RestInterrupted', 'RestCompleted', 'long_rest_no_benefit',
        'StableRecovered', 'DawnReached', 'EffectExpired', 'ResourceRecharged',
        'HitDieSpent', 'HitDiceRecovered', 'SongOfRestRolled', 'SpellSlotsRecovered',
        'ExhaustionReduced', 'HpRegained', 'TemporaryHitPointsEnded'
    )) NOT VALID;

ALTER TABLE player_character ADD COLUMN last_long_rest_end_seconds BIGINT;
ALTER TABLE player_character ADD CONSTRAINT player_character_last_long_rest_check
    CHECK (last_long_rest_end_seconds >= 0) NOT VALID;
