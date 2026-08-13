-- deploy/mysql/upgrade-instance-name-uk.sql
-- 存量 MySQL 数据卷增量迁移（2026-08-12）：rmq_instance.name 全局唯一。
-- 背景：实例名（instanceId 概念）在 开源/阿里云/腾讯云 之间不重复，作为业务唯一标识；
--       此前无唯一约束，重复提交会把同一云实例加入两次。
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
--       全新数据卷由 server/src/main/resources/db/schema.sql 直接带上 uk_instance_name。
-- 幂等：可重复执行。
--
-- ⚠️ 会删除同名重复实例（保留 created_at 最早的一条）。执行前可先用下面的查询核对重复：
--   SELECT name, COUNT(*) FROM rmq_instance GROUP BY name HAVING COUNT(*) > 1;
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-instance-name-uk.sql

SET NAMES utf8mb4;

-- 1. 清理同名重复（保留 created_at 最早，id 最小作为并列时的决胜）
DELETE i FROM rmq_instance i
JOIN rmq_instance k
  ON k.name = i.name
 AND (k.created_at < i.created_at
      OR (k.created_at = i.created_at AND k.id < i.id));

-- 2. 追加唯一键（仅当不存在时）
SET @uk_exists := (
  SELECT COUNT(*) FROM information_schema.statistics
   WHERE table_schema = DATABASE()
     AND table_name = 'rmq_instance'
     AND index_name = 'uk_instance_name'
);
SET @uk_sql := IF(@uk_exists = 0,
    'ALTER TABLE rmq_instance ADD UNIQUE KEY uk_instance_name (name)',
    'SELECT ''uk_instance_name already exists'' AS msg');
PREPARE stmt FROM @uk_sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
