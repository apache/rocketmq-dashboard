/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Locale;

/** Adds query-history columns and indexes to Studio databases created before the current schema. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryHistorySchemaMigration implements ApplicationRunner {
    private static final List<Column> COLUMNS = List.of(
            new Column("rmq_instance_message", "result_snapshot", "MEDIUMTEXT"),
            new Column("rmq_instance_trace", "trace_topic", "VARCHAR(255)"));
    /**
     * Columns that store the acting username, and the width they must have. `db/schema.sql` widens
     * them for fresh databases only (it runs on a new MySQL data directory, or in the dev H2
     * profile); an existing database never re-runs it, and a column left at its old 64 characters
     * silently drops the query-history and audit rows of every longer username (AuthService allows
     * 128) - the insert fails and both writers only log at WARN.
     */
    private static final List<ColumnWidth> WIDTHS = List.of(
            new ColumnWidth("rmq_instance_message", "queried_by", 128),
            new ColumnWidth("rmq_instance_trace", "queried_by", 128),
            new ColumnWidth("rmq_operation_audit", "operator", 128));
    private static final List<Index> INDEXES = List.of(
            new Index("rmq_instance_message", "idx_message_query_owner_lookup",
                    "queried_by, cluster_id, gmt_create, id"),
            new Index("rmq_instance_message", "idx_message_query_owner_type_lookup",
                    "queried_by, cluster_id, query_type, gmt_create, id"),
            new Index("rmq_instance_trace", "idx_trace_query_owner_lookup",
                    "queried_by, cluster_id, gmt_create, id"));

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (Column column : COLUMNS) {
                ensureColumn(metadata, catalog, statement, column);
            }
            for (ColumnWidth width : WIDTHS) {
                ensureWidth(metadata, catalog, statement, width);
            }
            for (Index index : INDEXES) {
                ensureIndex(metadata, catalog, statement, index);
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
            log.info("Adding query history column {}.{}", column.table(), column.name());
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
        if (!hasTable(metadata, catalog, index.table()) || hasIndex(metadata, catalog, index.table(), index.name())) {
            return;
        }
        try {
            log.info("Adding query history index {}.{}", index.table(), index.name());
            statement.executeUpdate("CREATE INDEX " + index.name() + " ON " + index.table()
                    + " (" + index.columns() + ")");
        } catch (SQLException failure) {
            if (!hasIndex(metadata, catalog, index.table(), index.name())) {
                throw failure;
            }
        }
    }

    private static void ensureWidth(DatabaseMetaData metadata, String catalog, Statement statement,
                                    ColumnWidth width) throws Exception {
        if (!hasTable(metadata, catalog, width.table())) {
            return;
        }
        int current = columnWidth(metadata, catalog, width.table(), width.name());
        if (current == 0 || current >= width.width()) {
            return;
        }
        String product = metadata.getDatabaseProductName();
        String alter = product != null && product.toLowerCase(Locale.ROOT).contains("h2")
                ? "ALTER TABLE " + width.table() + " ALTER COLUMN " + width.name()
                        + " SET DATA TYPE VARCHAR(" + width.width() + ")"
                : "ALTER TABLE " + width.table() + " MODIFY " + width.name()
                        + " VARCHAR(" + width.width() + ")";
        try {
            log.info("Widening {}.{} from {} to {} characters", width.table(), width.name(), current,
                    width.width());
            statement.executeUpdate(alter);
        } catch (SQLException failure) {
            // A failed widening leaves the column as it was and the write path reports the resulting
            // data-too-long error; turning it into a startup failure would help nobody.
            log.warn("Could not widen {}.{}: {}", width.table(), width.name(), failure.getMessage());
        }
    }

    private static int columnWidth(DatabaseMetaData metadata, String catalog, String table, String column)
            throws Exception {
        try (ResultSet columns = metadata.getColumns(catalog, null, table, column)) {
            return columns.next() ? columns.getInt("COLUMN_SIZE") : 0;
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String table) throws Exception {
        try (ResultSet tables = metadata.getTables(catalog, null, table, new String[] {"TABLE"})) {
            return tables.next();
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

    private static boolean hasColumn(DatabaseMetaData metadata, String catalog, String table, String column)
            throws Exception {
        try (ResultSet columns = metadata.getColumns(catalog, null, table, column)) {
            return columns.next();
        }
    }

    private record Column(String table, String name, String definition) {
    }

    private record ColumnWidth(String table, String name, int width) {
    }

    private record Index(String table, String name, String columns) {
    }
}
