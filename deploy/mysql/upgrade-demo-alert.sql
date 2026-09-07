-- deploy/mysql/upgrade-demo-alert.sql
-- Compatibility helper for deployments that predate the alert tables.
-- Existing deployments must otherwise follow the canonical schema in
-- server/src/main/resources/db/schema.sql. This helper only creates missing alert tables.
-- 幂等：可重复执行。
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-demo-alert.sql

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

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
