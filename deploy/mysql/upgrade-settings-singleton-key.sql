-- deploy/mysql/upgrade-settings-singleton-key.sql
-- Existing MySQL volumes: enforce the rmq_settings singleton invariant.
-- Fresh volumes receive settings_key and uk_settings_key from schema.sql.
--
-- This migration preserves the row the application historically selected (the lowest id)
-- and removes any additional rows that violate the singleton invariant. Back up first.
--
-- Run once on existing deployments:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-settings-singleton-key.sql

SET NAMES utf8mb4;

SET @column_exists := (
  SELECT COUNT(*) FROM information_schema.columns
   WHERE table_schema = DATABASE()
     AND table_name = 'rmq_settings'
     AND column_name = 'settings_key'
);
SET @column_sql := IF(@column_exists = 0,
    'ALTER TABLE rmq_settings ADD COLUMN settings_key VARCHAR(32) NOT NULL DEFAULT ''general'' COMMENT ''Settings singleton business key'' AFTER gmt_modified',
    'SELECT ''settings_key already exists'' AS msg');
PREPARE stmt FROM @column_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DELETE duplicate_settings FROM rmq_settings duplicate_settings
JOIN rmq_settings retained_settings
  ON duplicate_settings.settings_key = retained_settings.settings_key
 AND duplicate_settings.id > retained_settings.id;

SET @index_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'rmq_settings'
     AND index_name = 'uk_settings_key'
);
SET @index_sql := IF(@index_exists = 0,
    'ALTER TABLE rmq_settings ADD UNIQUE KEY uk_settings_key (settings_key)',
    'SELECT ''uk_settings_key already exists'' AS msg');
PREPARE stmt FROM @index_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
