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

-- deploy/mysql/upgrade-username-column-width.sql
-- Existing MySQL volumes: widen the username columns to VARCHAR(128).
-- Fresh volumes already receive these widths from server/src/main/resources/db/schema.sql, and
-- UsernameColumnWideningSchemaMigration re-applies them on every boot, so this script is only
-- needed when the application's database user is not allowed to run ALTER (the runner then just
-- logs a warning and leaves the columns narrow).
-- Usernames may be up to 128 characters (see AuthService), so the query-history owner columns and
-- the audit operator must match.
-- 幂等：可重复执行（MODIFY 只增长列宽，已是 128 时为空操作）。
--
-- 用法（远程容器内执行）：
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-username-column-width.sql

SET NAMES utf8mb4;

ALTER TABLE rmq_instance_message MODIFY queried_by VARCHAR(128);
ALTER TABLE rmq_instance_trace MODIFY queried_by VARCHAR(128);
ALTER TABLE rmq_operation_audit MODIFY operator VARCHAR(128);
