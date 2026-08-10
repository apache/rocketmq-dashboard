-- Flyway V1: RocketMQ Studio PostgreSQL schema
-- Equivalent to the Studio-owned MySQL schema; RocketMQ runtime data remains external.

-- 1. NameServer / 集群地址注册表
CREATE TABLE IF NOT EXISTS rmq_nameserver (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  namesrv_addr VARCHAR(512) NOT NULL,
  cluster_type VARCHAR(32) DEFAULT 'V5_PROXY_CLUSTER',
  status VARCHAR(32) DEFAULT 'healthy',
  description TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
-- 2. 实例注册表（实例管理页的数据源，topic/group 按 instance_id 归属统计）
CREATE TABLE IF NOT EXISTS rmq_instance (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  type VARCHAR(32) NOT NULL,
  endpoint VARCHAR(512) NOT NULL,
  vendor VARCHAR(32) NOT NULL DEFAULT 'APACHE',
  cloud_instance_id VARCHAR(128),
  credential_id VARCHAR(64),
  region_id VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 3. Topic 管理记录（通过 Studio 创建/管理的 Topic 元数据）
CREATE TABLE IF NOT EXISTS rmq_topic (
  id BIGSERIAL PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64),
  name VARCHAR(255) NOT NULL,
  topic_type VARCHAR(32) DEFAULT 'NORMAL',
  read_queue_nums INT DEFAULT 8,
  write_queue_nums INT DEFAULT 8,
  perm INT DEFAULT 6,
  remark VARCHAR(255),
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cluster_topic UNIQUE (cluster_id, name)
);

-- 4. Consumer Group 管理记录
CREATE TABLE IF NOT EXISTS rmq_group (
  id BIGSERIAL PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64),
  name VARCHAR(255) NOT NULL,
  consume_type VARCHAR(32) DEFAULT 'CONCURRENTLY',
  message_model VARCHAR(32) DEFAULT 'CLUSTERING',
  max_retry INT DEFAULT 16,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cluster_group UNIQUE (cluster_id, name)
);

-- 5. K8s 证书管理
CREATE TABLE IF NOT EXISTS rmq_k8s_certificate (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  namespace VARCHAR(128),
  cluster VARCHAR(128),
  cert_type VARCHAR(32) DEFAULT 'TLS',
  issuer VARCHAR(256),
  not_before TIMESTAMP,
  not_after TIMESTAMP,
  status VARCHAR(32) DEFAULT 'valid',
  days_remaining INT,
  san TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 6. 消息查询记录
CREATE TABLE IF NOT EXISTS rmq_message_query (
  id BIGSERIAL PRIMARY KEY,
  query_type VARCHAR(32) NOT NULL,
  topic VARCHAR(255),
  msg_id VARCHAR(128),
  tag VARCHAR(128),
  message_key VARCHAR(255),
  start_time BIGINT,
  end_time BIGINT,
  result_count INT DEFAULT 0,
  cluster_id VARCHAR(255),
  queried_by VARCHAR(64),
  queried_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 7. 消息轨迹查询记录
CREATE TABLE IF NOT EXISTS rmq_trace_query (
  id BIGSERIAL PRIMARY KEY,
  msg_id VARCHAR(128) NOT NULL,
  topic VARCHAR(255),
  node_count INT DEFAULT 0,
  consumer_count INT DEFAULT 0,
  cluster_id VARCHAR(255),
  queried_by VARCHAR(64),
  queried_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 8. 操作审计日志（所有写操作）
CREATE TABLE IF NOT EXISTS rmq_operation_audit (
  id BIGSERIAL PRIMARY KEY,
  operation VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_name VARCHAR(255),
  cluster_id VARCHAR(64),
  detail TEXT,
  result VARCHAR(16) DEFAULT 'SUCCESS',
  error_message TEXT,
  operator VARCHAR(64),
  operated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 9. 通用设置（单行）
CREATE TABLE IF NOT EXISTS rmq_settings (
  id VARCHAR(16) PRIMARY KEY DEFAULT 'singleton',
  json TEXT NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 10. 数据源配置
CREATE TABLE IF NOT EXISTS rmq_data_source (
  ds_key VARCHAR(64) PRIMARY KEY,
  json TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 11. ACL 规则
CREATE TABLE IF NOT EXISTS rmq_acl_rule (
  id VARCHAR(64) PRIMARY KEY,
  principal VARCHAR(128) NOT NULL,
  resource VARCHAR(255) NOT NULL,
  resource_type VARCHAR(32),
  resource_pattern VARCHAR(32),
  actions VARCHAR(128),
  decision VARCHAR(16),
  scope VARCHAR(64),
  acl_version VARCHAR(16),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 12. ACL 用户（secret_key 为 base64 编码后的密码，禁止明文存储）
CREATE TABLE IF NOT EXISTS rmq_acl_user (
  id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(128) NOT NULL,
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL,
  admin BOOLEAN DEFAULT FALSE,
  clusters VARCHAR(1024),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_username UNIQUE (username)
);

-- 13. 告警规则
CREATE TABLE IF NOT EXISTS rmq_alert_rule (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  metric VARCHAR(128),
  operator VARCHAR(16),
  threshold DOUBLE PRECISION,
  threshold_unit VARCHAR(32),
  duration VARCHAR(32),
  channels VARCHAR(512),
  enabled BOOLEAN DEFAULT TRUE,
  last_triggered VARCHAR(64),
  description VARCHAR(512),
  broker_name VARCHAR(128),
  cluster_name VARCHAR(128),
  severity VARCHAR(32),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 14. 系统告警事件
CREATE TABLE IF NOT EXISTS rmq_system_alert (
  id VARCHAR(64) PRIMARY KEY,
  level VARCHAR(32),
  title VARCHAR(255),
  description TEXT,
  time TIMESTAMP,
  acknowledged BOOLEAN DEFAULT FALSE,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 15. Cloud provider credentials (secret_key is base64-encoded and never seeded).
CREATE TABLE IF NOT EXISTS rmq_cloud_credential (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  vendor VARCHAR(32) NOT NULL,
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL,
  remark VARCHAR(255),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_vendor_access_key UNIQUE (vendor, access_key)
);

CREATE INDEX IF NOT EXISTS idx_instance_rmq_topic ON rmq_topic (instance_id);
CREATE INDEX IF NOT EXISTS idx_instance_rmq_group ON rmq_group (instance_id);
CREATE INDEX IF NOT EXISTS idx_queried_at_rmq_message_query ON rmq_message_query (queried_at);
CREATE INDEX IF NOT EXISTS idx_topic_rmq_message_query ON rmq_message_query (topic);
CREATE INDEX IF NOT EXISTS idx_msg_id_rmq_trace_query ON rmq_trace_query (msg_id);
CREATE INDEX IF NOT EXISTS idx_queried_at_rmq_trace_query ON rmq_trace_query (queried_at);
CREATE INDEX IF NOT EXISTS idx_operated_at_rmq_operation_audit ON rmq_operation_audit (operated_at);
CREATE INDEX IF NOT EXISTS idx_resource_rmq_operation_audit ON rmq_operation_audit (resource_type, resource_name);
CREATE INDEX IF NOT EXISTS idx_operation_rmq_operation_audit ON rmq_operation_audit (operation);
CREATE INDEX IF NOT EXISTS idx_principal_rmq_acl_rule ON rmq_acl_rule (principal);
CREATE INDEX IF NOT EXISTS idx_level_rmq_system_alert ON rmq_system_alert (level);
CREATE INDEX IF NOT EXISTS idx_acknowledged_rmq_system_alert ON rmq_system_alert (acknowledged);
