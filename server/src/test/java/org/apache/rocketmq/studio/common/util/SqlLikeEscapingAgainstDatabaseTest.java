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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the predicate this package builds through a real engine, rather than only asserting on the
 * wrapper text. Both H2 modes are exercised: the plain one and the {@code MODE=MySQL} the dev
 * profile uses. The corresponding MySQL behaviour was measured the same way and is recorded in
 * {@link SqlLikeUtils}; the suite has no MySQL, which is exactly why the clause cannot be chosen by
 * running these tests alone.
 */
class SqlLikeEscapingAgainstDatabaseTest {

    private static final String PLAIN = "jdbc:h2:mem:like-plain;DATABASE_TO_LOWER=TRUE";
    private static final String MYSQL_MODE =
            "jdbc:h2:mem:like-mysql-mode;MODE=MySQL;DATABASE_TO_LOWER=TRUE";

    private static final String PROBE_COLUMN = "probe_value";

    private static final String[] SEEDED = {"orders", "100%done", "100_done", "100xdone", "a\\b"};

    @Test
    void shouldMatchALiteralPercentInsteadOfEveryRowTest() throws SQLException {
        forEachMode(connection -> {
            // The predicate the repositories emit, so only the row that really has a percent sign.
            assertThat(countMatching(connection, SqlLikeUtils.contains("%"))).isEqualTo(1);
            // Without the escaping a bare % widens to every row: this is the reported symptom.
            assertThat(countMatching(connection, "%")).isEqualTo(SEEDED.length);
        });
    }

    @Test
    void shouldMatchALiteralUnderscoreInsteadOfAnySingleCharacterTest() throws SQLException {
        forEachMode(connection -> {
            // Only 100_done, not the row where _ stands for the x.
            assertThat(countMatching(connection, SqlLikeUtils.contains("100_done"))).isEqualTo(1);
            // Unescaped, _ is any single character, so 100_done and 100%done match as well.
            assertThat(countMatching(connection, "100_done")).isEqualTo(3);
        });
    }

    @Test
    void shouldMatchTheEscapeCharacterItselfLiterallyTest() throws SQLException {
        forEachMode(connection ->
                assertThat(countMatching(connection, SqlLikeUtils.contains("a\\b"))).isEqualTo(1));
    }

    @Test
    void shouldLeaveOrdinarySearchesUnaffectedTest() throws SQLException {
        forEachMode(connection -> {
            assertThat(countMatching(connection, SqlLikeUtils.contains("orders"))).isEqualTo(1);
            assertThat(countMatching(connection, SqlLikeUtils.contains("100xdone"))).isEqualTo(1);
            assertThat(countMatching(connection, SqlLikeUtils.contains("no-such-row"))).isZero();
        });
    }

    @Test
    void aBlankTermShouldNotProduceAMatchEverythingPatternTest() throws SQLException {
        forEachMode(connection -> {
            // contains() answers null, which callers always pair with a condition that is false.
            assertThat(SqlLikeUtils.contains("  ")).isNull();
            assertThat(countMatching(connection, "%%")).isEqualTo(SEEDED.length);
        });
    }

    @Test
    void h2ShouldRejectTheDoubledBackslashSpellingTest() throws SQLException {
        // H2 does not apply MySQL's string-literal rules, so for H2 '\\' is two characters and its
        // LIKE ESCAPE is invalid. This is the executable half of why the shared clause spells the
        // backslash as CHAR(92): the doubled form cannot be the one constant for both engines.
        forEachMode(connection -> assertThatThrownBy(
                () -> countMatching(connection, "%", " ESCAPE '\\\\'"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("ESCAPE"));
    }

    private interface ConnectionCheck {
        void run(Connection connection) throws SQLException;
    }

    private void forEachMode(ConnectionCheck check) throws SQLException {
        for (String url : new String[] {PLAIN, MYSQL_MODE}) {
            try (Connection connection = DriverManager.getConnection(url, "sa", "")) {
                seed(connection);
                check.run(connection);
            }
        }
    }

    private void seed(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS rmq_like_probe");
            statement.execute("CREATE TABLE rmq_like_probe (" + PROBE_COLUMN + " VARCHAR(64))");
        }
        for (String value : SEEDED) {
            try (PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO rmq_like_probe (" + PROBE_COLUMN + ") VALUES (?)")) {
                statement.setString(1, value);
                statement.executeUpdate();
            }
        }
    }

    private int countMatching(Connection connection, String pattern) throws SQLException {
        return countMatching(connection, pattern, SqlLikeUtils.LIKE_ESCAPE_CLAUSE);
    }

    private int countMatching(Connection connection, String pattern, String escapeClause)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM rmq_like_probe WHERE " + PROBE_COLUMN + " LIKE ?"
                        + escapeClause)) {
            statement.setString(1, pattern);
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
