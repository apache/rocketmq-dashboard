-- deploy/mysql/upgrade-query-history-context.sql
-- Existing MySQL volumes: add query-history context columns.
-- Fresh volumes already receive these columns from server/src/main/resources/db/schema.sql.
--
-- Run once on existing deployments:
--   docker exec -i rocketmq-studio-mysql mysql -uroot -pstudio123 rocketmq_studio < upgrade-query-history-context.sql

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
