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

/**
 * Adds the forced-rotation flag to Studio user rows created before the current schema.
 * Existing accounts default to "no rotation pending": their passwords were set by their
 * owners under the previous behavior, and an upgrade must not lock anyone out.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StudioUserSchemaMigration implements ApplicationRunner {

    private static final String TABLE = "rmq_studio_user";
    private static final String COLUMN = "password_must_change";
    private static final String DEFINITION = "TINYINT(1) NOT NULL DEFAULT 0";

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            if (!hasTable(metadata, catalog) || hasColumn(metadata, catalog)) {
                return;
            }
            try {
                log.info("Adding studio user column {}.{}", TABLE, COLUMN);
                statement.executeUpdate("ALTER TABLE " + TABLE + " ADD COLUMN " + COLUMN
                        + " " + DEFINITION);
            } catch (SQLException failure) {
                if (!hasColumn(metadata, catalog)) {
                    throw failure;
                }
            }
        }
    }

    private boolean hasTable(DatabaseMetaData metadata, String catalog) throws Exception {
        try (ResultSet tables = metadata.getTables(catalog, null, TABLE, new String[]{"TABLE"})) {
            return tables.next();
        }
    }

    private boolean hasColumn(DatabaseMetaData metadata, String catalog) throws Exception {
        try (ResultSet columns = metadata.getColumns(catalog, null, TABLE, COLUMN)) {
            return columns.next();
        }
    }
}
