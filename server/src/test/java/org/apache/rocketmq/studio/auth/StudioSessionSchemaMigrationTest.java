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

class StudioSessionSchemaMigrationTest {

    @Test
    void addsAttributionColumnsToExistingSessionsWithoutDroppingRowsAndIsIdempotent() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:studio-session-schema-migration;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_studio_session ("
                    + "id BIGINT PRIMARY KEY, user_id BIGINT NOT NULL, token_hash CHAR(64) NOT NULL, "
                    + "expires_at TIMESTAMP NOT NULL, revoked_at TIMESTAMP, "
                    + "last_seen_at TIMESTAMP NOT NULL, gmt_create TIMESTAMP, gmt_modified TIMESTAMP)");
            statement.execute("INSERT INTO rmq_studio_session (id, user_id, token_hash, expires_at, last_seen_at) "
                    + "VALUES (1, 7, 'hash-of-existing-token', '2027-01-01 00:00:00', '2026-10-01 00:00:00')");
        }

        StudioSessionSchemaMigration migration = new StudioSessionSchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String column : new String[] {"client_ip", "user_agent"}) {
                try (ResultSet columns = statement.executeQuery("SELECT COUNT(*) FROM information_schema.columns "
                        + "WHERE table_name = 'rmq_studio_session' AND column_name = '" + column + "'")) {
                    columns.next();
                    assertThat(columns.getInt(1)).as("column %s", column).isEqualTo(1);
                }
            }
            try (ResultSet rows = statement.executeQuery(
                    "SELECT token_hash, client_ip, user_agent FROM rmq_studio_session")) {
                rows.next();
                assertThat(rows.getString("token_hash")).isEqualTo("hash-of-existing-token");
                assertThat(rows.getString("client_ip")).isNull();
                assertThat(rows.getString("user_agent")).isNull();
                assertThat(rows.next()).isFalse();
            }
        }
    }

    @Test
    void skipsMissingSessionTableSoTheMainSchemaCanCreateItLater() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:studio-session-schema-missing;MODE=MySQL;"
                + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");

        StudioSessionSchemaMigration migration = new StudioSessionSchemaMigration(dataSource);

        migration.run(new DefaultApplicationArguments());

        try (Connection connection = dataSource.getConnection();
             ResultSet tables = connection.getMetaData().getTables(null, null, "rmq_studio_session",
                     new String[] {"TABLE"})) {
            assertThat(tables.next()).isFalse();
        }
    }
}
