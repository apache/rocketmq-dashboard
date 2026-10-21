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
package org.apache.rocketmq.studio.auth;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class StudioUserSchemaMigrationTest {

    @Test
    void addsRotationFlagToExistingUsersWithoutDroppingRowsAndIsIdempotent() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:studio-user-schema-migration;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_studio_user ("
                    + "id BIGINT PRIMARY KEY, username VARCHAR(128) NOT NULL, "
                    + "password_hash VARCHAR(512) NOT NULL, admin TINYINT(1) NOT NULL DEFAULT 0, "
                    + "enabled TINYINT(1) NOT NULL DEFAULT 1, "
                    + "password_changed_at TIMESTAMP NOT NULL, gmt_create TIMESTAMP, gmt_modified TIMESTAMP)");
            statement.execute("INSERT INTO rmq_studio_user (id, username, password_hash, password_changed_at) "
                    + "VALUES (7, 'operator', 'stored-hash', '2026-08-22 08:00:00')");
        }

        StudioUserSchemaMigration migration = new StudioUserSchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try (ResultSet columns = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                    + "WHERE table_name = 'rmq_studio_user' AND column_name = 'password_must_change'")) {
                columns.next();
                assertThat(columns.getInt(1)).isEqualTo(1);
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT username, password_must_change FROM rmq_studio_user")) {
                rows.next();
                assertThat(rows.getString("username")).isEqualTo("operator");
                // Pre-existing accounts owned their passwords under the previous behavior;
                // the upgrade must not force a rotation on them.
                assertThat(rows.getBoolean("password_must_change")).isFalse();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void skipsMissingUserTableSoTheMainSchemaCanCreateItLater() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:studio-user-schema-missing;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");

        StudioUserSchemaMigration migration = new StudioUserSchemaMigration(dataSource);

        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, "rmq_studio_user",
                     new String[] {"TABLE"})) {
            assertThat(tables.next()).isFalse();
        }
    }
}
