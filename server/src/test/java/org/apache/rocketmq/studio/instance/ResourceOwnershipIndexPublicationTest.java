/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.instance;

import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@Timeout(10)
class ResourceOwnershipIndexPublicationTest {

    private JdbcDataSource database;
    private JdbcTemplate jdbc;
    private final AtomicInteger readsAfterConflict = new AtomicInteger();

    @BeforeEach
    void setUp() {
        database = new JdbcDataSource();
        database.setURL("jdbc:h2:mem:index-publication-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(database);
        jdbc = new JdbcTemplate(database);
        jdbc.execute("ALTER TABLE rmq_instance_group DROP INDEX uk_group_name");
    }

    @Test
    void racingIdenticalIndexShouldBeAcceptedAfterMetadataBecomesVisibleTest() throws Exception {
        var migration = migrationWithRacingCreate("CREATE UNIQUE INDEX uk_group_name ON rmq_instance_group (name)", 2);
        assertThatCode(migration::afterPropertiesSet).doesNotThrowAnyException();
        assertThat(readsAfterConflict.get()).isGreaterThan(2);
        jdbc.update("INSERT INTO rmq_instance_group (name, instance_id, cluster_id) VALUES ('buyers', 'first', 'a')");
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rmq_instance_group (name, instance_id, cluster_id)"
                + " VALUES ('buyers', 'second', 'b')"))
                .isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "CREATE INDEX uk_group_name ON rmq_instance_group (name)",
        "CREATE UNIQUE INDEX uk_group_name ON rmq_instance_group (instance_id)"
    })
    void racingWrongIndexDefinitionShouldStillFailTest(String competingDdl) throws Exception {
        var migration = migrationWithRacingCreate(competingDdl, 0);
        assertThatThrownBy(migration::afterPropertiesSet).isInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(((SQLException) failure).getErrorCode()).isEqualTo(42111));
    }

    @Test
    void failedCreateWithoutACompatibleIndexShouldPreserveTheOriginalErrorTest() throws Exception {
        var migration = migrationWithRacingCreate(null, 0);
        assertThatThrownBy(migration::afterPropertiesSet).isInstanceOf(SQLException.class)
                .hasMessage("create rejected");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_group", Integer.class)).isZero();
    }

    @Test
    void duplicateDataAddedAfterPrecheckShouldStillFailWithoutDiscardingRowsTest() throws Exception {
        var migration = migrationWithRacingCreate("INSERT INTO rmq_instance_group (name, cluster_id)"
                + " VALUES ('buyers', 'a'), ('buyers', 'b')", 0);
        assertThatThrownBy(migration::afterPropertiesSet).isInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(((SQLException) failure).getErrorCode()).isEqualTo(23505));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_group", Integer.class)).isEqualTo(2);
    }

    @Test
    void anIndexThatNeverBecomesVisibleShouldNotBeAssumedCompatibleTest() throws Exception {
        var migration = migrationWithRacingCreate("CREATE UNIQUE INDEX uk_group_name ON rmq_instance_group (name)",
                Integer.MAX_VALUE);
        assertThatThrownBy(migration::afterPropertiesSet).isInstanceOf(SQLException.class)
                .satisfies(failure -> assertThat(((SQLException) failure).getErrorCode()).isEqualTo(42111));
    }

    @Test
    void interruptedRecheckShouldPreserveTheCreateErrorAndInterruptFlagTest() throws Exception {
        var migration = migrationWithRacingCreate(null, 0, true);
        try {
            assertThatThrownBy(migration::afterPropertiesSet).isInstanceOf(SQLException.class)
                    .hasMessage("create rejected")
                    .satisfies(failure -> assertThat(failure.getSuppressed())
                            .hasAtLeastOneElementOfType(InterruptedException.class));
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    /** Real H2 DDL, with only the competing index's metadata publication delayed. */
    private ResourceOwnershipSchemaMigration migrationWithRacingCreate(String competingDdl, int hiddenReads)
            throws Exception {
        return migrationWithRacingCreate(competingDdl, hiddenReads, false);
    }

    private ResourceOwnershipSchemaMigration migrationWithRacingCreate(String competingDdl, int hiddenReads,
                                                                       boolean interruptOnFailure) throws Exception {
        Connection actual = database.getConnection();
        Connection connection = mock(Connection.class, delegatesTo(actual));
        DatabaseMetaData actualMetadata = actual.getMetaData();
        DatabaseMetaData metadata = mock(DatabaseMetaData.class, delegatesTo(actualMetadata));
        AtomicBoolean collided = new AtomicBoolean();
        doReturn(metadata).when(connection).getMetaData();
        doAnswer(call -> {
            ResultSet rows = actualMetadata.getIndexInfo(call.getArgument(0), call.getArgument(1),
                    call.getArgument(2), call.getArgument(3), call.getArgument(4));
            boolean hide = collided.get() && "rmq_instance_group".equals(call.getArgument(2))
                    && readsAfterConflict.incrementAndGet() <= hiddenReads;
            if (!hide) {
                return rows;
            }
            ResultSet delayed = mock(ResultSet.class, delegatesTo(rows));
            doAnswer(next -> {
                while (rows.next()) {
                    if (!"uk_group_name".equalsIgnoreCase(rows.getString("INDEX_NAME"))) {
                        return true;
                    }
                }
                return false;
            }).when(delayed).next();
            return delayed;
        }).when(metadata).getIndexInfo(anyString(), org.mockito.ArgumentMatchers.isNull(),
                anyString(), org.mockito.ArgumentMatchers.eq(false), org.mockito.ArgumentMatchers.eq(false));
        doAnswer(call -> {
            Statement actualStatement = actual.createStatement();
            Statement statement = mock(Statement.class, delegatesTo(actualStatement));
            doAnswer(update -> {
                String sql = update.getArgument(0);
                if (sql.equals("CREATE UNIQUE INDEX uk_group_name ON rmq_instance_group (name)")) {
                    if (competingDdl == null) {
                        if (interruptOnFailure) {
                            Thread.currentThread().interrupt();
                        }
                        throw new SQLException("create rejected");
                    }
                    jdbc.execute(competingDdl);
                    collided.set(true);
                }
                return actualStatement.executeUpdate(sql);
            }).when(statement).executeUpdate(anyString());
            return statement;
        }).when(connection).createStatement();
        DataSource wrapped = mock(DataSource.class);
        when(wrapped.getConnection()).thenReturn(connection);
        return new ResourceOwnershipSchemaMigration(wrapped, mock(RocketMQDefaultClusterResolver.class));
    }
}
