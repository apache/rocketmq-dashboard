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

/**
 * Widens the username columns to VARCHAR(128) on databases created before schema.sql widened them.
 *
 * <p>The trailing {@code ALTER TABLE ... MODIFY} statements in db/schema.sql only run against a
 * fresh MySQL data directory or the dev H2 profile; existing deployments are upgraded by the
 * application schema-migration runners, so this runner closes the gap for volumes initialised
 * while the columns were still VARCHAR(64).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UsernameColumnWideningSchemaMigration implements ApplicationRunner {
    private static final int REQUIRED_WIDTH = 128;
    /**
     * Width reported when the metadata lookup cannot answer, so the column is left untouched. Used
     * before an ALTER is considered: an unverifiable schema must not be modified.
     */
    private static final int WIDTH_UNKNOWN_ASSUME_WIDE = REQUIRED_WIDTH;
    /**
     * Width reported when the metadata lookup cannot answer after an ALTER was attempted, so the
     * caller treats the widening as unconfirmed and warns.
     */
    private static final int WIDTH_UNKNOWN_ASSUME_NARROW = 0;
    private static final List<Widening> WIDENINGS = List.of(
            new Widening("rmq_instance_message", "queried_by"),
            new Widening("rmq_instance_trace", "queried_by"),
            new Widening("rmq_operation_audit", "operator"));

    private final DataSource dataSource;

    @Override
    public void run(ApplicationArguments args) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metadata = connection.getMetaData();
            String catalog = connection.getCatalog();
            for (Widening widening : WIDENINGS) {
                ensureWidth(connection, metadata, catalog, widening);
            }
        } catch (SQLException failure) {
            log.warn("Skipped username column widening: {}", failure.getMessage());
        }
    }

    private static void ensureWidth(Connection connection, DatabaseMetaData metadata, String catalog,
            Widening widening) {
        if (!hasTable(metadata, catalog, widening.table())) {
            return;
        }
        if (columnWidth(metadata, catalog, widening.table(), widening.column(),
                WIDTH_UNKNOWN_ASSUME_WIDE) >= REQUIRED_WIDTH) {
            return;
        }
        try (Statement statement = connection.createStatement()) {
            log.info("Widening username column {}.{} to VARCHAR({})",
                    widening.table(), widening.column(), REQUIRED_WIDTH);
            statement.executeUpdate("ALTER TABLE " + widening.table() + " MODIFY " + widening.column()
                    + " VARCHAR(" + REQUIRED_WIDTH + ")");
        } catch (SQLException failure) {
            // A server that refuses the widening must not stop the application from booting; the
            // column stays narrow until the deployment grants the ALTER (schema.sql stays idempotent).
            // The re-check reports an unreadable width as "not widened" so the warning below still
            // reaches the log instead of being swallowed by a second metadata failure.
            if (columnWidth(metadata, catalog, widening.table(), widening.column(),
                    WIDTH_UNKNOWN_ASSUME_NARROW) < REQUIRED_WIDTH) {
                log.warn("Could not widen {}.{} to VARCHAR({}): {}",
                        widening.table(), widening.column(), REQUIRED_WIDTH, failure.getMessage());
            }
        }
    }

    private static boolean hasTable(DatabaseMetaData metadata, String catalog, String table) {
        try (ResultSet tables = metadata.getTables(catalog, null, table, new String[] {"TABLE"})) {
            return tables.next();
        } catch (SQLException failure) {
            return false;
        }
    }

    /**
     * Returns the declared width of the column, or {@code undeterminable} when the column or its size
     * cannot be read. Callers pick the fallback: {@link #WIDTH_UNKNOWN_ASSUME_WIDE} leaves an
     * unverifiable schema untouched, {@link #WIDTH_UNKNOWN_ASSUME_NARROW} reports a widening that
     * could not be confirmed.
     */
    private static int columnWidth(DatabaseMetaData metadata, String catalog, String table, String column,
            int undeterminable) {
        try (ResultSet columns = metadata.getColumns(catalog, null, table, column)) {
            if (!columns.next()) {
                return undeterminable;
            }
            return columns.getInt("COLUMN_SIZE");
        } catch (SQLException failure) {
            return undeterminable;
        }
    }

    private record Widening(String table, String column) {
    }
}
