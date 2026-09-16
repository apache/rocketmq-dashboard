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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the escaped pattern through a real database rather than asserting on the wrapper: the
 * helper is only worth anything if the engine treats the backslash as the {@code LIKE} escape
 * character. H2 is used in the same {@code MODE=MySQL} as the dev profile, which is also the mode
 * MySQL itself runs in.
 */
class SqlLikeEscapingAgainstDatabaseTest {

    private static final String PROBE_COLUMN = "probe_value";

    private Connection connection;

    @BeforeEach
    void setUp() throws SQLException {
        connection = DriverManager.getConnection(
                "jdbc:h2:mem:sql-like-escaping;MODE=MySQL;DATABASE_TO_LOWER=TRUE", "sa", "");
        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_like_probe (probe_value VARCHAR(64))");
        }
        insert("orders");
        insert("100%done");
        insert("100_done");
        insert("100xdone");
        insert("a\\b");
    }

    @AfterEach
    void tearDown() throws SQLException {
        connection.close();
    }

    @Test
    void shouldMatchALiteralPercentInsteadOfEveryRowTest() throws SQLException {
        // Escaped: only the row that really contains a percent sign.
        assertThat(countMatching(SqlLikeUtils.escape("%"))).isEqualTo(1);
        // Unescaped: a bare % widens the pattern to every row, which is the reported symptom.
        assertThat(countMatching("%")).isEqualTo(5);
    }

    @Test
    void shouldMatchALiteralUnderscoreInsteadOfAnySingleCharacterTest() throws SQLException {
        // Escaped: only the row that really contains 100_done.
        assertThat(countMatching(SqlLikeUtils.escape("100_done"))).isEqualTo(1);
        // Unescaped: _ stands for any single character, so three rows match.
        assertThat(countMatching("100_done")).isEqualTo(3);
    }

    @Test
    void shouldMatchALiteralBackslashTest() throws SQLException {
        assertThat(countMatching(SqlLikeUtils.escape("a\\b"))).isEqualTo(1);
    }

    @Test
    void shouldLeaveOrdinarySearchesUnaffectedTest() throws SQLException {
        assertThat(countMatching(SqlLikeUtils.escape("orders"))).isEqualTo(1);
        assertThat(countMatching(SqlLikeUtils.escape("100xdone"))).isEqualTo(1);
        assertThat(countMatching(SqlLikeUtils.escape("no-such-row"))).isZero();
    }

    private void insert(String probeValue) throws SQLException {
        try (PreparedStatement statement =
                     connection.prepareStatement("INSERT INTO rmq_like_probe (" + PROBE_COLUMN + ") VALUES (?)")) {
            statement.setString(1, probeValue);
            statement.executeUpdate();
        }
    }

    private int countMatching(String term) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM rmq_like_probe WHERE " + PROBE_COLUMN + " LIKE ?")) {
            statement.setString(1, "%" + term + "%");
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                return result.getInt(1);
            }
        }
    }
}
