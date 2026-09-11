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
package org.apache.rocketmq.studio.provider.credential;

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

/** Adds referential integrity for cloud instances created by earlier Studio builds. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudCredentialSchemaMigration implements ApplicationRunner {
    static final String INSTANCE_TABLE = "rmq_instance";
    static final String CREDENTIAL_TABLE = "rmq_cloud_credential";
    static final String CREDENTIAL_INDEX = "idx_instance_credential_id";
    static final String CREDENTIAL_FK = "fk_instance_cloud_credential";

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            if (!hasTable(metadata, catalog, INSTANCE_TABLE) || !hasTable(metadata, catalog, CREDENTIAL_TABLE)) {
                return;
            }
            if (hasImportedKey(metadata, catalog)) {
                return;
            }
            long orphanCount = countOrphanedCredentialReferences(statement);
            if (orphanCount > 0) {
                throw new IllegalStateException("Cannot add cloud credential foreign key because " + orphanCount
                        + " instances reference missing cloud credentials");
            }
            ensureIndex(metadata, catalog, statement);
            addForeignKey(metadata, catalog, statement);
        }
    }

    private static long countOrphanedCredentialReferences(Statement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM " + INSTANCE_TABLE
                + " i LEFT JOIN " + CREDENTIAL_TABLE + " c ON c.id = i.credential_id"
                + " WHERE i.credential_id IS NOT NULL AND c.id IS NULL")) {
            result.next();
            return result.getLong(1);
        }
    }

    private static void ensureIndex(DatabaseMetaData metadata, String catalog, Statement statement) throws Exception {
        if (hasIndex(metadata, catalog, CREDENTIAL_INDEX)) {
            return;
        }
        try {
            log.info("Adding cloud credential reference index {}.{}", INSTANCE_TABLE, CREDENTIAL_INDEX);
            statement.executeUpdate("CREATE INDEX " + CREDENTIAL_INDEX + " ON " + INSTANCE_TABLE + " (credential_id)");
        } catch (SQLException failure) {
            if (!hasIndex(metadata, catalog, CREDENTIAL_INDEX)) {
                throw failure;
            }
        }
    }

    private static void addForeignKey(DatabaseMetaData metadata, String catalog, Statement statement) throws Exception {
        try {
            log.info("Adding cloud credential foreign key {}.{}", INSTANCE_TABLE, CREDENTIAL_FK);
            statement.executeUpdate("ALTER TABLE " + INSTANCE_TABLE + " ADD CONSTRAINT " + CREDENTIAL_FK
                    + " FOREIGN KEY (credential_id) REFERENCES " + CREDENTIAL_TABLE + " (id) ON DELETE RESTRICT");
        } catch (SQLException failure) {
            if (!hasImportedKey(metadata, catalog)) {
                throw failure;
            }
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String table) throws Exception {
        try (ResultSet tables = metadata.getTables(catalog, null, table, new String[] {"TABLE"})) {
            return tables.next();
        }
    }

    private static boolean hasIndex(DatabaseMetaData metadata, String catalog, String index) throws Exception {
        try (ResultSet indexes = metadata.getIndexInfo(catalog, null, INSTANCE_TABLE, false, false)) {
            while (indexes.next()) {
                if (index.equalsIgnoreCase(indexes.getString("INDEX_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static boolean hasImportedKey(DatabaseMetaData metadata, String catalog) throws Exception {
        try (ResultSet keys = metadata.getImportedKeys(catalog, null, INSTANCE_TABLE)) {
            while (keys.next()) {
                if (CREDENTIAL_FK.equalsIgnoreCase(keys.getString("FK_NAME"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
