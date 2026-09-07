-- deploy/mysql/upgrade-cloud-vendor.sql
-- 存量 MySQL 数据卷增量迁移：rmq_instance 增加厂商维度 + 新增 rmq_cloud_credential 凭据表
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
-- 全新数据卷由 server/src/main/resources/db/schema.sql 直接覆盖，无需本脚本。
-- 幂等：可重复执行。
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-cloud-vendor.sql

-- 固定连接编码，防止 mysql 客户端以 latin1 解释 UTF-8 字节导致中文双重编码
SET NAMES utf8mb4;

SET @schema_name := DATABASE();

-- 1. rmq_instance.vendor
SET @vendor_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_instance'
      AND column_name = 'vendor'
);
SET @vendor_sql := IF(@vendor_column_exists = 0,
    "ALTER TABLE rmq_instance ADD COLUMN vendor VARCHAR(32) NOT NULL DEFAULT 'APACHE' COMMENT 'APACHE/ALIYUN/TENCENT' AFTER endpoint",
    'SELECT 1');
PREPARE vendor_statement FROM @vendor_sql;
EXECUTE vendor_statement;
DEALLOCATE PREPARE vendor_statement;

-- 2. rmq_instance.cloud_instance_id
SET @cloud_instance_id_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_instance'
      AND column_name = 'cloud_instance_id'
);
SET @cloud_instance_id_sql := IF(@cloud_instance_id_column_exists = 0,
    "ALTER TABLE rmq_instance ADD COLUMN cloud_instance_id VARCHAR(128) COMMENT '云厂商实例 ID（vendor 非 APACHE 时必填）' AFTER vendor",
    'SELECT 1');
PREPARE cloud_instance_id_statement FROM @cloud_instance_id_sql;
EXECUTE cloud_instance_id_statement;
DEALLOCATE PREPARE cloud_instance_id_statement;

-- 3. rmq_instance.credential_id
SET @credential_id_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_instance'
      AND column_name = 'credential_id'
);
SET @credential_id_sql := IF(@credential_id_column_exists = 0,
    "ALTER TABLE rmq_instance ADD COLUMN credential_id VARCHAR(64) COMMENT '引用 rmq_cloud_credential.id' AFTER cloud_instance_id",
    'SELECT 1');
PREPARE credential_id_statement FROM @credential_id_sql;
EXECUTE credential_id_statement;
DEALLOCATE PREPARE credential_id_statement;

-- 4. rmq_instance.region_id
SET @region_id_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_instance'
      AND column_name = 'region_id'
);
SET @region_id_sql := IF(@region_id_column_exists = 0,
    "ALTER TABLE rmq_instance ADD COLUMN region_id VARCHAR(64) COMMENT '云 region' AFTER credential_id",
    'SELECT 1');
PREPARE region_id_statement FROM @region_id_sql;
EXECUTE region_id_statement;
DEALLOCATE PREPARE region_id_statement;

-- 5. rmq_instance.admin_credential_ref. The reference is non-secret; actual Apache admin
-- credentials remain external Spring configuration and are never stored in this database.
SET @admin_credential_ref_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_instance'
      AND column_name = 'admin_credential_ref'
);
SET @admin_credential_ref_sql := IF(@admin_credential_ref_column_exists = 0,
    "ALTER TABLE rmq_instance ADD COLUMN admin_credential_ref VARCHAR(128) COMMENT 'External Apache admin credential reference; no secret material' AFTER credential_id",
    'SELECT 1');
PREPARE admin_credential_ref_statement FROM @admin_credential_ref_sql;
EXECUTE admin_credential_ref_statement;
DEALLOCATE PREPARE admin_credential_ref_statement;

-- 6. 存量实例兜底回填
UPDATE rmq_instance SET vendor = 'APACHE' WHERE vendor IS NULL OR vendor = '';

-- 7. 云厂商凭据表（secret_key 为 base64 编码，禁止明文；access_key 明文用于唯一键与打码展示）
CREATE TABLE IF NOT EXISTS rmq_cloud_credential (
  id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL COMMENT '凭据显示名',
  vendor VARCHAR(32) NOT NULL COMMENT 'ALIYUN/TENCENT',
  access_key VARCHAR(255) NOT NULL,
  secret_key VARCHAR(512) NOT NULL COMMENT 'base64 编码的 SK',
  remark VARCHAR(255),
  created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_vendor_access_key (vendor, access_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
