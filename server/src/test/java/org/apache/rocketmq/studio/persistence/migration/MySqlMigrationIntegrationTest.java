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
package org.apache.rocketmq.studio.persistence.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class MySqlMigrationIntegrationTest {

    @Container
    private static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("rocketmq")
            .withUsername("studio")
            .withPassword("studio");

    @BeforeAll
    static void createLegacySchema() throws SQLException {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    CREATE TABLE rmq_nameserver (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      name VARCHAR(128) NOT NULL,
                      namesrv_addr VARCHAR(512) NOT NULL,
                      cluster_type VARCHAR(32) DEFAULT 'V5_PROXY_CLUSTER'
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_instance (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      name VARCHAR(128) NOT NULL,
                      type VARCHAR(32) NOT NULL,
                      endpoint VARCHAR(512) NOT NULL,
                      region_id VARCHAR(128)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_instance_topic (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      cluster_id VARCHAR(64) NOT NULL,
                      instance_id BIGINT UNSIGNED,
                      name VARCHAR(255) NOT NULL,
                      UNIQUE KEY uk_cluster_topic (cluster_id, name),
                      KEY idx_topic_instance (instance_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_instance_group (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      cluster_id VARCHAR(64) NOT NULL,
                      instance_id BIGINT UNSIGNED,
                      name VARCHAR(255) NOT NULL,
                      UNIQUE KEY uk_cluster_group (cluster_id, name),
                      KEY idx_group_instance (instance_id)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_k8s_certificate (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      name VARCHAR(128) NOT NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_instance_message (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      query_type VARCHAR(32) NOT NULL,
                      queried_by VARCHAR(64)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_instance_trace (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      msg_id VARCHAR(128) NOT NULL,
                      queried_by VARCHAR(64)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_operation_audit (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      operation VARCHAR(64) NOT NULL,
                      resource_type VARCHAR(64) NOT NULL,
                      operator VARCHAR(64)
                    )
                    """);
            statement.execute("""
                    CREATE TABLE rmq_acl_user (
                      id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                      gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
                      gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                      username VARCHAR(128) NOT NULL,
                      access_key VARCHAR(255) NOT NULL,
                      secret_key VARCHAR(512) NOT NULL,
                      admin TINYINT(1) DEFAULT 0,
                      clusters VARCHAR(1024),
                      white_remote_address VARCHAR(255),
                      UNIQUE KEY uk_username (username)
                    )
                    """);
            statement.execute("INSERT INTO rmq_instance (id, name, type, endpoint) VALUES (7, 'instance-a', 'DIRECT', '127.0.0.1:9876')");
            statement.execute("INSERT INTO rmq_instance_topic (cluster_id, instance_id, name) VALUES ('cluster-a', 7, 'TopicA')");
            statement.execute("INSERT INTO rmq_instance_group (cluster_id, instance_id, name) VALUES ('cluster-a', 7, 'GroupA')");
            statement.execute("INSERT INTO rmq_k8s_certificate (name) VALUES ('k8s-a')");
            statement.execute("INSERT INTO rmq_acl_user (username, access_key, secret_key) VALUES ('admin', 'access-a', 'secret')");
        }
        try (Connection connection = rootConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE DATABASE rocketmq_fresh CHARACTER SET utf8mb4");
            statement.execute("CREATE DATABASE rocketmq_unsupported CHARACTER SET utf8mb4");
            statement.execute("""
                    CREATE TABLE rocketmq_unsupported.rmq_instance (
                      id VARCHAR(64) PRIMARY KEY,
                      name VARCHAR(128) NOT NULL,
                      type VARCHAR(32) NOT NULL,
                      endpoint VARCHAR(512) NOT NULL
                    )
                    """);
        }
    }

    @Test
    void upgradesExistingDatabaseAndRemainsIdempotentTest() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        MigrateResult first = flyway.migrate();

        assertThat(first.migrationsExecuted).isEqualTo(2);
        assertThat(columnExists("rmq_nameserver", "k8s_namespace")).isTrue();
        assertThat(columnExists("rmq_nameserver", "k8s_id")).isTrue();
        assertThat(columnType("rmq_instance_topic", "instance_id")).isEqualTo("varchar");
        assertThat(columnValue("rmq_instance_topic", "instance_id")).isEqualTo("instance-a");
        assertThat(indexExists("rmq_instance_topic", "uk_cluster_instance_topic")).isTrue();
        assertThat(columnType("rmq_instance_group", "instance_id")).isEqualTo("varchar");
        assertThat(columnValue("rmq_instance_group", "instance_id")).isEqualTo("instance-a");
        assertThat(indexExists("rmq_instance_group", "uk_cluster_instance_group")).isTrue();
        assertThat(columnExists("rmq_k8s_certificate", "k8s_id")).isTrue();
        assertThat(columnExists("rmq_k8s_certificate", "name")).isFalse();
        assertThat(columnValue("rmq_k8s_certificate", "k8s_id")).isEqualTo("k8s-a");
        assertThat(columnLength("rmq_instance_message", "queried_by")).isEqualTo(128);
        assertThat(columnLength("rmq_instance_trace", "queried_by")).isEqualTo(128);
        assertThat(columnLength("rmq_operation_audit", "operator")).isEqualTo(128);
        assertThat(indexExists("rmq_acl_user", "uk_access_key")).isTrue();
        assertThat(tableExists("rmq_studio_user")).isTrue();
        assertThat(tableExists("rmq_studio_session")).isTrue();

        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(flyway.info().pending()).isEmpty();
    }

    @Test
    void createsFreshDatabaseFromCurrentBaselineTest() throws SQLException {
        Flyway flyway = Flyway.configure()
                .dataSource(freshJdbcUrl(), "root", MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);
        try (Connection connection = DriverManager.getConnection(
                freshJdbcUrl(), "root", MYSQL.getPassword())) {
            assertThat(queryForInt(connection, "SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE table_schema = DATABASE() AND table_name = 'rmq_studio_user'"))
                    .isEqualTo(1);
            assertThat(queryForString(connection, "SELECT data_type FROM information_schema.columns "
                    + "WHERE table_schema = DATABASE() AND table_name = 'rmq_instance_topic' "
                    + "AND column_name = 'instance_id'"))
                    .isEqualTo("varchar");
        }
    }

    @Test
    void rejectsPreNumericIdSchemaBeforePartialUpgradeTest() {
        Flyway flyway = Flyway.configure()
                .dataSource(unsupportedJdbcUrl(), "root", MYSQL.getPassword())
                .locations("classpath:db/migration/mysql")
                .baselineOnMigrate(true)
                .baselineVersion("0")
                .load();

        assertThatThrownBy(flyway::migrate)
                .hasMessageContaining("rocketmq_studio_upgrade_requires_numeric_id_schema");
    }

    private static boolean tableExists(String table) throws SQLException {
        return exists("""
                SELECT COUNT(*) FROM information_schema.tables
                 WHERE table_schema = DATABASE() AND table_name = '%s'
                """.formatted(table));
    }

    private static boolean columnExists(String table, String column) throws SQLException {
        return exists("""
                SELECT COUNT(*) FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = '%s' AND column_name = '%s'
                """.formatted(table, column));
    }

    private static boolean indexExists(String table, String index) throws SQLException {
        return exists("""
                SELECT COUNT(*) FROM information_schema.statistics
                 WHERE table_schema = DATABASE() AND table_name = '%s' AND index_name = '%s'
                """.formatted(table, index));
    }

    private static int columnLength(String table, String column) throws SQLException {
        return queryForInt("""
                SELECT character_maximum_length FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = '%s' AND column_name = '%s'
                """.formatted(table, column));
    }

    private static String columnType(String table, String column) throws SQLException {
        return queryForString("""
                SELECT data_type FROM information_schema.columns
                 WHERE table_schema = DATABASE() AND table_name = '%s' AND column_name = '%s'
                """.formatted(table, column));
    }

    private static String columnValue(String table, String column) throws SQLException {
        return queryForString("SELECT %s FROM %s LIMIT 1".formatted(column, table));
    }

    private static boolean exists(String query) throws SQLException {
        return queryForInt(query) > 0;
    }

    private static int queryForInt(String query) throws SQLException {
        try (Connection connection = connection()) {
            return queryForInt(connection, query);
        }
    }

    private static int queryForInt(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static String queryForString(String query) throws SQLException {
        try (Connection connection = connection()) {
            return queryForString(connection, query);
        }
    }

    private static String queryForString(Connection connection, String query) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(query)) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private static String freshJdbcUrl() {
        return MYSQL.getJdbcUrl().replace("/rocketmq?", "/rocketmq_fresh?");
    }

    private static String unsupportedJdbcUrl() {
        return MYSQL.getJdbcUrl().replace("/rocketmq?", "/rocketmq_unsupported?");
    }

    private static Connection connection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    private static Connection rootConnection() throws SQLException {
        return DriverManager.getConnection(MYSQL.getJdbcUrl(), "root", MYSQL.getPassword());
    }
}
