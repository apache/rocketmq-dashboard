-- Upgrade databases created from the first numeric-ID RocketMQ Studio schema.
-- V1 uses CREATE TABLE IF NOT EXISTS, so it also creates tables introduced after
-- that schema (for example Studio users and sessions) before these alterations run.

SET @schema_name := DATABASE();

-- The automatic upgrade boundary starts with the numeric-ID schema introduced by
-- #2317. Abort before changing anything when a pre-standardization VARCHAR-ID
-- volume is detected; that layout needs an explicit data conversion plan.
-- V1 may have added newly introduced tables, but no legacy identifiers or rows
-- have been rewritten at this point.
SET @sql := (SELECT IF(MAX(data_type = 'bigint') = 1, 'SELECT 1',
  'SELECT * FROM rocketmq_studio_upgrade_requires_numeric_id_schema')
  FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'rmq_instance' AND column_name = 'id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- NameServer records acquired Kubernetes discovery metadata after numeric IDs landed.
SET @sql := (SELECT IF(COUNT(*) = 0,
  "ALTER TABLE rmq_nameserver ADD COLUMN k8s_namespace VARCHAR(128) DEFAULT NULL COMMENT 'K8s namespace (headless Service deployments)' AFTER namesrv_addr",
  'SELECT 1') FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'rmq_nameserver' AND column_name = 'k8s_namespace');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(COUNT(*) = 0,
  "ALTER TABLE rmq_nameserver ADD COLUMN k8s_id VARCHAR(128) DEFAULT NULL COMMENT 'K8s ID; empty for non-K8s deployments' AFTER k8s_namespace",
  'SELECT 1') FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'rmq_nameserver' AND column_name = 'k8s_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Topic ownership changed from the numeric instance primary key to the stable
-- instance name. Convert existing references before replacing the unique key.
ALTER TABLE rmq_instance_topic MODIFY COLUMN instance_id VARCHAR(128) NULL;
UPDATE rmq_instance_topic topic
  JOIN rmq_instance instance ON CAST(topic.instance_id AS UNSIGNED) = instance.id
  SET topic.instance_id = instance.name
  WHERE topic.instance_id REGEXP '^[0-9]+$';
UPDATE rmq_instance_topic SET instance_id = '' WHERE instance_id IS NULL;
ALTER TABLE rmq_instance_topic
  MODIFY COLUMN instance_id VARCHAR(128) NOT NULL DEFAULT ''
    COMMENT 'Managed instance name; empty for historical unscoped records';
SET @sql := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE rmq_instance_topic DROP INDEX uk_cluster_topic', 'SELECT 1')
  FROM information_schema.statistics WHERE table_schema = @schema_name
  AND table_name = 'rmq_instance_topic' AND index_name = 'uk_cluster_topic');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE rmq_instance_topic ADD UNIQUE INDEX uk_cluster_instance_topic (cluster_id, instance_id, name)',
  'SELECT 1') FROM information_schema.statistics WHERE table_schema = @schema_name
  AND table_name = 'rmq_instance_topic' AND index_name = 'uk_cluster_instance_topic');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Consumer-group ownership follows the same stable-name model as topics.
ALTER TABLE rmq_instance_group MODIFY COLUMN instance_id VARCHAR(128) NULL;
UPDATE rmq_instance_group consumer_group
  JOIN rmq_instance instance ON CAST(consumer_group.instance_id AS UNSIGNED) = instance.id
  SET consumer_group.instance_id = instance.name
  WHERE consumer_group.instance_id REGEXP '^[0-9]+$';
UPDATE rmq_instance_group SET instance_id = '' WHERE instance_id IS NULL;
ALTER TABLE rmq_instance_group
  MODIFY COLUMN instance_id VARCHAR(128) NOT NULL DEFAULT ''
    COMMENT 'Managed instance name; empty for historical unscoped records';
SET @sql := (SELECT IF(COUNT(*) > 0,
  'ALTER TABLE rmq_instance_group DROP INDEX uk_cluster_group', 'SELECT 1')
  FROM information_schema.statistics WHERE table_schema = @schema_name
  AND table_name = 'rmq_instance_group' AND index_name = 'uk_cluster_group');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE rmq_instance_group ADD UNIQUE INDEX uk_cluster_instance_group (cluster_id, instance_id, name)',
  'SELECT 1') FROM information_schema.statistics WHERE table_schema = @schema_name
  AND table_name = 'rmq_instance_group' AND index_name = 'uk_cluster_instance_group');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Certificate identity was renamed from a display name to the Kubernetes ID.
SET @sql := (SELECT CASE
  WHEN SUM(column_name = 'k8s_id') > 0 THEN 'SELECT 1'
  WHEN SUM(column_name = 'name') > 0
    THEN 'ALTER TABLE rmq_k8s_certificate RENAME COLUMN name TO k8s_id'
  ELSE 'ALTER TABLE rmq_k8s_certificate ADD COLUMN k8s_id VARCHAR(128) NOT NULL' END
  FROM information_schema.columns WHERE table_schema = @schema_name
  AND table_name = 'rmq_k8s_certificate');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- Usernames can be 128 characters, so actor fields must use the same width.
ALTER TABLE rmq_instance_message MODIFY COLUMN queried_by VARCHAR(128);
ALTER TABLE rmq_instance_trace MODIFY COLUMN queried_by VARCHAR(128);
ALTER TABLE rmq_operation_audit MODIFY COLUMN operator VARCHAR(128);

-- Access keys are login identifiers and therefore must be unique as well.
SET @sql := (SELECT IF(COUNT(*) = 0,
  'ALTER TABLE rmq_acl_user ADD UNIQUE INDEX uk_access_key (access_key)', 'SELECT 1')
  FROM information_schema.statistics WHERE table_schema = @schema_name
  AND table_name = 'rmq_acl_user' AND index_name = 'uk_access_key');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;
