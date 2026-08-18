-- deploy/mysql/upgrade-studio-auth.sql
-- Existing MySQL volume migration (2026-08-19): persistent Studio users and sessions.
-- Fresh volumes already receive these tables from server/src/main/resources/db/schema.sql.
-- Idempotent: safe to run more than once.
--
-- Usage:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-studio-auth.sql

SET NAMES utf8mb4;

-- Keep these definitions aligned with schema.sql so upgraded and fresh volumes use the same schema.
CREATE TABLE IF NOT EXISTS rmq_studio_user (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  username VARCHAR(128) NOT NULL COMMENT '用户名',
  password_hash VARCHAR(512) NOT NULL COMMENT 'PBKDF2 密码哈希，禁止存储明文',
  admin TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否管理员',
  enabled TINYINT(1) NOT NULL DEFAULT 1 COMMENT '是否启用',
  password_changed_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近修改密码时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_studio_user_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS rmq_studio_session (
  `id`           bigint(20) unsigned NOT NULL AUTO_INCREMENT COMMENT '主键',
  `gmt_create`   datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `gmt_modified` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  user_id bigint(20) unsigned NOT NULL COMMENT '会话所属用户，引用 rmq_studio_user.id',
  token_hash CHAR(64) NOT NULL COMMENT 'SHA-256(session token)',
  expires_at DATETIME NOT NULL COMMENT '会话过期时间',
  revoked_at DATETIME NULL COMMENT '会话注销时间',
  last_seen_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近活跃时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY uk_studio_session_token_hash (token_hash),
  INDEX idx_studio_session_user (user_id),
  INDEX idx_studio_session_expiry (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
