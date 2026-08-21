-- deploy/mysql/upgrade-demo-instance.sql
-- Development-only sample instances, topics and consumer groups for the current Studio schema.
--
-- Prerequisite: initialize the database with server/src/main/resources/db/schema.sql first.
-- This script does not create or alter schema objects and must not be used as a schema migration.
-- It is safe to rerun: instance names and (cluster_id, name) metadata keys are unique.
--
-- Usage:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-demo-instance.sql

SET NAMES utf8mb4;

-- The application owns numeric auto-increment primary keys. The stable API identifier is
-- rmq_instance.name, so demo rows omit id and resolve child instance_id values by name.
INSERT IGNORE INTO rmq_instance
  (name, remark, type, endpoint, vendor)
VALUES
  ('instance-direct-1', '直连实例 1，交易核心链路（NameServer 直连）', 'DIRECT', '10.0.1.11:9876', 'APACHE'),
  ('instance-direct-2', '直连实例 2，风控与审计链路（NameServer 直连）', 'DIRECT', '10.0.1.12:9876', 'APACHE'),
  ('instance-proxy-1',  'Proxy 实例 1，电商交易主链路', 'PROXY_CLUSTER', '10.0.2.21:8080', 'APACHE'),
  ('instance-proxy-2',  'Proxy 实例 2，营销与会员链路', 'PROXY_CLUSTER', '10.0.2.22:8080', 'APACHE'),
  ('instance-proxy-3',  'Proxy 实例 3，物流与大数据链路', 'PROXY_CLUSTER', '10.0.2.23:8080', 'APACHE');

-- Topic metadata. Each insert resolves the numeric foreign key from the stable instance name.
INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'refund_apply_event', 'NORMAL', 4, 4, 6,
       '退款申请事件，客服与财务系统订阅', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'cart_sync_event', 'NORMAL', 4, 4, 6,
       '购物车多端同步事件', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'trade_close_archive', 'NORMAL', 2, 2, 4,
       '交易关单归档，只读供对账回溯', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'marketing_campaign_push', 'NORMAL', 8, 8, 6,
       '大促活动 push 触达，按人群包分批投递', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'member_register_event', 'NORMAL', 4, 4, 6,
       '新会员注册事件，积分与权益系统订阅', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'member_points_change', 'FIFO', 4, 4, 6,
       '会员积分变动，按会员 ID 分区保序', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'member_level_upgrade', 'DELAY', 4, 4, 6,
       '会员升级权益延迟发放', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'sms_send_command', 'NORMAL', 8, 8, 6,
       '短信下发指令，网关限流后消费', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'logistics_dispatch_order', 'FIFO', 8, 8, 6,
       '运单调度指令，同单有序', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'bi_realtime_report', 'NORMAL', 16, 16, 6,
       '实时报表数据流，BI 大屏消费', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'user_behavior_log', 'NORMAL', 16, 16, 6,
       '用户行为埋点日志，离线分析入湖', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'click_stream_etl', 'NORMAL', 8, 8, 6,
       '点击流 ETL 中间结果', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'trade_core_order_flow', 'FIFO', 8, 8, 6,
       '交易核心订单流水，直连低延迟链路', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'payment_channel_callback', 'NORMAL', 8, 8, 6,
       '支付渠道回调通知', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'account_ledger_entry', 'TRANSACTION', 8, 8, 6,
       '账户记账分录，与账务落库同事务', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'ledger_reconcile_task', 'DELAY', 4, 4, 6,
       '对账任务延迟触发，T+1 凌晨执行', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'risk_event_alert', 'NORMAL', 4, 4, 6,
       '风控命中事件告警，实时推送处置平台', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'audit_operation_log', 'NORMAL', 8, 8, 6,
       '操作审计日志，合规留存 180 天', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-2';

INSERT IGNORE INTO rmq_instance_topic
  (cluster_id, instance_id, name, topic_type, read_queue_nums, write_queue_nums, perm, remark, status, created_by)
SELECT 'rocketmq-studio', id, 'compliance_report_daily', 'DELAY', 2, 2, 6,
       '合规日报延迟生成任务', 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-2';

-- Consumer-group metadata uses the same name-to-numeric-id resolution.
INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_refund_process', 'PUSH', 'CLUSTERING', 8, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_cart_sync', 'PUSH', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_trade_archive', 'PULL', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_campaign_push', 'PUSH', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_member_points', 'PUSH', 'CLUSTERING', 16, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_member_benefit', 'PUSH', 'CLUSTERING', 8, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_sms_gateway', 'PUSH', 'CLUSTERING', 5, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-2';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_logistics_dispatch', 'PUSH', 'CLUSTERING', 16, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_behavior_ingest', 'PUSH', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_click_stream_etl', 'PUSH', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-proxy-3';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_trade_core_flow', 'PUSH', 'CLUSTERING', 16, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_pay_channel_cb', 'PUSH', 'CLUSTERING', 16, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_ledger_entry', 'PUSH', 'CLUSTERING', 16, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_reconcile_task', 'PULL', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-1';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_risk_alert', 'PUSH', 'CLUSTERING', 8, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-2';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_audit_archive', 'PUSH', 'CLUSTERING', 3, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-2';

INSERT IGNORE INTO rmq_instance_group
  (cluster_id, instance_id, name, consume_type, message_model, max_retry, status, created_by)
SELECT 'rocketmq-studio', id, 'GID_compliance_daily', 'PULL', 'CLUSTERING', 1, 'ACTIVE', 'seed'
FROM rmq_instance WHERE name = 'instance-direct-2';
