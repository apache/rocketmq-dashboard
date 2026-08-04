-- server/src/main/resources/db/schema.sql
-- RocketMQ Studio 数据库 Schema（MySQL 8.0）
-- 此文件为唯一权威 DDL 来源，MyBatis-Plus Entity 与此保持同步
-- 注意：MyBatis-Plus 不自动建表，需通过此 SQL 初始化（docker-compose 挂载执行）

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

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

-- 2. 实例注册表（实例管理页的数据源，topic/group 按 instance_id 归属统计）
CREATE TABLE IF NOT EXISTS rmq_instance (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  type VARCHAR(32) NOT NULL COMMENT 'PROXY/DIRECT',
  endpoint VARCHAR(512) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. Topic 管理记录（通过 Studio 创建/管理的 Topic 元数据）
CREATE TABLE IF NOT EXISTS rmq_topic (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64) COMMENT '归属实例，引用 rmq_instance.id',
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
  UNIQUE KEY uk_cluster_topic (cluster_id, name),
  INDEX idx_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4. Consumer Group 管理记录
CREATE TABLE IF NOT EXISTS rmq_group (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  cluster_id VARCHAR(64) NOT NULL,
  instance_id VARCHAR(64) COMMENT '归属实例，引用 rmq_instance.id',
  name VARCHAR(255) NOT NULL,
  consume_type VARCHAR(32) DEFAULT 'CONCURRENTLY',
  message_model VARCHAR(32) DEFAULT 'CLUSTERING',
  max_retry INT DEFAULT 16,
  status VARCHAR(32) DEFAULT 'ACTIVE',
  created_by VARCHAR(64),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_cluster_group (cluster_id, name),
  INDEX idx_instance (instance_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 5. K8s 证书管理
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

-- 6. 消息查询记录
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

-- 7. 消息轨迹查询记录
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

-- 8. 操作审计日志（所有写操作）
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

-- 9. 通用设置（单行）
CREATE TABLE IF NOT EXISTS rmq_settings (
  id VARCHAR(16) PRIMARY KEY DEFAULT 'singleton',
  json TEXT NOT NULL COMMENT 'GeneralSettingsVO JSON',
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 10. 数据源配置
CREATE TABLE IF NOT EXISTS rmq_data_source (
  ds_key VARCHAR(64) PRIMARY KEY,
  json TEXT NOT NULL COMMENT 'DataSourceVO JSON',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ============================================================
-- 样例数据（幂等）：instance / topic / group 列表以本库为准，创建时写库、读取时读库。
-- 实例管理页默认 5 个实例：2 个 DIRECT（instance-direct-1/2）+ 3 个 PROXY（instance-proxy-1/2/3）。
-- topic/group 通过 instance_id 归属实例，实例页的 topic/group 数量按 instance_id group by 实时统计。
-- cluster_id 需与 NameServer 上报的集群名一致，否则页面按集群过滤时查不到。
-- ============================================================

INSERT IGNORE INTO rmq_instance (id, name, remark, type, endpoint) VALUES
  ('instance-direct-1', 'instance-direct-1', '直连实例 1，交易核心链路（NameServer 直连）', 'DIRECT', '10.0.1.11:9876'),
  ('instance-direct-2', 'instance-direct-2', '直连实例 2，风控与审计链路（NameServer 直连）', 'DIRECT', '10.0.1.12:9876'),
  ('instance-proxy-1',  'instance-proxy-1',  'Proxy 实例 1，电商交易主链路', 'PROXY', '10.0.2.21:8080'),
  ('instance-proxy-2',  'instance-proxy-2',  'Proxy 实例 2，营销与会员链路', 'PROXY', '10.0.2.22:8080'),
  ('instance-proxy-3',  'instance-proxy-3',  'Proxy 实例 3，物流与大数据链路', 'PROXY', '10.0.2.23:8080');

INSERT IGNORE INTO rmq_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
VALUES
  -- instance-proxy-1：电商交易主链路
  ('rocketmq-studio', 'instance-proxy-1', 'order_create_event',        'NORMAL',      8,  8, 6,
   '下单成功事件，履约、营销、风控多方订阅', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'order_status_change',       'FIFO',        4,  4, 6,
   '订单状态流转，按订单号分区保证同单有序', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'order_timeout_cancel',      'DELAY',       8,  8, 6,
   '未支付订单超时关单，延迟 30 分钟投递', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'payment_result_notify',     'TRANSACTION', 8,  8, 6,
   '支付结果通知，与支付流水落库同事务', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'inventory_deduct_command',  'NORMAL',     16, 16, 6,
   '库存扣减指令，大促期间扩容至 16 队列', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'refund_apply_event',        'NORMAL',      4,  4, 6,
   '退款申请事件，客服与财务系统订阅', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'cart_sync_event',           'NORMAL',      4,  4, 6,
   '购物车多端同步事件', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'trade_close_archive',       'NORMAL',      2,  2, 4,
   '交易关单归档，只读供对账回溯', 'ACTIVE', 'seed'),
  -- instance-proxy-2：营销与会员链路
  ('rocketmq-studio', 'instance-proxy-2', 'marketing_coupon_issue',    'NORMAL',      4,  4, 6,
   '营销发券，活动期间异步发放优惠券', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'marketing_campaign_push',   'NORMAL',      8,  8, 6,
   '大促活动 push 触达，按人群包分批投递', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'member_register_event',     'NORMAL',      4,  4, 6,
   '新会员注册事件，积分与权益系统订阅', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'member_points_change',      'FIFO',        4,  4, 6,
   '会员积分变动，按会员 ID 分区保序', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'member_level_upgrade',      'DELAY',       4,  4, 6,
   '会员升级权益延迟发放', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'sms_send_command',          'NORMAL',      8,  8, 6,
   '短信下发指令，网关限流后消费', 'ACTIVE', 'seed'),
  -- instance-proxy-3：物流与大数据链路
  ('rocketmq-studio', 'instance-proxy-3', 'logistics_tracking_update', 'NORMAL',      8,  8, 6,
   '物流轨迹更新，承运商回调后投递', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'logistics_dispatch_order',  'FIFO',        8,  8, 6,
   '运单调度指令，同单有序', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'settlement_daily_archive',  'NORMAL',      2,  2, 4,
   '日结账单归档，已停止写入仅供回溯消费', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'bi_realtime_report',        'NORMAL',     16, 16, 6,
   '实时报表数据流，BI 大屏消费', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'user_behavior_log',         'NORMAL',     16, 16, 6,
   '用户行为埋点日志，离线分析入湖', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'click_stream_etl',          'NORMAL',      8,  8, 6,
   '点击流 ETL 中间结果', 'ACTIVE', 'seed'),
  -- instance-direct-1：交易核心直连链路
  ('rocketmq-studio', 'instance-direct-1', 'trade_core_order_flow',    'FIFO',        8,  8, 6,
   '交易核心订单流水，直连低延迟链路', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'payment_channel_callback', 'NORMAL',      8,  8, 6,
   '支付渠道回调通知', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'account_ledger_entry',     'TRANSACTION', 8,  8, 6,
   '账户记账分录，与账务落库同事务', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'ledger_reconcile_task',    'DELAY',       4,  4, 6,
   '对账任务延迟触发，T+1 凌晨执行', 'ACTIVE', 'seed'),
  -- instance-direct-2：风控与审计链路
  ('rocketmq-studio', 'instance-direct-2', 'risk_control_audit',       'NORMAL',      4,  4, 2,
   '风控审计流水，仅生产侧写入，消费方待接入', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'risk_event_alert',         'NORMAL',      4,  4, 6,
   '风控命中事件告警，实时推送处置平台', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'audit_operation_log',      'NORMAL',      8,  8, 6,
   '操作审计日志，合规留存 180 天', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'compliance_report_daily',  'DELAY',       2,  2, 6,
   '合规日报延迟生成任务', 'ACTIVE', 'seed');

INSERT IGNORE INTO rmq_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
VALUES
  -- instance-proxy-1
  ('rocketmq-studio', 'instance-proxy-1', 'GID_fulfillment_order',  'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_inventory_deduct',   'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_payment_result',     'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_refund_process',     'PUSH', 'CLUSTERING',    8, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_cart_sync',          'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_trade_archive',      'PULL', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  -- instance-proxy-2
  ('rocketmq-studio', 'instance-proxy-2', 'GID_marketing_coupon',   'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_campaign_push',      'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_member_points',      'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_member_benefit',     'PUSH', 'CLUSTERING',    8, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_sms_gateway',        'PUSH', 'CLUSTERING',    5, 'ACTIVE', 'seed'),
  -- instance-proxy-3
  ('rocketmq-studio', 'instance-proxy-3', 'GID_logistics_tracking', 'PUSH', 'CLUSTERING',    5, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_logistics_dispatch', 'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_settlement_archive', 'PULL', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_bi_realtime_report', 'PUSH', 'BROADCASTING',  1, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_behavior_ingest',    'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_click_stream_etl',   'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  -- instance-direct-1
  ('rocketmq-studio', 'instance-direct-1', 'GID_trade_core_flow',   'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_pay_channel_cb',    'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_ledger_entry',      'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_reconcile_task',    'PULL', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  -- instance-direct-2
  ('rocketmq-studio', 'instance-direct-2', 'GID_risk_alert',        'PUSH', 'CLUSTERING',    8, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'GID_audit_archive',     'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'GID_compliance_daily',  'PULL', 'CLUSTERING',    1, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'studio-trace-consumer', 'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed');

