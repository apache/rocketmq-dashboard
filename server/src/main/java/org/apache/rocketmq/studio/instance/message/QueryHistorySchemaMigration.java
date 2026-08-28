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

/** Adds query-history indexes to Studio databases created before the index contract was added. */
@Slf4j
@Component
@RequiredArgsConstructor
public class QueryHistorySchemaMigration implements ApplicationRunner {
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
            for (Index index : INDEXES) {
                ensureIndex(metadata, catalog, statement, index);
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

    private record Index(String table, String name, String columns) {
    }
}
