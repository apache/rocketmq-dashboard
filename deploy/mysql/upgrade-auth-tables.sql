-- deploy/mysql/upgrade-auth-tables.sql
-- Existing MySQL volumes: create the Studio auth tables introduced in 2026-08.
-- Fresh volumes already receive these tables from server/src/main/resources/db/schema.sql.
-- Applicable when the MySQL data volume was initialized before Studio auth shipped, so
-- docker-entrypoint-initdb.d will not run schema.sql again.
-- Idempotent: safe to run multiple times.
--
-- Run once on an upgraded deployment before the first login:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-auth-tables.sql

-- Keep the client encoding fixed so comments and bootstrap usernames are not double-encoded.
SET NAMES utf8mb4;

-- 1. Studio console users (distinct from RocketMQ ACL users).
CREATE TABLE IF NOT EXISTS rmq_studio_user (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `username` VARCHAR(128) NOT NULL COMMENT '用户名',
  `password_hash` VARCHAR(512) NOT NULL COMMENT 'PBKDF2 密码哈希，禁止存储明文',
  `admin` TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员',
  `enabled` TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  `password_changed_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近修改密码时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_studio_user_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2. Studio bearer-token sessions. token_hash stores SHA-256(token), never the raw token.
CREATE TABLE IF NOT EXISTS rmq_studio_session (
  `id` bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `user_id` bigint(20) unsigned NOT NULL COMMENT '会话所属用户，引用 rmq_studio_user.id',
  `token_hash` CHAR(64) NOT NULL COMMENT 'SHA-256(session token)',
  `expires_at` DATETIME NOT NULL COMMENT '会话过期时间',
  `revoked_at` DATETIME NULL COMMENT '会话注销时间',
  `last_seen_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_studio_session_token_hash` (`token_hash`),
  KEY `idx_studio_session_user` (`user_id`),
  KEY `idx_studio_session_expiry` (`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
