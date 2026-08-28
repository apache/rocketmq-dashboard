/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.instance.message;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QueryHistorySchemaIndexTest {
    @Test
    void freshSchemaCreatesUserScopedHistoryIndexesTest() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:query-history-schema-indexes;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }

        try (Connection connection = dataSource.getConnection()) {
            assertThat(indexColumns(connection, "rmq_instance_message", "idx_message_query_owner_lookup"))
                    .containsExactly("queried_by", "cluster_id", "gmt_create", "id");
            assertThat(indexColumns(connection, "rmq_instance_message", "idx_message_query_owner_type_lookup"))
                    .containsExactly("queried_by", "cluster_id", "query_type", "gmt_create", "id");
            assertThat(indexColumns(connection, "rmq_instance_trace", "idx_trace_query_owner_lookup"))
                    .containsExactly("queried_by", "cluster_id", "gmt_create", "id");
        }
    }

    @Test
    void migrationAddsUserScopedHistoryIndexesToExistingTablesTest() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:query-history-schema-index-migration;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_instance_message ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "query_type VARCHAR(32) NOT NULL, "
                    + "cluster_id VARCHAR(255), "
                    + "queried_by VARCHAR(128))");
            statement.execute("CREATE TABLE rmq_instance_trace ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                    + "cluster_id VARCHAR(255), "
                    + "queried_by VARCHAR(128))");
        }

        QueryHistorySchemaMigration migration = new QueryHistorySchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection()) {
            assertThat(indexColumns(connection, "rmq_instance_message", "idx_message_query_owner_lookup"))
                    .containsExactly("queried_by", "cluster_id", "gmt_create", "id");
            assertThat(indexColumns(connection, "rmq_instance_message", "idx_message_query_owner_type_lookup"))
                    .containsExactly("queried_by", "cluster_id", "query_type", "gmt_create", "id");
            assertThat(indexColumns(connection, "rmq_instance_trace", "idx_trace_query_owner_lookup"))
                    .containsExactly("queried_by", "cluster_id", "gmt_create", "id");
        }
    }

    private static List<String> indexColumns(Connection connection, String tableName, String indexName) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet result = statement.executeQuery("SELECT column_name FROM information_schema.index_columns "
                        + "WHERE table_name = '" + tableName + "' AND index_name = '" + indexName + "' "
                        + "ORDER BY ordinal_position")) {
            List<String> columns = new ArrayList<>();
            while (result.next()) {
                columns.add(result.getString(1));
            }
            return columns;
        }
    }
}
