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
package org.apache.rocketmq.studio.persistence;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class DemoDataSqlCompatibilityTest {

    @Test
    void demoScriptsMatchCanonicalSchemaAndRemainIdempotent() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:demo-data-sql;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));

            for (int pass = 0; pass < 2; pass++) {
                executeDeployScript(connection, "upgrade-demo-instance.sql");
                executeDeployScript(connection, "upgrade-demo-acl.sql");
            }

            assertThat(count(connection, "rmq_instance")).isEqualTo(5);
            assertThat(count(connection, "rmq_instance_topic")).isEqualTo(19);
            assertThat(count(connection, "rmq_instance_group")).isEqualTo(17);
            assertThat(count(connection, "rmq_acl_rule")).isEqualTo(13);
            assertThat(count(connection, "rmq_acl_user")).isEqualTo(8);
            assertThat(countOrphanedMetadata(connection, "rmq_instance_topic")).isZero();
            assertThat(countOrphanedMetadata(connection, "rmq_instance_group")).isZero();
            assertThat(countNumericMetadataReferences(connection, "rmq_instance_topic")).isZero();
            assertThat(countNumericMetadataReferences(connection, "rmq_instance_group")).isZero();
            assertThat(allIdsAreNumeric(connection, "rmq_instance")).isTrue();
            assertThat(allIdsAreNumeric(connection, "rmq_acl_rule")).isTrue();
            assertThat(allIdsAreNumeric(connection, "rmq_acl_user")).isTrue();
        }
    }

    @Test
    void alertCompatibilityHelperCreatesCanonicalNumericTables() throws Exception {
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:demo-alert-sql;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "")) {
            executeDeployScript(connection, "upgrade-demo-alert.sql");
            executeDeployScript(connection, "upgrade-demo-alert.sql");

            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO rmq_alert_rule (name, enabled) VALUES ('demo-rule', TRUE)");
                statement.executeUpdate("INSERT INTO rmq_system_alert (level, title, acknowledged)"
                        + " VALUES ('warning', 'demo-alert', FALSE)");
            }

            assertThat(count(connection, "rmq_alert_rule")).isEqualTo(1);
            assertThat(count(connection, "rmq_system_alert")).isEqualTo(1);
            assertThat(allIdsAreNumeric(connection, "rmq_alert_rule")).isTrue();
            assertThat(allIdsAreNumeric(connection, "rmq_system_alert")).isTrue();
            assertThat(hasColumn(connection, "rmq_alert_rule", "gmt_create")).isTrue();
            assertThat(hasColumn(connection, "rmq_alert_rule", "gmt_modified")).isTrue();
            assertThat(hasColumn(connection, "rmq_alert_rule", "created_at")).isFalse();
            assertThat(hasColumn(connection, "rmq_system_alert", "updated_at")).isFalse();
        }
    }

    private static void executeDeployScript(Connection connection, String filename) throws IOException {
        String script = Files.readString(resolveDeployScript(filename), StandardCharsets.UTF_8)
                .replace("SET NAMES utf8mb4;", "");
        ScriptUtils.executeSqlScript(connection,
                new ByteArrayResource(script.getBytes(StandardCharsets.UTF_8), filename));
    }

    private static Path resolveDeployScript(String filename) {
        Path fromModule = Path.of("..", "deploy", "mysql", filename);
        if (Files.isRegularFile(fromModule)) {
            return fromModule;
        }
        Path fromRepository = Path.of("deploy", "mysql", filename);
        assertThat(Files.isRegularFile(fromRepository))
                .as("deploy SQL script %s must be available from the Maven working directory", filename)
                .isTrue();
        return fromRepository;
    }

    private static long count(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table)) {
            result.next();
            return result.getLong(1);
        }
    }

    private static long countOrphanedMetadata(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table
                     + " child LEFT JOIN rmq_instance parent ON parent.name = child.instance_id"
                     + " WHERE parent.id IS NULL")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static long countNumericMetadataReferences(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table
                     + " child JOIN rmq_instance parent ON CAST(parent.id AS VARCHAR) = child.instance_id")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static boolean allIdsAreNumeric(Connection connection, String table) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + table + " WHERE id <= 0")) {
            result.next();
            return result.getLong(1) == 0;
        }
    }

    private static boolean hasColumn(Connection connection, String table, String column) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, table, column)) {
            return columns.next();
        }
    }
}
