-- deploy/mysql/upgrade-demo-alert.sql
-- 存量 MySQL 数据卷增量迁移：告警规则与系统告警入库（rmq_alert_rule / rmq_system_alert）
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
-- 全新数据卷由 server/src/main/resources/db/schema.sql 直接覆盖，无需本脚本。
-- 幂等：可重复执行。
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-demo-alert.sql

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_alert_rule (
  id VARCHAR(64) PRIMARY KEY,
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
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_system_alert (
  id VARCHAR(64) PRIMARY KEY,
  level VARCHAR(32),
  title VARCHAR(255),
  description TEXT,
  time DATETIME,
  acknowledged TINYINT(1) DEFAULT 0,
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  INDEX idx_level (level),
  INDEX idx_acknowledged (acknowledged)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
