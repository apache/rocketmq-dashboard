-- deploy/mysql/upgrade-demo-instance.sql
-- 存量 MySQL 数据卷增量迁移（2026-08-03）：实例持久化 + topic/group 增加 instance_id
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
-- 全新数据卷由 server/src/main/resources/db/schema.sql 直接覆盖，无需本脚本。
-- 幂等：可重复执行。
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq_studio < upgrade-demo-instance.sql

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

-- 1. 实例注册表
CREATE TABLE IF NOT EXISTS rmq_instance (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  remark VARCHAR(255),
  type VARCHAR(32) NOT NULL COMMENT 'PROXY/DIRECT',
  endpoint VARCHAR(512) NOT NULL,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. rmq_topic / rmq_group 增加 instance_id 列与索引（MySQL 8.0 无 ADD COLUMN IF NOT EXISTS，用 information_schema 判断）
SET @sql = (SELECT IF(
    COUNT(*) > 0, 'SELECT ''rmq_topic.instance_id already exists'' AS msg',
    'ALTER TABLE rmq_topic ADD COLUMN instance_id VARCHAR(64) COMMENT ''归属实例，引用 rmq_instance.id'' AFTER cluster_id, ADD INDEX idx_instance (instance_id)')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rmq_topic' AND COLUMN_NAME = 'instance_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @sql = (SELECT IF(
    COUNT(*) > 0, 'SELECT ''rmq_group.instance_id already exists'' AS msg',
    'ALTER TABLE rmq_group ADD COLUMN instance_id VARCHAR(64) COMMENT ''归属实例，引用 rmq_instance.id'' AFTER cluster_id, ADD INDEX idx_instance (instance_id)')
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'rmq_group' AND COLUMN_NAME = 'instance_id');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

-- 3. 默认 5 个实例（幂等）
INSERT IGNORE INTO rmq_instance (id, name, remark, type, endpoint) VALUES
  ('instance-direct-1', 'instance-direct-1', '直连实例 1，交易核心链路（NameServer 直连）', 'DIRECT', '10.0.1.11:9876'),
  ('instance-direct-2', 'instance-direct-2', '直连实例 2，风控与审计链路（NameServer 直连）', 'DIRECT', '10.0.1.12:9876'),
  ('instance-proxy-1',  'instance-proxy-1',  'Proxy 实例 1，电商交易主链路', 'PROXY', '10.0.2.21:8080'),
  ('instance-proxy-2',  'instance-proxy-2',  'Proxy 实例 2，营销与会员链路', 'PROXY', '10.0.2.22:8080'),
  ('instance-proxy-3',  'instance-proxy-3',  'Proxy 实例 3，物流与大数据链路', 'PROXY', '10.0.2.23:8080');

-- 4. 旧种子数据回填 instance_id（旧部署里这 9 个 topic、8 个 group 已存在，INSERT IGNORE 不会更新它们）
UPDATE rmq_topic SET instance_id = 'instance-proxy-1'
  WHERE instance_id IS NULL AND name IN (
    'order_create_event', 'order_status_change', 'order_timeout_cancel',
    'payment_result_notify', 'inventory_deduct_command');
UPDATE rmq_topic SET instance_id = 'instance-proxy-2'
  WHERE instance_id IS NULL AND name IN ('marketing_coupon_issue');
UPDATE rmq_topic SET instance_id = 'instance-proxy-3'
  WHERE instance_id IS NULL AND name IN ('logistics_tracking_update', 'settlement_daily_archive');
UPDATE rmq_topic SET instance_id = 'instance-direct-2'
  WHERE instance_id IS NULL AND name IN ('risk_control_audit');

UPDATE rmq_group SET instance_id = 'instance-proxy-1'
  WHERE instance_id IS NULL AND name IN (
    'GID_fulfillment_order', 'GID_inventory_deduct', 'GID_payment_result');
UPDATE rmq_group SET instance_id = 'instance-proxy-2'
  WHERE instance_id IS NULL AND name IN ('GID_marketing_coupon');
UPDATE rmq_group SET instance_id = 'instance-proxy-3'
  WHERE instance_id IS NULL AND name IN (
    'GID_logistics_tracking', 'GID_settlement_archive', 'GID_bi_realtime_report');
UPDATE rmq_group SET instance_id = 'instance-direct-2'
  WHERE instance_id IS NULL AND name IN ('studio-trace-consumer');

-- 5. 补充新种子 topic/group（与 schema.sql 一致，已存在的行被 IGNORE 跳过）
INSERT IGNORE INTO rmq_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
VALUES
  ('rocketmq-studio', 'instance-proxy-1', 'refund_apply_event',        'NORMAL',      4,  4, 6,
   '退款申请事件，客服与财务系统订阅', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'cart_sync_event',           'NORMAL',      4,  4, 6,
   '购物车多端同步事件', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'trade_close_archive',       'NORMAL',      2,  2, 4,
   '交易关单归档，只读供对账回溯', 'ACTIVE', 'seed'),
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
  ('rocketmq-studio', 'instance-proxy-3', 'logistics_dispatch_order',  'FIFO',        8,  8, 6,
   '运单调度指令，同单有序', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'bi_realtime_report',        'NORMAL',     16, 16, 6,
   '实时报表数据流，BI 大屏消费', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'user_behavior_log',         'NORMAL',     16, 16, 6,
   '用户行为埋点日志，离线分析入湖', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'click_stream_etl',          'NORMAL',      8,  8, 6,
   '点击流 ETL 中间结果', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'trade_core_order_flow',    'FIFO',        8,  8, 6,
   '交易核心订单流水，直连低延迟链路', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'payment_channel_callback', 'NORMAL',      8,  8, 6,
   '支付渠道回调通知', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'account_ledger_entry',     'TRANSACTION', 8,  8, 6,
   '账户记账分录，与账务落库同事务', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'ledger_reconcile_task',    'DELAY',       4,  4, 6,
   '对账任务延迟触发，T+1 凌晨执行', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'risk_event_alert',         'NORMAL',      4,  4, 6,
   '风控命中事件告警，实时推送处置平台', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'audit_operation_log',      'NORMAL',      8,  8, 6,
   '操作审计日志，合规留存 180 天', 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'compliance_report_daily',  'DELAY',       2,  2, 6,
   '合规日报延迟生成任务', 'ACTIVE', 'seed');

INSERT IGNORE INTO rmq_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
VALUES
  ('rocketmq-studio', 'instance-proxy-1', 'GID_refund_process',     'PUSH', 'CLUSTERING',    8, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_cart_sync',          'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-1', 'GID_trade_archive',      'PULL', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_campaign_push',      'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_member_points',      'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_member_benefit',     'PUSH', 'CLUSTERING',    8, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-2', 'GID_sms_gateway',        'PUSH', 'CLUSTERING',    5, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_logistics_dispatch', 'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_behavior_ingest',    'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-proxy-3', 'GID_click_stream_etl',   'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_trade_core_flow',   'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_pay_channel_cb',    'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_ledger_entry',      'PUSH', 'CLUSTERING',   16, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-1', 'GID_reconcile_task',    'PULL', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'GID_risk_alert',        'PUSH', 'CLUSTERING',    8, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'GID_audit_archive',     'PUSH', 'CLUSTERING',    3, 'ACTIVE', 'seed'),
  ('rocketmq-studio', 'instance-direct-2', 'GID_compliance_daily',  'PULL', 'CLUSTERING',    1, 'ACTIVE', 'seed');
