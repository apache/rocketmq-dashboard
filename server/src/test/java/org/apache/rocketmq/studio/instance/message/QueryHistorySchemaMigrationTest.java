/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.instance.message;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class QueryHistorySchemaMigrationTest {

    @Test
    void addsTraceTopicToExistingHistoryWithoutDroppingRowsAndIsIdempotent() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:trace-history-schema-migration;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_instance_trace ("
                    + "id BIGINT PRIMARY KEY, msg_id VARCHAR(128) NOT NULL, topic VARCHAR(255), "
                    + "node_count INT, consumer_count INT, cluster_id VARCHAR(255), "
                    + "queried_by VARCHAR(128), gmt_create TIMESTAMP, gmt_modified TIMESTAMP)");
            statement.execute("INSERT INTO rmq_instance_trace (id, msg_id, topic) "
                    + "VALUES (1, 'old-msg', 'orders')");
        }

        QueryHistorySchemaMigration migration = new QueryHistorySchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet columns = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_name = 'rmq_instance_trace' AND column_name = 'trace_topic'")) {
                columns.next();
                assertThat(columns.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rows = statement.executeQuery("SELECT msg_id, trace_topic FROM rmq_instance_trace")) {
                rows.next();
                assertThat(rows.getString("msg_id")).isEqualTo("old-msg");
                assertThat(rows.getString("trace_topic")).isNull();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void skipsMissingHistoryTableSoCustomSchemaCanCreateItLater() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:trace-history-schema-missing;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");

        QueryHistorySchemaMigration migration = new QueryHistorySchemaMigration(dataSource);

        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement();
                ResultSet tables = connection.getMetaData().getTables(null, null, "rmq_instance_trace",
                        new String[] {"TABLE"})) {
            assertThat(tables.next()).isFalse();
        }
    }

    @Test
    void addsMessageSnapshotColumnAndAllOwnerIndexesIdempotently() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:query-history-schema-migration;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_instance_message ("
                    + "id BIGINT PRIMARY KEY, queried_by VARCHAR(128), cluster_id VARCHAR(255), "
                    + "query_type VARCHAR(32), gmt_create TIMESTAMP, gmt_modified TIMESTAMP)");
            statement.execute("CREATE TABLE rmq_instance_trace ("
                    + "id BIGINT PRIMARY KEY, queried_by VARCHAR(128), cluster_id VARCHAR(255), "
                    + "gmt_create TIMESTAMP, gmt_modified TIMESTAMP)");
        }

        QueryHistorySchemaMigration migration = new QueryHistorySchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet columns = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_name = 'rmq_instance_message' AND column_name = 'result_snapshot'")) {
                columns.next();
                assertThat(columns.getInt(1)).isEqualTo(1);
            }
            assertThat(hasIndex(connection, "rmq_instance_message", "idx_message_query_owner_lookup")).isTrue();
            assertThat(hasIndex(connection, "rmq_instance_message", "idx_message_query_owner_type_lookup")).isTrue();
            assertThat(hasIndex(connection, "rmq_instance_trace", "idx_trace_query_owner_lookup")).isTrue();
        }
    }

    private static boolean hasIndex(Connection connection, String table, String index) throws Exception {
        try (ResultSet indexes = connection.getMetaData().getIndexInfo(
                connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
        }
        return false;
    }
}
