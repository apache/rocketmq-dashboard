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

/** Adds the session client-attribution columns to Studio databases created before the current schema. */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudioSessionSchemaMigration implements ApplicationRunner {
    private static final List<Column> COLUMNS = List.of(
            new Column("rmq_studio_session", "client_ip", "VARCHAR(64)"),
            new Column("rmq_studio_session", "user_agent", "VARCHAR(255)"));

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (Column column : COLUMNS) {
                ensureColumn(metadata, catalog, statement, column);
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
            log.info("Adding studio session column {}.{}", column.table(), column.name());
            statement.executeUpdate("ALTER TABLE " + column.table() + " ADD COLUMN " + column.name()
                    + " " + column.definition());
        } catch (SQLException failure) {
            if (!hasColumn(metadata, catalog, column.table(), column.name())) {
                throw failure;
            }
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String table) throws Exception {
        try (ResultSet tables = metadata.getTables(catalog, null, table, new String[]{"TABLE"})) {
            return tables.next();
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
}
