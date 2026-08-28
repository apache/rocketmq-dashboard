-- server/src/main/resources/db/schema.sql
-- RocketMQ Studio 数据库 Schema（MySQL 8.0）
-- 此文件为唯一权威 DDL 来源，MyBatis-Plus Entity 与此保持同步
-- 注意：MyBatis-Plus 不自动建表，需通过此 SQL 初始化（docker-compose 挂载执行）
--
-- 表结构规范（所有表强制）：
--   id           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键'
--   gmt_create   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
--   gmt_modified datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间'
-- 禁止 created_at/updated_at，禁止 VARCHAR UUID 主键。

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

-- 1. Studio 控制台用户（区别于下方的 RocketMQ ACL 用户）
CREATE TABLE IF NOT EXISTS rmq_studio_user (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  username VARCHAR(128) NOT NULL COMMENT '用户名',
  password_hash VARCHAR(512) NOT NULL COMMENT 'PBKDF2 密码哈希，禁止存储明文',
  admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近修改密码时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_studio_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Studio bearer-token 会话。token_hash 为 SHA-256(token)，禁止存储 token 原文。
CREATE TABLE IF NOT EXISTS rmq_studio_session (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  user_id bigint(20) unsigned NOT NULL COMMENT '会话所属用户，引用 rmq_studio_user.id',
  token_hash CHAR(64) NOT NULL COMMENT 'SHA-256(session token)',
  expires_at DATETIME NOT NULL COMMENT '会话过期时间',
  revoked_at DATETIME NULL COMMENT '会话注销时间',
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_studio_session_token_hash (token_hash),
  INDEX idx_studio_session_user (user_id),
  INDEX idx_studio_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. NameServer / 集群地址注册表
CREATE TABLE IF NOT EXISTS rmq_nameserver (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  name VARCHAR(128) NOT NULL,
  namesrv_addr VARCHAR(512) NOT NULL COMMENT 'NameServer 地址，逗号分隔',
  k8s_namespace VARCHAR(128) DEFAULT NULL COMMENT 'K8s namespace（headless Service 部署场景）',
  k8s_id VARCHAR(128) DEFAULT NULL COMMENT 'k8s ID（K8s 部署场景填写，非 K8s 部署留空）',
  status VARCHAR(32) DEFAULT 'healthy',
  description TEXT,
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_nameserver_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 实例注册表（实例管理页的数据源，topic/group 按 instance_id 归属统计）
CREATE TABLE IF NOT EXISTS rmq_instance (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  type VARCHAR(32) NOT NULL COMMENT 'CLOUD/PROXY_LOCAL/PROXY_CLUSTER/DIRECT',
  endpoint VARCHAR(512) NOT NULL,
  vendor VARCHAR(32),
  cloud_instance_id VARCHAR(128),
  credential_id bigint(20) unsigned COMMENT '引用 rmq_cloud_credential.id',
  admin_credential_ref VARCHAR(128) COMMENT 'External Apache admin credential reference; no secret material',
  region_id VARCHAR(128),
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_instance_name (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Topic 管理记录（通过 Studio 创建/管理的 Topic 元数据）
CREATE TABLE IF NOT EXISTS rmq_instance_topic (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '归属实例 ID（rmq_instance.name；空字符串表示历史未归属记录）',
  name VARCHAR(255) NOT NULL,
  topic_type VARCHAR(32) DEFAULT 'NORMAL',
  read_queue_nums INT DEFAULT 8,
  write_queue_nums INT DEFAULT 8,
  perm INT DEFAULT 6,
  remark VARCHAR(255) COMMENT '业务用途备注',
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_cluster_instance_topic (cluster_id, instance_id, name),
  INDEX idx_topic_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Consumer Group 管理记录
CREATE TABLE IF NOT EXISTS rmq_instance_group (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(128) NOT NULL DEFAULT '' COMMENT '归属实例 ID（rmq_instance.name；空字符串表示历史未归属记录）',
  name VARCHAR(255) NOT NULL,
  consume_type VARCHAR(32) DEFAULT 'CONCURRENTLY',
  message_model VARCHAR(32) DEFAULT 'CLUSTERING',
  max_retry INT DEFAULT 16,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_cluster_instance_group (cluster_id, instance_id, name),
  INDEX idx_group_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. K8s 证书管理
CREATE TABLE IF NOT EXISTS rmq_k8s_certificate (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  k8s_id VARCHAR(128) NOT NULL COMMENT 'k8s ID',
  cluster VARCHAR(128),
  cert_type VARCHAR(32) DEFAULT 'TLS',
  issuer VARCHAR(256),
  not_before DATETIME,
  not_after DATETIME,
  status VARCHAR(32) DEFAULT 'valid',
  days_remaining INT,
  san TEXT COMMENT 'JSON array of SANs',
  cert_pem TEXT COMMENT 'PEM encoded certificate',
  key_pem TEXT COMMENT 'PEM encoded private key',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 消息查询记录
CREATE TABLE IF NOT EXISTS rmq_instance_message (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  query_type VARCHAR(32) NOT NULL COMMENT 'TOPIC/KEY/MSG_ID',
  topic VARCHAR(255),
  msg_id VARCHAR(128),
  tag VARCHAR(128),
  message_key VARCHAR(255),
  start_time BIGINT,
  end_time BIGINT,
  result_count INT DEFAULT 0,
  result_snapshot MEDIUMTEXT COMMENT '查询结果快照（不含消息体）JSON',
  cluster_id VARCHAR(255),
  queried_by VARCHAR(128),
  PRIMARY KEY (`id`),
  INDEX idx_message_query_cleanup (gmt_create, id),
  INDEX idx_message_query_owner_lookup (queried_by, cluster_id, gmt_create, id),
  INDEX idx_message_query_owner_type_lookup (queried_by, cluster_id, query_type, gmt_create, id),
  INDEX idx_topic (topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 消息轨迹查询记录
CREATE TABLE IF NOT EXISTS rmq_instance_trace (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  msg_id VARCHAR(128) NOT NULL,
  topic VARCHAR(255),
  node_count INT DEFAULT 0,
  consumer_count INT DEFAULT 0,
  cluster_id VARCHAR(255),
  queried_by VARCHAR(128),
  PRIMARY KEY (`id`),
  INDEX idx_msg_id (msg_id),
  INDEX idx_trace_query_cleanup (gmt_create, id),
  INDEX idx_trace_query_owner_lookup (queried_by, cluster_id, gmt_create, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 操作审计日志（所有写操作）
CREATE TABLE IF NOT EXISTS rmq_operation_audit (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  operation VARCHAR(64) NOT NULL COMMENT 'CREATE_TOPIC/DELETE_TOPIC/CREATE_GROUP/RESET_OFFSET/SEND_MESSAGE/UPDATE_CONFIG/...',
  resource_type VARCHAR(64) NOT NULL COMMENT 'TOPIC/GROUP/CLUSTER/CERT/SETTINGS',
  resource_name VARCHAR(255),
  cluster_id VARCHAR(64),
  detail TEXT COMMENT 'JSON: 操作详情/变更内容',
  result VARCHAR(16) DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
  error_message TEXT,
  operator VARCHAR(128),
  PRIMARY KEY (`id`),
  INDEX idx_operation_audit_cleanup (gmt_create, id),
  INDEX idx_resource (resource_type, resource_name),
  INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 通用设置（单行）
CREATE TABLE IF NOT EXISTS rmq_settings (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  settings_key VARCHAR(32) NOT NULL DEFAULT 'general' COMMENT '设置单例业务键',
  json TEXT NOT NULL COMMENT 'GeneralSettingsVO JSON',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_settings_key (settings_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 数据源配置
CREATE TABLE IF NOT EXISTS rmq_data_source (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  ds_key VARCHAR(64) NOT NULL COMMENT '对外业务键',
  json TEXT NOT NULL COMMENT 'DataSourceVO JSON',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_ds_key (ds_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 11. ACL 规则
CREATE TABLE IF NOT EXISTS rmq_acl_rule (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  principal VARCHAR(128) NOT NULL,
  resource VARCHAR(255) NOT NULL,
  resource_type VARCHAR(32) COMMENT 'Topic/Group/Cluster',
  resource_pattern VARCHAR(32) COMMENT 'LITERAL/PREFIX',
  actions VARCHAR(128) COMMENT '逗号分隔：PUB/SUB/ALL',
  decision VARCHAR(16) COMMENT 'ALLOW/DENY',
  scope VARCHAR(64) COMMENT '生效范围（集群名/实例 id）',
  acl_version VARCHAR(16) COMMENT '1.0/2.0',
  PRIMARY KEY (`id`),
  INDEX idx_principal (principal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 12. ACL 用户（secret_key 为 base64 编码后的密码，禁止明文存储）
CREATE TABLE IF NOT EXISTS rmq_acl_user (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  username VARCHAR(128) NOT NULL,
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL COMMENT 'base64 编码的密码',
  admin TINYINT(1) DEFAULT 0,
  clusters VARCHAR(1024) COMMENT '逗号分隔的集群/实例 id',
  white_remote_address VARCHAR(255) COMMENT 'plain access 账号 IP 白名单，空表示不限制',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_username (username),
  UNIQUE KEY uk_access_key (access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 13. 告警规则
CREATE TABLE IF NOT EXISTS rmq_alert_rule (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  name VARCHAR(128) NOT NULL,
  metric VARCHAR(128),
  operator VARCHAR(16),
  threshold DOUBLE,
  threshold_unit VARCHAR(32),
  duration VARCHAR(32),
  aggregation VARCHAR(16) NOT NULL DEFAULT 'LAST',
  window_seconds INT NOT NULL DEFAULT 0,
  channels VARCHAR(512) COMMENT '逗号分隔的通知渠道',
  enabled TINYINT(1) DEFAULT 1,
  last_triggered VARCHAR(64),
  description VARCHAR(512),
  broker_name VARCHAR(128),
  cluster_name VARCHAR(128),
  severity VARCHAR(32),
  domain VARCHAR(16) NOT NULL DEFAULT 'BUSINESS' COMMENT 'BUSINESS or CLUSTER alert rule domain',
  instance_id VARCHAR(128) COMMENT 'Studio instance scope required for native rule evaluation',
  consumer_group VARCHAR(255) COMMENT 'Optional consumer group selector for business metrics',
  topic VARCHAR(255) COMMENT 'Optional topic selector for topic backlog metrics',
  consecutive_samples INT NOT NULL DEFAULT 1 COMMENT 'Consecutive native samples required before firing',
  reminder_interval VARCHAR(32) NOT NULL DEFAULT '30m' COMMENT 'Repeat notification interval while unacknowledged',
  notification_template TEXT COMMENT 'Optional notification body template',
  semantic_fingerprint CHAR(64) NOT NULL COMMENT 'SHA-256 identity of the rule evaluation conditions',
  UNIQUE KEY uk_alert_rule_semantic_fingerprint (semantic_fingerprint),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. Native alert metric snapshots (short retention, managed by CollectorScheduler)
CREATE TABLE IF NOT EXISTS rmq_metric_snapshot (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `instance_id` VARCHAR(128) NOT NULL,
  `metric_key` VARCHAR(128) NOT NULL,
  `domain` VARCHAR(16) NOT NULL,
  `cluster_id` VARCHAR(128),
  `labels_hash` CHAR(64) NOT NULL,
  `labels_json` TEXT NOT NULL,
  `value` DOUBLE NULL,
  `availability` VARCHAR(16) NOT NULL,
  `collected_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  INDEX idx_metric_snapshot_lookup (`instance_id`, `metric_key`, `collected_at`),
  INDEX idx_metric_snapshot_scope_cluster (`instance_id`, `metric_key`, `domain`, `labels_hash`, `cluster_id`, `availability`, `collected_at`),
  INDEX idx_metric_snapshot_scope_global (`instance_id`, `metric_key`, `domain`, `labels_hash`, `availability`, `collected_at`),
  INDEX idx_metric_snapshot_retention (`collected_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_alert_collection_lease (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `lease_name` VARCHAR(128) NOT NULL,
  `holder_id` VARCHAR(64) NOT NULL,
  `expires_at` DATETIME NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_alert_collection_lease_name (`lease_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_alert_state (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `rule_id` bigint(20) unsigned NOT NULL,
  `fingerprint` CHAR(64) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `consecutive_hits` INT NOT NULL DEFAULT 0,
  `current_value` DOUBLE NULL,
  `first_pending_at` DATETIME NULL,
  `fired_at` DATETIME NULL,
  `last_notified_at` DATETIME NULL,
  `resolved_at` DATETIME NULL,
  `version` INT NOT NULL DEFAULT 0,
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_alert_state_rule_fingerprint (`rule_id`, `fingerprint`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_alert_silence (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `domain` VARCHAR(16) NULL,
  `rule_id` bigint(20) unsigned NULL,
  `instance_id` VARCHAR(128) NULL,
  `labels_json` TEXT NULL,
  `starts_at` DATETIME NOT NULL,
  `ends_at` DATETIME NOT NULL,
  `reason` VARCHAR(512) NULL,
  `created_by` VARCHAR(128) NOT NULL,
  PRIMARY KEY (`id`),
  INDEX idx_alert_silence_active (`starts_at`, `ends_at`),
  INDEX idx_alert_silence_expiry (`ends_at`, `starts_at`),
  INDEX idx_alert_silence_scope (`domain`, `rule_id`, `instance_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_alert_notification_outbox (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT,
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `alert_id` bigint(20) unsigned NOT NULL,
  `channel` VARCHAR(32) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `attempt_count` INT NOT NULL DEFAULT 0,
  `next_attempt_at` DATETIME NOT NULL,
  `sending_started_at` DATETIME NULL,
  `claim_token` VARCHAR(64) NULL,
  `last_error` VARCHAR(1000) NULL,
  `message_content` TEXT NULL COMMENT 'Rendered notification body snapshot',
  `delivered_at` DATETIME NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_alert_notification_outbox (`alert_id`, `channel`),
  INDEX idx_alert_notification_ready (`status`, `next_attempt_at`),
  INDEX idx_alert_notification_delivered_retention (`status`, `delivered_at`),
  INDEX idx_alert_notification_modified_retention (`status`, `gmt_modified`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. 系统告警事件
CREATE TABLE IF NOT EXISTS rmq_system_alert (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  level VARCHAR(32),
  title VARCHAR(255),
  description TEXT,
  time DATETIME,
  acknowledged TINYINT(1) DEFAULT 0,
  acknowledged_by VARCHAR(128),
  acknowledged_at DATETIME,
  domain VARCHAR(16),
  rule_id bigint(20) unsigned,
  fingerprint CHAR(64),
  transition VARCHAR(16),
  instance_id VARCHAR(128),
  current_value DOUBLE,
  notification_suppressed TINYINT(1) NOT NULL DEFAULT 0,
  suppression_cause_alert_id bigint(20) unsigned,
  suppression_reason VARCHAR(512),
  labels_json TEXT,
  PRIMARY KEY (`id`),
  INDEX idx_level (level),
  INDEX idx_acknowledged (acknowledged),
  INDEX idx_system_alert_domain_time (domain, time),
  INDEX idx_system_alert_feed (domain, instance_id, transition, time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 15. Cloud provider credentials (secret_key is base64-encoded and never seeded).
CREATE TABLE IF NOT EXISTS rmq_cloud_credential (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  name VARCHAR(128) NOT NULL COMMENT 'Credential display name',
  vendor VARCHAR(32) NOT NULL COMMENT 'ALIYUN/TENCENT',
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL COMMENT 'Base64-encoded secret key',
  remark VARCHAR(255),
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_vendor_access_key (vendor, access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Idempotent upgrades for databases created before the corresponding CREATE statements
-- were widened. Safe to re-run: on fresh databases the columns already match, and on
-- existing databases the MODIFY below only grows the column width. Usernames may be up
-- to 128 characters (see AuthService), so columns that store the acting username — the
-- query-history owner columns and the audit operator — must match.
ALTER TABLE rmq_instance_message MODIFY queried_by VARCHAR(128);
ALTER TABLE rmq_instance_trace MODIFY queried_by VARCHAR(128);
ALTER TABLE rmq_operation_audit MODIFY operator VARCHAR(128);

-- Existing deployments are upgraded by AlertSchemaMigration after the application
-- connects, because this schema is also parsed by H2 in the development profile.
