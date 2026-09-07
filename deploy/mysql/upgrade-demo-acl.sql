-- deploy/mysql/upgrade-demo-acl.sql
-- Development-only sample ACL rules and users for the current Studio schema.
--
-- Prerequisite: initialize the database with server/src/main/resources/db/schema.sql first.
-- Run upgrade-demo-instance.sql first so every scope and cluster name refers to a visible demo instance.
-- This script does not create or alter schema objects and must not be used as a schema migration.
-- It is safe to rerun: users use schema unique keys and rules use their full logical identity.
--
-- Usage:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-demo-acl.sql

SET NAMES utf8mb4;

-- Rule IDs are numeric auto-increment values. Use logical-key checks instead of carrying the obsolete
-- acl-001-style string IDs from the pre-standardization schema.
INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-order-service', 'order_*', 'Topic', 'PREFIX', 'PUB,SUB', 'ALLOW', 'instance-proxy-1', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-order-service' AND resource = 'order_*' AND resource_type = 'Topic'
    AND resource_pattern = 'PREFIX' AND scope = 'instance-proxy-1' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-payment-service', 'payment_*', 'Topic', 'PREFIX', 'PUB,SUB', 'ALLOW', 'instance-proxy-1', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-payment-service' AND resource = 'payment_*' AND resource_type = 'Topic'
    AND resource_pattern = 'PREFIX' AND scope = 'instance-proxy-1' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-admin', '*', 'Cluster', 'LITERAL', 'ALL', 'ALLOW', 'instance-proxy-1', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-admin' AND resource = '*' AND resource_type = 'Cluster'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-1' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-log-collector', 'audit_operation_log', 'Topic', 'LITERAL', 'SUB', 'ALLOW', 'instance-direct-2', '1.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-log-collector' AND resource = 'audit_operation_log' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-direct-2' AND acl_version = '1.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-order-service', 'GID_fulfillment_*', 'Group', 'PREFIX', 'SUB', 'ALLOW', 'instance-proxy-1', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-order-service' AND resource = 'GID_fulfillment_*' AND resource_type = 'Group'
    AND resource_pattern = 'PREFIX' AND scope = 'instance-proxy-1' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-inventory-service', 'inventory_deduct_command', 'Topic', 'LITERAL', 'PUB,SUB', 'ALLOW', 'instance-proxy-1', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-inventory-service' AND resource = 'inventory_deduct_command' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-1' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-guest', 'payment_result_notify', 'Topic', 'LITERAL', 'PUB,SUB', 'DENY', 'instance-proxy-1', '1.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-guest' AND resource = 'payment_result_notify' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-1' AND acl_version = '1.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-notification-service', 'sms_send_command', 'Topic', 'LITERAL', 'PUB', 'ALLOW', 'instance-proxy-2', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-notification-service' AND resource = 'sms_send_command' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-2' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-risk-control', 'risk_event_alert', 'Topic', 'LITERAL', 'SUB', 'ALLOW', 'instance-direct-2', '1.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-risk-control' AND resource = 'risk_event_alert' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-direct-2' AND acl_version = '1.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-guest', '*', 'Cluster', 'LITERAL', 'PUB', 'DENY', 'instance-proxy-2', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-guest' AND resource = '*' AND resource_type = 'Cluster'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-2' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-payment-service', 'GID_payment_*', 'Group', 'PREFIX', 'SUB', 'ALLOW', 'instance-proxy-1', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-payment-service' AND resource = 'GID_payment_*' AND resource_type = 'Group'
    AND resource_pattern = 'PREFIX' AND scope = 'instance-proxy-1' AND acl_version = '2.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-monitor', 'user_behavior_log', 'Topic', 'LITERAL', 'SUB', 'ALLOW', 'instance-proxy-3', '1.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-monitor' AND resource = 'user_behavior_log' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-3' AND acl_version = '1.0'
);

INSERT INTO rmq_acl_rule
  (principal, resource, resource_type, resource_pattern, actions, decision, scope, acl_version)
SELECT 'user-ai-service', 'click_stream_etl', 'Topic', 'LITERAL', 'PUB,SUB', 'ALLOW', 'instance-proxy-3', '2.0'
WHERE NOT EXISTS (
  SELECT 1 FROM rmq_acl_rule
  WHERE principal = 'user-ai-service' AND resource = 'click_stream_etl' AND resource_type = 'Topic'
    AND resource_pattern = 'LITERAL' AND scope = 'instance-proxy-3' AND acl_version = '2.0'
);

-- User IDs are numeric auto-increment values. Access keys and usernames are unique in the
-- canonical schema, so INSERT IGNORE preserves credentials an operator has already changed.
INSERT IGNORE INTO rmq_acl_user
  (username, access_key, secret_key, admin, clusters)
VALUES
  ('user-admin', 'AKSTUDIOadmin0001', 'QWRtaW5AU3R1ZGlvIzIwMjY=', 1,
   'instance-proxy-1,instance-proxy-2,instance-proxy-3,instance-direct-1,instance-direct-2'),
  ('user-order-service', 'AKSTUDIOordr0002', 'T3JkZXJTdmNAMjAyNiNQcm9k', 0, 'instance-proxy-1'),
  ('user-payment-service', 'AKSTUDIOpaym0003', 'UGF5U3ZjQDIwMjYjUHJvZA==', 0, 'instance-proxy-1'),
  ('user-log-collector', 'AKSTUDIOlogs0004', 'TG9nQ29sbGVjdEAyMDI2I09wcw==', 0, 'instance-direct-2'),
  ('user-guest', 'AKSTUDIOgues0005', 'R3Vlc3RAMjAyNiNSZWFk', 0, 'instance-proxy-2'),
  ('user-inventory-service', 'AKSTUDIOinvn0006', 'SW52U3ZjQDIwMjYjUHJvZA==', 0, 'instance-proxy-1'),
  ('user-notification-service', 'AKSTUDIONtfy0007', 'Tm90aWZ5U3ZjQDIwMjYjTXNn', 0, 'instance-proxy-2'),
  ('user-monitor', 'AKSTUDIOmonr0008', 'TW9uaXRvckAyMDI2I09icw==', 0,
   'instance-proxy-3,instance-direct-2');
