-- deploy/mysql/upgrade-demo-acl.sql
-- 存量 MySQL 数据卷增量迁移（2026-08-03）：ACL 规则/用户入库
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
-- 全新数据卷由 server/src/main/resources/db/schema.sql 直接覆盖，无需本脚本。
-- 幂等：可重复执行。
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq_studio < upgrade-demo-acl.sql

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

-- 1. ACL 规则表
CREATE TABLE IF NOT EXISTS rmq_acl_rule (
  id VARCHAR(64) PRIMARY KEY,
  principal VARCHAR(128) NOT NULL,
  resource VARCHAR(255) NOT NULL,
  resource_type VARCHAR(32) COMMENT 'Topic/Group/Cluster',
  resource_pattern VARCHAR(32) COMMENT 'LITERAL/PREFIX',
  actions VARCHAR(128) COMMENT '逗号分隔：PUB/SUB/ALL',
  decision VARCHAR(16) COMMENT 'ALLOW/DENY',
  scope VARCHAR(64) COMMENT '生效范围（集群名/实例 id）',
  acl_version VARCHAR(16) COMMENT '1.0/2.0',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_principal (principal)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. ACL 用户表（secret_key 为 base64 编码后的密码，禁止明文存储）
CREATE TABLE IF NOT EXISTS rmq_acl_user (
  id VARCHAR(64) PRIMARY KEY,
  username VARCHAR(128) NOT NULL,
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL COMMENT 'base64 编码的密码',
  admin TINYINT(1) DEFAULT 0,
  clusters VARCHAR(1024) COMMENT '逗号分隔的集群/实例 id',
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3. 规则种子（与 schema.sql 一致）
INSERT IGNORE INTO rmq_acl_rule
  (id, principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
VALUES
  ('acl-001', 'user-order-service',         'order_*',                 'Topic',   'PREFIX',  'PUB,SUB', 'ALLOW', 'instance-proxy-1',  '2.0'),
  ('acl-002', 'user-payment-service',       'payment_*',               'Topic',   'PREFIX',  'PUB,SUB', 'ALLOW', 'instance-proxy-1',  '2.0'),
  ('acl-003', 'user-admin',                 '*',                       'Cluster', 'LITERAL', 'ALL',     'ALLOW', 'instance-proxy-1',  '2.0'),
  ('acl-004', 'user-log-collector',         'audit_operation_log',     'Topic',   'LITERAL', 'SUB',     'ALLOW', 'instance-direct-2', '1.0'),
  ('acl-005', 'user-order-service',         'GID_fulfillment_*',       'Group',   'PREFIX',  'SUB',     'ALLOW', 'instance-proxy-1',  '2.0'),
  ('acl-006', 'user-inventory-service',     'inventory_deduct_command','Topic',   'LITERAL', 'PUB,SUB', 'ALLOW', 'instance-proxy-1',  '2.0'),
  ('acl-007', 'user-guest',                 'payment_result_notify',   'Topic',   'LITERAL', 'PUB,SUB', 'DENY',  'instance-proxy-1',  '1.0'),
  ('acl-008', 'user-notification-service',  'sms_send_command',        'Topic',   'LITERAL', 'PUB',     'ALLOW', 'instance-proxy-2',  '2.0'),
  ('acl-009', 'user-risk-control',          'risk_event_alert',        'Topic',   'LITERAL', 'SUB',     'ALLOW', 'instance-direct-2', '1.0'),
  ('acl-010', 'user-guest',                 '*',                       'Cluster', 'LITERAL', 'PUB',     'DENY',  'instance-proxy-2',  '2.0'),
  ('acl-011', 'user-payment-service',       'GID_payment_*',           'Group',   'PREFIX',  'SUB',     'ALLOW', 'instance-proxy-1',  '2.0'),
  ('acl-012', 'user-monitor',               'user_behavior_log',       'Topic',   'LITERAL', 'SUB',     'ALLOW', 'instance-proxy-3',  '1.0'),
  ('acl-013', 'user-ai-service',            'click_stream_etl',        'Topic',   'LITERAL', 'PUB,SUB', 'ALLOW', 'instance-proxy-3',  '2.0');

-- 4. 用户种子（secret_key 为密码的 base64 编码）
INSERT IGNORE INTO rmq_acl_user
  (id, username, access_key, secret_key, admin, clusters)
VALUES
  ('u-001', 'user-admin',                 'AKSTUDIOadmin0001', 'QWRtaW5AU3R1ZGlvIzIwMjY=',     1,
   'instance-proxy-1,instance-proxy-2,instance-proxy-3,instance-direct-1,instance-direct-2'),
  ('u-002', 'user-order-service',         'AKSTUDIOordr0002',  'T3JkZXJTdmNAMjAyNiNQcm9k',     0, 'instance-proxy-1'),
  ('u-003', 'user-payment-service',       'AKSTUDIOpaym0003',  'UGF5U3ZjQDIwMjYjUHJvZA==',     0, 'instance-proxy-1'),
  ('u-004', 'user-log-collector',         'AKSTUDIOlogs0004',  'TG9nQ29sbGVjdEAyMDI2I09wcw==', 0, 'instance-direct-2'),
  ('u-005', 'user-guest',                 'AKSTUDIOgues0005',  'R3Vlc3RAMjAyNiNSZWFk',         0, 'instance-proxy-2'),
  ('u-006', 'user-inventory-service',     'AKSTUDIOinvn0006',  'SW52U3ZjQDIwMjYjUHJvZA==',     0, 'instance-proxy-1'),
  ('u-007', 'user-notification-service',  'AKSTUDIONtfy0007',  'Tm90aWZ5U3ZjQDIwMjYjTXNn',     0, 'instance-proxy-2'),
  ('u-008', 'user-monitor',               'AKSTUDIOmonr0008',  'TW9uaXRvckAyMDI2I09icw==',     0,
   'instance-proxy-3,instance-direct-2');
