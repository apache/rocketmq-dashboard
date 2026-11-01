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

-- deploy/mysql/upgrade-query-history-context.sql
-- Existing MySQL volumes: add query-history context columns.
-- Fresh volumes already receive these columns from server/src/main/resources/db/schema.sql.
--
-- Run once on existing deployments:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq < upgrade-query-history-context.sql

SET @schema_name := DATABASE();

SET @message_query_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_message_query'
      AND column_name = 'cluster_id'
);
SET @message_query_sql := IF(@message_query_column_exists = 0,
    'ALTER TABLE rmq_message_query ADD COLUMN cluster_id VARCHAR(255) AFTER result_count',
    'SELECT 1');
PREPARE message_query_statement FROM @message_query_sql;
EXECUTE message_query_statement;
DEALLOCATE PREPARE message_query_statement;

SET @trace_query_column_exists := (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = @schema_name
      AND table_name = 'rmq_trace_query'
      AND column_name = 'cluster_id'
);
SET @trace_query_sql := IF(@trace_query_column_exists = 0,
    'ALTER TABLE rmq_trace_query ADD COLUMN cluster_id VARCHAR(255) AFTER consumer_count',
    'SELECT 1');
PREPARE trace_query_statement FROM @trace_query_sql;
EXECUTE trace_query_statement;
DEALLOCATE PREPARE trace_query_statement;
