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

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class StudioAuthMigrationTest {

    private static final Path SCHEMA_PATH = Path.of("src/main/resources/db/schema.sql");
    private static final Path MIGRATION_PATH = Path.of("../deploy/mysql/upgrade-studio-auth.sql");

    @Test
    void migrationCreatesAuthTablesOnExistingSchemaAndIsIdempotentTest() throws Exception {
        String schema = Files.readString(SCHEMA_PATH);
        String migration = Files.readString(MIGRATION_PATH);
        String userTable = createTableStatement(migration, "rmq_studio_user");
        String sessionTable = createTableStatement(migration, "rmq_studio_session");

        assertThat(normalize(userTable))
                .isEqualTo(normalize(createTableStatement(schema, "rmq_studio_user")));
        assertThat(normalize(sessionTable))
                .isEqualTo(normalize(createTableStatement(schema, "rmq_studio_session")));

        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:studio-auth-migration;MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE legacy_settings (id BIGINT PRIMARY KEY, setting_value VARCHAR(32))");
            statement.execute("INSERT INTO legacy_settings VALUES (1, 'preserved')");

            executeMigration(statement, userTable, sessionTable);
            executeMigration(statement, userTable, sessionTable);

            assertThat(tableExists(connection, "RMQ_STUDIO_USER")).isTrue();
            assertThat(tableExists(connection, "RMQ_STUDIO_SESSION")).isTrue();
            try (ResultSet result = statement.executeQuery(
                    "SELECT setting_value FROM legacy_settings WHERE id = 1")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getString(1)).isEqualTo("preserved");
            }
        }
    }

    private static void executeMigration(Statement statement, String... createStatements) throws Exception {
        for (String createStatement : createStatements) {
            statement.execute(createStatement.replaceFirst(
                    "(?i)\\s+ENGINE=InnoDB\\s+DEFAULT\\s+CHARSET=utf8mb4;$", ";"));
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws Exception {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private static String createTableStatement(String sql, String tableName) {
        Pattern pattern = Pattern.compile(
                "(?is)CREATE\\s+TABLE\\s+IF\\s+NOT\\s+EXISTS\\s+" + Pattern.quote(tableName) + "\\s*\\(.*?\\)"
                        + "\\s+ENGINE=InnoDB\\s+DEFAULT\\s+CHARSET=utf8mb4;");
        Matcher matcher = pattern.matcher(sql);
        assertThat(matcher.find()).as("DDL for %s", tableName).isTrue();
        return matcher.group();
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
    }
}
