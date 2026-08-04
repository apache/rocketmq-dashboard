-- RocketMQ Studio schema for PostgreSQL 16+.
-- This mirrors db/schema.sql without MySQL-only clauses or sample data.

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

CREATE TABLE IF NOT EXISTS rmq_instance (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  type VARCHAR(32) NOT NULL,
  endpoint VARCHAR(512) NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rmq_topic (
  id BIGSERIAL PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64),
  name VARCHAR(255) NOT NULL,
  topic_type VARCHAR(32) DEFAULT 'NORMAL',
  read_queue_nums INTEGER DEFAULT 8,
  write_queue_nums INTEGER DEFAULT 8,
  perm INTEGER DEFAULT 6,
  remark VARCHAR(255),
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cluster_topic UNIQUE (cluster_id, name)
);
CREATE INDEX IF NOT EXISTS idx_topic_instance ON rmq_topic (instance_id);

CREATE TABLE IF NOT EXISTS rmq_group (
  id BIGSERIAL PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64),
  name VARCHAR(255) NOT NULL,
  consume_type VARCHAR(32) DEFAULT 'CONCURRENTLY',
  message_model VARCHAR(32) DEFAULT 'CLUSTERING',
  max_retry INTEGER DEFAULT 16,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_cluster_group UNIQUE (cluster_id, name)
);
CREATE INDEX IF NOT EXISTS idx_group_instance ON rmq_group (instance_id);

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
  days_remaining INTEGER,
  san TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rmq_message_query (
  id BIGSERIAL PRIMARY KEY,
  query_type VARCHAR(32) NOT NULL,
  topic VARCHAR(255),
  msg_id VARCHAR(128),
  tag VARCHAR(128),
  message_key VARCHAR(255),
  start_time BIGINT,
  end_time BIGINT,
  result_count INTEGER DEFAULT 0,
  queried_by VARCHAR(64),
  queried_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_message_query_queried_at ON rmq_message_query (queried_at);
CREATE INDEX IF NOT EXISTS idx_message_query_topic ON rmq_message_query (topic);

CREATE TABLE IF NOT EXISTS rmq_trace_query (
  id BIGSERIAL PRIMARY KEY,
  msg_id VARCHAR(128) NOT NULL,
  topic VARCHAR(255),
  node_count INTEGER DEFAULT 0,
  consumer_count INTEGER DEFAULT 0,
  queried_by VARCHAR(64),
  queried_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX IF NOT EXISTS idx_trace_query_msg_id ON rmq_trace_query (msg_id);
CREATE INDEX IF NOT EXISTS idx_trace_query_queried_at ON rmq_trace_query (queried_at);

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
CREATE INDEX IF NOT EXISTS idx_operation_audit_operated_at ON rmq_operation_audit (operated_at);
CREATE INDEX IF NOT EXISTS idx_operation_audit_resource ON rmq_operation_audit (resource_type, resource_name);
CREATE INDEX IF NOT EXISTS idx_operation_audit_operation ON rmq_operation_audit (operation);

CREATE TABLE IF NOT EXISTS rmq_settings (
  id VARCHAR(16) PRIMARY KEY DEFAULT 'singleton',
  json TEXT NOT NULL,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rmq_data_source (
  ds_key VARCHAR(64) PRIMARY KEY,
  json TEXT NOT NULL,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

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
CREATE INDEX IF NOT EXISTS idx_acl_rule_principal ON rmq_acl_rule (principal);

CREATE TABLE IF NOT EXISTS rmq_acl_user (
  id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(128) NOT NULL,
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL,
  admin BOOLEAN DEFAULT FALSE,
  clusters VARCHAR(1024),
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT uk_acl_user_username UNIQUE (username)
);

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
CREATE INDEX IF NOT EXISTS idx_system_alert_level ON rmq_system_alert (level);
CREATE INDEX IF NOT EXISTS idx_system_alert_acknowledged ON rmq_system_alert (acknowledged);
