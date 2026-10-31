-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

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
