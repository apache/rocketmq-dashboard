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
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. 实例注册表（实例管理页的数据源，topic/group 按 instance_id 归属统计）
CREATE TABLE IF NOT EXISTS rmq_instance (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  type VARCHAR(32) NOT NULL COMMENT 'PROXY/PROXY_LOCAL/PROXY_CLUSTER/DIRECT',
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
  instance_id VARCHAR(128) COMMENT '归属实例 ID（rmq_instance.name，全局唯一）',
  name VARCHAR(255) NOT NULL,
  topic_type VARCHAR(32) DEFAULT 'NORMAL',
  read_queue_nums INT DEFAULT 8,
  write_queue_nums INT DEFAULT 8,
  perm INT DEFAULT 6,
  remark VARCHAR(255) COMMENT '业务用途备注',
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_cluster_topic (cluster_id, name),
  INDEX idx_topic_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Consumer Group 管理记录
CREATE TABLE IF NOT EXISTS rmq_instance_group (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(128) COMMENT '归属实例 ID（rmq_instance.name，全局唯一）',
  name VARCHAR(255) NOT NULL,
  consume_type VARCHAR(32) DEFAULT 'CONCURRENTLY',
  message_model VARCHAR(32) DEFAULT 'CLUSTERING',
  max_retry INT DEFAULT 16,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_cluster_group (cluster_id, name),
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
  cluster_id VARCHAR(255),
  queried_by VARCHAR(64),
  PRIMARY KEY (`id`),
  INDEX idx_message_query_gmt_create (gmt_create),
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
  queried_by VARCHAR(64),
  PRIMARY KEY (`id`),
  INDEX idx_msg_id (msg_id),
  INDEX idx_trace_query_gmt_create (gmt_create)
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
  operator VARCHAR(64),
  PRIMARY KEY (`id`),
  INDEX idx_gmt_create (gmt_create),
  INDEX idx_resource (resource_type, resource_name),
  INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 通用设置（单行）
CREATE TABLE IF NOT EXISTS rmq_settings (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  json TEXT NOT NULL COMMENT 'GeneralSettingsVO JSON',
  PRIMARY KEY (`id`)
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
  UNIQUE KEY uk_username (username)
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
  channels VARCHAR(512) COMMENT '逗号分隔的通知渠道',
  enabled TINYINT(1) DEFAULT 1,
  last_triggered VARCHAR(64),
  description VARCHAR(512),
  broker_name VARCHAR(128),
  cluster_name VARCHAR(128),
  severity VARCHAR(32),
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 14. 系统告警事件
CREATE TABLE IF NOT EXISTS rmq_system_alert (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  level VARCHAR(32),
  title VARCHAR(255),
  description TEXT,
  time DATETIME,
  acknowledged TINYINT(1) DEFAULT 0,
  PRIMARY KEY (`id`),
  INDEX idx_level (level),
  INDEX idx_acknowledged (acknowledged)
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
