-- deploy/mysql/upgrade-nameserver-name-uk.sql
-- 存量 MySQL 数据卷增量迁移（2026-08-25）：rmq_nameserver.name 全局唯一。
-- 背景：nameserver 注册名作为业务唯一标识；此前无唯一约束，并发创建可能产生重复记录。
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
--       全新数据卷由 server/src/main/resources/db/schema.sql 直接带上 uk_nameserver_name。
-- 幂等：可重复执行。
--
-- ⚠️ 会删除同名重复记录（保留 created_at 最早的一条）。执行前可先用下面的查询核对重复：
--   SELECT name, COUNT(*) FROM rmq_nameserver GROUP BY name HAVING COUNT(*) > 1;
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-nameserver-name-uk.sql

SET NAMES utf8mb4;

-- 1. 清理同名重复（保留 created_at 最早，id 最小作为并列时的决胜）
DELETE i FROM rmq_nameserver i
JOIN rmq_nameserver k
  ON k.name = i.name
 AND (k.created_at < i.created_at
      OR (k.created_at = i.created_at AND k.id < i.id));

-- 2. 追加唯一键（仅当不存在时）
SET @uk_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'rmq_nameserver'
     AND index_name = 'uk_nameserver_name'
);
SET @uk_sql := IF(@uk_exists = 0,
    'ALTER TABLE rmq_nameserver ADD UNIQUE KEY uk_nameserver_name (name)',
    'SELECT ''uk_nameserver_name already exists'' AS msg');
PREPARE stmt FROM @uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
