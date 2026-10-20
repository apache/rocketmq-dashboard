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

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import javax.sql.DataSource;
import java.io.PrintWriter;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static org.assertj.core.api.Assertions.assertThat;

class UsernameColumnWideningSchemaMigrationTest {

    @Test
    void widensNarrowUsernameColumnsTo128WithoutDroppingRowsAndIsIdempotent() throws Exception {
        JdbcDataSource dataSource = newDataSource("username-widening");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_instance_message "
                    + "(id BIGINT PRIMARY KEY, queried_by VARCHAR(64), msg_id VARCHAR(128))");
            statement.execute("CREATE TABLE rmq_instance_trace "
                    + "(id BIGINT PRIMARY KEY, queried_by VARCHAR(64), msg_id VARCHAR(128))");
            statement.execute("CREATE TABLE rmq_operation_audit "
                    + "(id BIGINT PRIMARY KEY, operator VARCHAR(64), action VARCHAR(255))");
            statement.execute("INSERT INTO rmq_instance_message (id, queried_by, msg_id) "
                    + "VALUES (1, 'old-user', 'm1')");
            statement.execute("INSERT INTO rmq_instance_trace (id, queried_by, msg_id) "
                    + "VALUES (1, 'old-user', 't1')");
            statement.execute("INSERT INTO rmq_operation_audit (id, operator, action) "
                    + "VALUES (1, 'old-user', 'login')");
        }

        UsernameColumnWideningSchemaMigration migration = new UsernameColumnWideningSchemaMigration(dataSource);
        migration.run(new DefaultApplicationArguments());
        migration.run(new DefaultApplicationArguments());

        assertWidth(dataSource, "rmq_instance_message", "queried_by", 128);
        assertWidth(dataSource, "rmq_instance_trace", "queried_by", 128);
        assertWidth(dataSource, "rmq_operation_audit", "operator", 128);
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            assertStoredValue(statement, "SELECT queried_by FROM rmq_instance_message WHERE id = 1", "old-user");
            assertStoredValue(statement, "SELECT queried_by FROM rmq_instance_trace WHERE id = 1", "old-user");
            assertStoredValue(statement, "SELECT operator FROM rmq_operation_audit WHERE id = 1", "old-user");
        }
    }

    @Test
    void leavesAlreadyWidenedColumnsUntouched() throws Exception {
        JdbcDataSource delegate = newDataSource("username-widening-done");
        try (Connection connection = delegate.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_instance_message "
                    + "(id BIGINT PRIMARY KEY, queried_by VARCHAR(128))");
            statement.execute("INSERT INTO rmq_instance_message (id, queried_by) VALUES (1, 'wide-user')");
        }

        List<String> updates = new ArrayList<>();
        UsernameColumnWideningSchemaMigration migration =
                new UsernameColumnWideningSchemaMigration(interceptingUpdates(delegate, updates::add));
        migration.run(new DefaultApplicationArguments());

        assertWidth(delegate, "rmq_instance_message", "queried_by", 128);
        try (Connection connection = delegate.getConnection(); Statement statement = connection.createStatement()) {
            assertStoredValue(statement, "SELECT queried_by FROM rmq_instance_message WHERE id = 1", "wide-user");
        }
        // MODIFY to the width a column already has succeeds on H2, so the width assertion above would
        // still pass without the columnWidth() guard; only the statement count proves it was skipped.
        assertThat(updates).isEmpty();
    }

    @Test
    void skipsMissingTablesSoCustomSchemaCanCreateThemLater() throws Exception {
        JdbcDataSource delegate = newDataSource("username-widening-missing");

        List<String> updates = new ArrayList<>();
        UsernameColumnWideningSchemaMigration migration =
                new UsernameColumnWideningSchemaMigration(interceptingUpdates(delegate, updates::add));
        migration.run(new DefaultApplicationArguments());

        try (Connection connection = delegate.getConnection(); Statement statement = connection.createStatement()) {
            assertThat(tableExists(statement, "rmq_instance_message")).isFalse();
            assertThat(tableExists(statement, "rmq_instance_trace")).isFalse();
            assertThat(tableExists(statement, "rmq_operation_audit")).isFalse();
        }
        assertThat(updates).isEmpty();
    }

    @Test
    void keepsBootingWhenTheServerRefusesTheWidening() throws Exception {
        JdbcDataSource delegate = newDataSource("username-widening-refused");
        try (Connection connection = delegate.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_instance_message "
                    + "(id BIGINT PRIMARY KEY, queried_by VARCHAR(64))");
        }

        List<String> updates = new ArrayList<>();
        UsernameColumnWideningSchemaMigration migration = new UsernameColumnWideningSchemaMigration(
                interceptingUpdates(delegate, sql -> {
                    updates.add(sql);
                    throw new SQLException("widening refused by server");
                }));
        migration.run(new DefaultApplicationArguments());

        assertWidth(delegate, "rmq_instance_message", "queried_by", 64);
        // The ALTER has to be attempted for the failure branch (and its re-check of the width) to run.
        assertThat(updates).containsExactly("ALTER TABLE rmq_instance_message MODIFY queried_by VARCHAR(128)");
    }

    private static JdbcDataSource newDataSource(String name) {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + name + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        dataSource.setUser("sa");
        return dataSource;
    }

    private static void assertWidth(DataSource dataSource, String table, String column, int width)
            throws Exception {
        try (Connection connection = dataSource.getConnection();
                ResultSet columns = connection.getMetaData()
                        .getColumns(connection.getCatalog(), null, table, column)) {
            assertThat(columns.next()).isTrue();
            assertThat(columns.getInt("COLUMN_SIZE")).isEqualTo(width);
        }
    }

    private static void assertStoredValue(Statement statement, String query, String value) throws SQLException {
        try (ResultSet rows = statement.executeQuery(query)) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).isEqualTo(value);
        }
    }

    private static boolean tableExists(Statement statement, String table) throws SQLException {
        try (ResultSet tables = statement.executeQuery("SELECT COUNT(*) FROM information_schema.tables "
                + "WHERE table_name = '" + table + "'")) {
            tables.next();
            return tables.getInt(1) > 0;
        }
    }

    /** DataSource that hands every {@code executeUpdate} SQL to {@code interceptor} before running it. */
    private static DataSource interceptingUpdates(DataSource delegate, UpdateInterceptor interceptor) {
        return new DataSource() {
            @Override
            public Connection getConnection() throws SQLException {
                return interceptedConnection(delegate.getConnection(), interceptor);
            }

            @Override
            public Connection getConnection(String username, String password) throws SQLException {
                return interceptedConnection(delegate.getConnection(username, password), interceptor);
            }

            @Override
            public PrintWriter getLogWriter() throws SQLException {
                return delegate.getLogWriter();
            }

            @Override
            public void setLogWriter(PrintWriter out) throws SQLException {
                delegate.setLogWriter(out);
            }

            @Override
            public void setLoginTimeout(int seconds) throws SQLException {
                delegate.setLoginTimeout(seconds);
            }

            @Override
            public int getLoginTimeout() throws SQLException {
                return delegate.getLoginTimeout();
            }

            @Override
            public Logger getParentLogger() throws SQLFeatureNotSupportedException {
                return delegate.getParentLogger();
            }

            @Override
            public <T> T unwrap(Class<T> iface) throws SQLException {
                return delegate.unwrap(iface);
            }

            @Override
            public boolean isWrapperFor(Class<?> iface) throws SQLException {
                return delegate.isWrapperFor(iface);
            }
        };
    }

    /** Records or rejects one {@code executeUpdate} statement before it reaches the database. */
    private interface UpdateInterceptor {
        void intercept(String sql) throws SQLException;
    }

    private static Connection interceptedConnection(Connection delegate, UpdateInterceptor interceptor) {
        return (Connection) Proxy.newProxyInstance(
                UsernameColumnWideningSchemaMigrationTest.class.getClassLoader(),
                new Class<?>[] {Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("createStatement")) {
                        return interceptedStatement(delegate.createStatement(), interceptor);
                    }
                    return method.invoke(delegate, args);
                });
    }

    private static Statement interceptedStatement(Statement delegate, UpdateInterceptor interceptor) {
        return (Statement) Proxy.newProxyInstance(
                UsernameColumnWideningSchemaMigrationTest.class.getClassLoader(),
                new Class<?>[] {Statement.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("executeUpdate")) {
                        interceptor.intercept(String.valueOf(args[0]));
                    }
                    return method.invoke(delegate, args);
                });
    }
}
