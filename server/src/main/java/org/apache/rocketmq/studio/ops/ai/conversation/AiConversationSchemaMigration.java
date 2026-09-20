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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

/**
 * Creates the AI conversation tables on databases that were initialised from an older
 * {@code db/schema.sql}. A fresh database gets them from {@code schema.sql} directly
 * (compose mounts it into MySQL's initdb directory and the dev profile feeds it to H2),
 * but an existing database never re-runs that file, so this runner is the upgrade path.
 *
 * <p>Both sources must stay equivalent; {@code AiConversationSchemaMigrationTest} asserts
 * that every table and index name listed here also appears in {@code schema.sql}.
 *
 * <p>{@code @Order(0)} puts this ahead of every other {@link ApplicationRunner}, in particular
 * {@code AiConversationService}'s startup reaper, which reads {@code rmq_ai_run} and would otherwise
 * be querying a table this runner is about to create.
 *
 * <p>The DDL strings below deliberately use the terse dialect-free form also used by
 * {@code AlertSchemaMigration}: no column COMMENTs and no {@code unsigned}, because the
 * dev profile executes them against H2. {@code schema.sql} carries the fully documented
 * MySQL form.
 */
@Slf4j
@Component
@Order(0)
@RequiredArgsConstructor
public class AiConversationSchemaMigration implements ApplicationRunner {

    private static final List<Table> TABLES = List.of(
            new Table("rmq_ai_conversation", "CREATE TABLE rmq_ai_conversation ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "title VARCHAR(512) NOT NULL, owner VARCHAR(128) NOT NULL, "
                    + "engine VARCHAR(16) NOT NULL, model VARCHAR(128) NOT NULL, "
                    + "`mode` VARCHAR(16) NOT NULL DEFAULT 'chat', instance_id VARCHAR(128), "
                    + "runtime_session_id VARCHAR(128), last_seq INT NOT NULL DEFAULT 0, "
                    + "archived TINYINT(1) NOT NULL DEFAULT 0)"),
            new Table("rmq_ai_run", "CREATE TABLE rmq_ai_run ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "conversation_id BIGINT NOT NULL, turn INT NOT NULL, status VARCHAR(16) NOT NULL, "
                    + "engine VARCHAR(16) NOT NULL, model VARCHAR(128) NOT NULL, "
                    + "runtime_session_id VARCHAR(128), resumed_from VARCHAR(128), "
                    + "started_at DATETIME, finished_at DATETIME, duration_ms BIGINT, "
                    + "input_tokens INT, output_tokens INT, tokens_per_second DOUBLE, "
                    + "start_seq INT NOT NULL DEFAULT 0, end_seq INT NOT NULL DEFAULT 0, "
                    + "stop_reason VARCHAR(32), error_code VARCHAR(64), error_message VARCHAR(1024))"),
            new Table("rmq_ai_event", "CREATE TABLE rmq_ai_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                    + "conversation_id BIGINT NOT NULL, run_id BIGINT NOT NULL, turn INT NOT NULL, "
                    + "seq INT NOT NULL, type VARCHAR(32) NOT NULL, payload MEDIUMTEXT NOT NULL)"));

    private static final List<Index> INDEXES = List.of(
            new Index("rmq_ai_conversation", "idx_ai_conversation_owner", "owner, archived, gmt_modified, id"),
            new Index("rmq_ai_conversation", "idx_ai_conversation_cleanup", "gmt_create, id"),
            new Index("rmq_ai_run", "uk_ai_run_conversation_turn", "conversation_id, turn", true),
            new Index("rmq_ai_run", "idx_ai_run_conversation", "conversation_id, id"),
            new Index("rmq_ai_run", "idx_ai_run_active", "status, gmt_modified"),
            new Index("rmq_ai_run", "idx_ai_run_cleanup", "gmt_create, id"),
            new Index("rmq_ai_event", "uk_ai_event_conversation_seq", "conversation_id, seq", true),
            new Index("rmq_ai_event", "idx_ai_event_run", "run_id, seq"),
            new Index("rmq_ai_event", "idx_ai_event_cleanup", "gmt_create, id"));

    /** Columns added after the tables first shipped; existing databases get them by ALTER TABLE. */
    private static final List<Column> COLUMNS = List.of(
            new Column("rmq_ai_run", "tokens_per_second", "DOUBLE"));

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (Table table : TABLES) {
                ensureTable(metadata, catalog, statement, table);
            }
            for (Column column : COLUMNS) {
                ensureColumn(metadata, catalog, statement, column);
            }
            for (Index index : INDEXES) {
                ensureIndex(metadata, catalog, statement, index);
            }
        }
    }

    private static void ensureTable(DatabaseMetaData metadata, String catalog, Statement statement, Table table)
            throws Exception {
        if (hasTable(metadata, catalog, table.name())) {
            return;
        }
        try {
            log.info("Creating AI conversation table {}", table.name());
            statement.executeUpdate(table.definition());
        } catch (SQLException failure) {
            // Another instance may have created it between our probe and our DDL. Only
            // swallow the failure when a re-probe shows the table now exists.
            if (!hasTable(metadata, catalog, table.name())) {
                throw failure;
            }
        }
    }

    private static void ensureColumn(DatabaseMetaData metadata, String catalog, Statement statement, Column column)
            throws Exception {
        if (!hasTable(metadata, catalog, column.table())
                || hasColumn(metadata, catalog, column.table(), column.name())) {
            return;
        }
        try {
            log.info("Adding AI conversation column {}.{}", column.table(), column.name());
            statement.executeUpdate("ALTER TABLE " + column.table() + " ADD COLUMN " + column.name()
                    + " " + column.definition());
        } catch (SQLException failure) {
            if (!hasColumn(metadata, catalog, column.table(), column.name())) {
                throw failure;
            }
        }
    }

    private static void ensureIndex(DatabaseMetaData metadata, String catalog, Statement statement, Index index)
            throws Exception {
        if (!hasTable(metadata, catalog, index.table())) {
            return;
        }
        if (hasIndex(metadata, catalog, index.table(), index.name())) {
            return;
        }
        try {
            log.info("Adding AI conversation index {}.{}", index.table(), index.name());
            statement.executeUpdate("CREATE " + (index.unique() ? "UNIQUE " : "") + "INDEX " + index.name()
                    + " ON " + index.table() + " (" + index.columns() + ")");
        } catch (SQLException failure) {
            if (!hasIndex(metadata, catalog, index.table(), index.name())) {
                throw failure;
            }
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String table) throws Exception {
        try (ResultSet tables = metadata.getTables(catalog, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean hasColumn(DatabaseMetaData metadata, String catalog, String table, String column)
            throws Exception {
        try (ResultSet columns = metadata.getColumns(catalog, null, table, column)) {
            return columns.next();
        }
    }

    private static boolean hasIndex(DatabaseMetaData metadata, String catalog, String table, String index)
            throws Exception {
        try (ResultSet indexes = metadata.getIndexInfo(catalog, null, table, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private record Table(String name, String definition) {
    }

    private record Column(String table, String name, String definition) {
    }

    private record Index(String table, String name, String columns, boolean unique) {
        private Index(String table, String name, String columns) {
            this(table, name, columns, false);
        }
    }
}
