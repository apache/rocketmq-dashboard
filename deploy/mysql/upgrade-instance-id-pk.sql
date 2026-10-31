-- Licensed to the Apache Software Foundation (ASF) under one or more
-- contributor license agreements.  See the NOTICE file distributed with
-- this work for additional information regarding copyright ownership.
-- The ASF licenses this file to You under the Apache License, Version 2.0
-- (the "License"); you may not use this file except in compliance with
-- the License.  You may obtain a copy of the License at
--
--     http://www.apache.org/licenses/LICENSE-2.0
--
-- Unless required by applicable law or agreed to in writing, software
-- distributed under the License is distributed on an "AS IS" BASIS,
-- WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
-- See the License for the specific language governing permissions and
-- limitations under the License.

-- deploy/mysql/upgrade-instance-id-pk.sql
-- 存量 MySQL 数据卷增量迁移（2026-08-12）：废弃实例 UUID 主键，rmq_instance.id 改写为实例 ID（name）。
-- 背景：系统不再使用 UUID 作为实例标识；实例 ID（原 name 字段，全局唯一、创建后不可变）
--       直接作为主键，URL / API / 存储一律走实例 ID。
-- 适用：数据卷已初始化、docker-entrypoint-initdb.d 不会再执行的存量部署。
--       全新创建的实例 id 即实例 ID，无需本脚本。
-- 幂等：可重复执行（所有语句带 id <> name 条件）。
--
-- ⚠️ 第 3 步会改写 rmq_instance 主键值，执行前建议备份：
--   docker exec rocketmq-studio-mysql sh -c 'exec mysqldump -uroot -pstudio123 rocketmq' > backup.sql
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-instance-id-pk.sql

SET NAMES utf8mb4;

-- 1. 子表引用列先改（必须在 rmq_instance 主键改写之前执行）
UPDATE rmq_topic t
JOIN rmq_instance i ON t.instance_id = i.id AND i.id <> i.name
SET t.instance_id = i.name;

UPDATE rmq_group g
JOIN rmq_instance i ON g.instance_id = i.id AND i.id <> i.name
SET g.instance_id = i.name;

UPDATE rmq_acl_rule r
JOIN rmq_instance i ON r.scope = i.id AND i.id <> i.name
SET r.scope = i.name;

-- 2. 逗号分隔 / JSON 内容按实例逐个替换
UPDATE rmq_acl_user u
JOIN rmq_instance i ON i.id <> i.name
SET u.clusters = REPLACE(u.clusters, i.id, i.name)
WHERE u.clusters LIKE CONCAT('%', i.id, '%');

UPDATE rmq_data_source d
JOIN rmq_instance i ON i.id <> i.name
SET d.json = REPLACE(d.json, i.id, i.name)
WHERE d.json LIKE CONCAT('%', i.id, '%');

-- 3. 最后改写主键：id = 实例 ID
UPDATE rmq_instance SET id = name WHERE id <> name;
