-- 命名查询共享保存，不计入执行历史，也不受历史清理任务影响。
CREATE TABLE IF NOT EXISTS rmq_named_message_query (
  id VARCHAR(36) NOT NULL PRIMARY KEY,
  instance_id BIGINT UNSIGNED NOT NULL,
  name VARCHAR(80) NOT NULL,
  normalized_name VARCHAR(80) NOT NULL,
  mode VARCHAR(10) NOT NULL,
  topic VARCHAR(1024) NOT NULL,
  message_key VARCHAR(1024),
  msg_id VARCHAR(1024),
  start_time BIGINT,
  end_time BIGINT,
  created_at BIGINT NOT NULL,
  updated_at BIGINT NOT NULL,
  created_by VARCHAR(128) NOT NULL,
  CONSTRAINT uk_named_message_query UNIQUE (instance_id, normalized_name),
  CONSTRAINT fk_named_message_query_instance FOREIGN KEY (instance_id) REFERENCES rmq_instance(id) ON DELETE CASCADE
);
