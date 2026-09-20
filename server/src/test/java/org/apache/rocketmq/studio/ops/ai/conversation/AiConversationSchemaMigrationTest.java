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
package org.apache.rocketmq.studio.ops.ai.conversation;

import org.apache.rocketmq.studio.ops.ai.conversation.event.RunStatus;
import org.apache.rocketmq.studio.ops.ai.conversation.event.StopReason;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AiConversationSchemaMigrationTest {

    private static final List<String> TABLES = List.of("rmq_ai_conversation", "rmq_ai_run", "rmq_ai_event");

    /**
     * Index names the migration creates. H2 renames an inline {@code UNIQUE KEY uk_x} from
     * CREATE TABLE to {@code uk_x_INDEX_<n>}, while a standalone {@code CREATE UNIQUE INDEX}
     * keeps the exact name, so these are asserted by prefix rather than equality.
     */
    private static final List<String> INDEXES = List.of(
            "idx_ai_conversation_owner", "idx_ai_conversation_cleanup",
            "uk_ai_run_conversation_turn", "idx_ai_run_conversation", "idx_ai_run_active", "idx_ai_run_cleanup",
            "uk_ai_event_conversation_seq", "idx_ai_event_run", "idx_ai_event_cleanup");

    @Test
    void createsAiConversationTablesIdempotentlyTest() throws Exception {
        JdbcDataSource dataSource = newDataSource("ai-conversation-schema-migration");

        AiConversationSchemaMigration migration = new AiConversationSchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection()) {
            assertThat(tableNames(connection)).containsExactlyInAnyOrderElementsOf(TABLES);
            assertThat(columnNames(connection, "rmq_ai_conversation")).contains(
                    "id", "gmt_create", "gmt_modified", "title", "owner", "engine", "model", "mode",
                    "instance_id", "runtime_session_id", "last_seq", "archived");
            assertThat(columnNames(connection, "rmq_ai_run")).contains(
                    "id", "gmt_create", "gmt_modified", "conversation_id", "turn", "status", "engine", "model",
                    "runtime_session_id", "resumed_from", "started_at", "finished_at", "duration_ms",
                    "input_tokens", "output_tokens", "start_seq", "end_seq", "stop_reason",
                    "error_code", "error_message");
            // turn is a deliberate denormalisation: the timeline read must not join rmq_ai_run.
            assertThat(columnNames(connection, "rmq_ai_event")).contains(
                    "id", "gmt_create", "gmt_modified", "conversation_id", "run_id", "turn", "seq", "type",
                    "payload");
            for (String index : INDEXES) {
                assertThat(indexNames(connection))
                        .as("index %s should exist", index)
                        .anyMatch(name -> name.startsWith(index));
            }
        }
    }

    @Test
    void freshSchemaSqlCreatesTheSameAiConversationTablesTest() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:ai-conversation-fresh-schema;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));

            assertThat(tableNames(connection)).containsAll(TABLES);
            for (String index : INDEXES) {
                assertThat(indexNames(connection))
                        .as("schema.sql should create index %s", index)
                        .anyMatch(name -> name.startsWith(index));
            }
            // The timeline ordering key must survive, otherwise every history load filesorts.
            assertThat(indexColumns(connection, "rmq_ai_conversation", "idx_ai_conversation_owner"))
                    .containsExactly("owner", "archived", "gmt_modified", "id");
            assertThat(indexColumns(connection, "rmq_ai_event", "uk_ai_event_conversation_seq"))
                    .containsExactly("conversation_id", "seq");
        }
    }

    @Test
    void everyMigrationObjectNameAppearsInSchemaSqlTest() throws Exception {
        String ddl = schemaSql();
        // schema.sql is the fresh-database source and the migration is the upgrade path for
        // databases initialised from an older schema.sql. Neither re-runs the other, so a name
        // present in only one of them is silent drift.
        for (String name : concat(TABLES, INDEXES)) {
            assertThat(ddl).as("schema.sql should mention %s", name).contains(name);
        }
    }

    /**
     * The shared cross-language fixture exercises COMPLETED / STOPPED and USER_STOP only, so the
     * remaining run statuses and stop reasons would be verified by no test at all. The column
     * COMMENT is the DDL's documentation of that same vocabulary and the only thing an operator has
     * to interpret a stored value, so enum and comment must agree; this keeps them in step without
     * inventing fixture rows for states a real run rarely reaches.
     */
    @Test
    void everyRunStatusAndStopReasonIsDocumentedInSchemaSqlTest() throws Exception {
        String ddl = schemaSql();

        String statusComment = columnComment(ddl, "rmq_ai_run", "status");
        for (RunStatus status : RunStatus.values()) {
            assertThat(statusComment)
                    .as("rmq_ai_run.status COMMENT should document %s", status.name())
                    .contains(status.name());
        }

        String stopReasonComment = columnComment(ddl, "rmq_ai_run", "stop_reason");
        for (StopReason reason : StopReason.values()) {
            assertThat(stopReasonComment)
                    .as("rmq_ai_run.stop_reason COMMENT should document %s", reason.name())
                    .contains(reason.name());
        }
    }

    private static String schemaSql() throws IOException {
        try (InputStream in = new ClassPathResource("db/schema.sql").getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /**
     * The COMMENT of one column, matched inside that table's own CREATE TABLE body: several tables
     * have a {@code status} column and only this one documents the run lifecycle.
     */
    private static String columnComment(String ddl, String table, String column) {
        int start = ddl.indexOf("CREATE TABLE IF NOT EXISTS " + table + " (");
        assertThat(start).as("schema.sql should create %s", table).isNotNegative();
        int end = ddl.indexOf(") ENGINE=", start);
        assertThat(end).as("%s should declare its storage engine", table).isGreaterThan(start);
        Matcher matcher = Pattern.compile(
                "^[ \\t]*`?" + Pattern.quote(column) + "`?\\s[^\\n]*?COMMENT\\s+'([^']*)'",
                Pattern.MULTILINE).matcher(ddl.substring(start, end));
        assertThat(matcher.find())
                .as("%s.%s should carry a COMMENT", table, column)
                .isTrue();
        return matcher.group(1);
    }

    private static JdbcDataSource newDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        return dataSource;
    }

    private static List<String> concat(List<String> a, List<String> b) {
        List<String> all = new ArrayList<>(a);
        all.addAll(b);
        return all;
    }

    private static List<String> tableNames(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        for (String table : TABLES) {
            try (ResultSet tables = metadata.getTables(connection.getCatalog(), null, table, new String[] {"TABLE"})) {
                if (tables.next()) {
                    names.add(table);
                }
            }
        }
        return names;
    }

    private static List<String> columnNames(Connection connection, String table) throws SQLException {
        List<String> names = new ArrayList<>();
        try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, table, null)) {
            while (columns.next()) {
                names.add(columns.getString("COLUMN_NAME"));
            }
        }
        return names;
    }

    private static List<String> indexNames(Connection connection) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery(
                        "SELECT DISTINCT index_name FROM information_schema.indexes")) {
            while (result.next()) {
                names.add(result.getString(1));
            }
        }
        return names;
    }

    private static List<String> indexColumns(Connection connection, String table, String indexPrefix)
            throws SQLException {
        List<String> columns = new ArrayList<>();
        DatabaseMetaData metadata = connection.getMetaData();
        try (ResultSet indexes = metadata.getIndexInfo(connection.getCatalog(), null, table, false, false)) {
            while (indexes.next()) {
                String indexName = indexes.getString("INDEX_NAME");
                if (indexName != null && indexName.startsWith(indexPrefix)) {
                    columns.add(indexes.getString("COLUMN_NAME"));
                }
            }
        }
        return columns;
    }
}
