/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0.
 */
package org.apache.rocketmq.studio.provider.credential;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudCredentialSchemaMigrationTest {

    @Test
    void migrationShouldAddCredentialForeignKeyToExistingCleanDatabaseTest() throws Exception {
        JdbcDataSource dataSource = dataSource("clean");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            createLegacyTables(statement);
            statement.executeUpdate("INSERT INTO rmq_cloud_credential (id, name, vendor, access_key, secret_key)"
                    + " VALUES (1, 'aliyun', 'ALIYUN', 'ak', 'sk')");
            statement.executeUpdate("INSERT INTO rmq_instance (id, name, type, endpoint, vendor, credential_id)"
                    + " VALUES (10, 'cloud-a', 'CLOUD', 'endpoint', 'ALIYUN', 1)");
        }

        CloudCredentialSchemaMigration migration = new CloudCredentialSchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection()) {
            assertThat(hasImportedKey(connection)).isTrue();
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("DELETE FROM rmq_cloud_credential WHERE id = 1");
                }
            }).hasMessageContaining("constraint");
        }
    }

    @Test
    void migrationShouldRejectOrphanedCredentialReferencesBeforeAddingForeignKeyTest() throws Exception {
        JdbcDataSource dataSource = dataSource("orphan");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            createLegacyTables(statement);
            statement.executeUpdate("INSERT INTO rmq_instance (id, name, type, endpoint, vendor, credential_id)"
                    + " VALUES (10, 'cloud-a', 'CLOUD', 'endpoint', 'ALIYUN', 99)");
        }

        CloudCredentialSchemaMigration migration = new CloudCredentialSchemaMigration(dataSource);

        assertThatThrownBy(() -> migration.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing cloud credentials")
                .hasMessageContaining("1");
    }

    private static JdbcDataSource dataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:cloud-credential-schema-" + name
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        return dataSource;
    }

    private static void createLegacyTables(Statement statement) throws Exception {
        statement.execute("CREATE TABLE rmq_instance ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "name VARCHAR(128) NOT NULL, type VARCHAR(32) NOT NULL, endpoint VARCHAR(512) NOT NULL, "
                + "vendor VARCHAR(32), credential_id BIGINT)");
        statement.execute("CREATE TABLE rmq_cloud_credential ("
                + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "gmt_create DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP, "
                + "gmt_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, "
                + "name VARCHAR(128) NOT NULL, vendor VARCHAR(32) NOT NULL, access_key VARCHAR(255) NOT NULL, "
                + "secret_key VARCHAR(512) NOT NULL)");
    }

    private static boolean hasImportedKey(Connection connection) throws Exception {
        try (ResultSet keys = connection.getMetaData().getImportedKeys(null, null, "rmq_instance")) {
            while (keys.next()) {
                if ("fk_instance_cloud_credential".equalsIgnoreCase(keys.getString("FK_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
