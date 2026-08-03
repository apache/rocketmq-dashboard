-- server/src/main/resources/db/schema.sql
-- RocketMQ Studio 数据库 Schema（MySQL 8.0）
-- 此文件为唯一权威 DDL 来源，MyBatis-Plus Entity 与此保持同步
-- 注意：MyBatis-Plus 不自动建表，需通过此 SQL 初始化（docker-compose 挂载执行）

-- 1. NameServer / 集群地址注册表
CREATE TABLE IF NOT EXISTS rmq_nameserver (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  namesrv_addr VARCHAR(512) NOT NULL COMMENT 'NameServer 地址，逗号分隔',
  cluster_type VARCHAR(32) DEFAULT 'V5_PROXY_CLUSTER',
  status VARCHAR(32) DEFAULT 'healthy',
  description TEXT,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Topic 管理记录（通过 Studio 创建/管理的 Topic 元数据）
CREATE TABLE IF NOT EXISTS rmq_topic (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  topic_type VARCHAR(32) DEFAULT 'NORMAL',
  read_queue_nums INT DEFAULT 8,
  write_queue_nums INT DEFAULT 8,
  perm INT DEFAULT 6,
  remark VARCHAR(255) COMMENT '业务用途备注',
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cluster_topic (cluster_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Consumer Group 管理记录
CREATE TABLE IF NOT EXISTS rmq_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  name VARCHAR(255) NOT NULL,
  consume_type VARCHAR(32) DEFAULT 'CONCURRENTLY',
  message_model VARCHAR(32) DEFAULT 'CLUSTERING',
  max_retry INT DEFAULT 16,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cluster_group (cluster_id, name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. K8s 证书管理
CREATE TABLE IF NOT EXISTS rmq_k8s_certificate (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  namespace VARCHAR(128),
  cluster VARCHAR(128),
  cert_type VARCHAR(32) DEFAULT 'TLS',
  issuer VARCHAR(256),
  not_before DATETIME,
  not_after DATETIME,
  status VARCHAR(32) DEFAULT 'valid',
  days_remaining INT,
  san TEXT COMMENT 'JSON array of SANs',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. 消息查询记录
CREATE TABLE IF NOT EXISTS rmq_message_query (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  query_type VARCHAR(32) NOT NULL COMMENT 'TOPIC/KEY/MSG_ID',
  topic VARCHAR(255),
  msg_id VARCHAR(128),
  tag VARCHAR(128),
  message_key VARCHAR(255),
  start_time BIGINT,
  end_time BIGINT,
  result_count INT DEFAULT 0,
  queried_by VARCHAR(64),
  queried_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_queried_at (queried_at),
  INDEX idx_topic (topic)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 6. 消息轨迹查询记录
CREATE TABLE IF NOT EXISTS rmq_trace_query (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  msg_id VARCHAR(128) NOT NULL,
  topic VARCHAR(255),
  node_count INT DEFAULT 0,
  consumer_count INT DEFAULT 0,
  queried_by VARCHAR(64),
  queried_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_msg_id (msg_id),
  INDEX idx_queried_at (queried_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 7. 操作审计日志（所有写操作）
CREATE TABLE IF NOT EXISTS rmq_operation_audit (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  operation VARCHAR(64) NOT NULL COMMENT 'CREATE_TOPIC/DELETE_TOPIC/CREATE_GROUP/RESET_OFFSET/SEND_MESSAGE/UPDATE_CONFIG/...',
  resource_type VARCHAR(64) NOT NULL COMMENT 'TOPIC/GROUP/CLUSTER/CERT/SETTINGS',
  resource_name VARCHAR(255),
  cluster_id VARCHAR(64),
  detail TEXT COMMENT 'JSON: 操作详情/变更内容',
  result VARCHAR(16) DEFAULT 'SUCCESS' COMMENT 'SUCCESS/FAILED',
  error_message TEXT,
  operator VARCHAR(64),
  operated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_operated_at (operated_at),
  INDEX idx_resource (resource_type, resource_name),
  INDEX idx_operation (operation)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 8. 通用设置（单行）
CREATE TABLE IF NOT EXISTS rmq_settings (
  id VARCHAR(16) PRIMARY KEY DEFAULT 'singleton',
  json TEXT NOT NULL COMMENT 'GeneralSettingsVO JSON',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 9. 数据源配置
CREATE TABLE IF NOT EXISTS rmq_data_source (
  ds_key VARCHAR(64) PRIMARY KEY,
  json TEXT NOT NULL COMMENT 'DataSourceVO JSON',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 样例数据（幂等）：topic / group 列表以本库为准，创建时写库、读取时读库。
-- 以电商交易链路为例：下单 -> 支付 -> 库存 -> 履约 -> 物流 -> 结算。
-- cluster_id 需与 NameServer 上报的集群名一致，否则页面按集群过滤时查不到。
-- ============================================================

INSERT IGNORE INTO rmq_topic
  (cluster_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
VALUES
  ('rocketmq-studio', 'order_create_event',        'NORMAL',      8,  8, 6,
   '下单成功事件，履约、营销、风控多方订阅', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'order_status_change',       'FIFO',        4,  4, 6,
   '订单状态流转，按订单号分区保证同单有序', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'order_timeout_cancel',      'DELAY',       8,  8, 6,
   '未支付订单超时关单，延迟 30 分钟投递', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'payment_result_notify',     'TRANSACTION', 8,  8, 6,
   '支付结果通知，与支付流水落库同事务', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'inventory_deduct_command',  'NORMAL',     16, 16, 6,
   '库存扣减指令，大促期间扩容至 16 队列', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'logistics_tracking_update', 'NORMAL',      8,  8, 6,
   '物流轨迹更新，承运商回调后投递', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'marketing_coupon_issue',    'NORMAL',      4,  4, 6,
   '营销发券，活动期间异步发放优惠券', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'settlement_daily_archive',  'NORMAL',      2,  2, 4,
   '日结账单归档，已停止写入仅供回溯消费', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'risk_control_audit',        'NORMAL',      4,  4, 2,
   '风控审计流水，仅生产侧写入，消费方待接入', 'ACTIVE', 'seed');

INSERT IGNORE INTO rmq_group
  (cluster_id, name, consume_type, message_model, max_retry, status, created_by)
VALUES
  ('rocketmq-studio', 'GID_fulfillment_order',     'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'GID_inventory_deduct',      'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'GID_payment_result',        'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'GID_logistics_tracking',    'PUSH', 'CLUSTERING',    5, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'GID_marketing_coupon',      'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'GID_settlement_archive',    'PULL', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'GID_bi_realtime_report',    'PUSH', 'BROADCASTING',  1, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'studio-trace-consumer',     'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed');
