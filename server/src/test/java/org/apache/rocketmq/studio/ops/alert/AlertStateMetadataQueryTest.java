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
package org.apache.rocketmq.studio.ops.alert;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.rocketmq.studio.cluster.metrics.MetricCollectionScope;
import org.apache.rocketmq.studio.persistence.mapper.RmqAlertStateMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSystemAlertMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class AlertStateMetadataQueryTest {
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 19, 12, 0);
    private static final MetricCollectionScope SCOPE = new MetricCollectionScope(AlertDomain.BUSINESS,
            "local", Set.of("consumer.lag.total"));
    private JdbcTemplate jdbc;
    private SqlSession session;
    private MybatisPlusAlertStateRepository repository;
    private MetadataRows metadataRows;
    private long eventId;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:alert-metadata-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("metadata-test", new JdbcTransactionFactory(), dataSource));
        metadataRows = new MetadataRows();
        configuration.addInterceptor(metadataRows);
        configuration.addMapper(RmqAlertStateMapper.class);
        configuration.addMapper(RmqSystemAlertMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        repository = new MybatisPlusAlertStateRepository(session.getMapper(RmqAlertStateMapper.class),
                session.getMapper(RmqSystemAlertMapper.class));
    }

    @AfterEach
    void tearDown() {
        if (session != null) session.close();
        if (jdbc != null) jdbc.execute("SHUTDOWN");
    }

    @ParameterizedTest
    @ValueSource(ints = {1000, 2000})
    void boundsMappedRowsByActiveKeysRatherThanRetainedHistoryTest(int historyPerKey) {
        state(4, "orders", "FIRING");
        state(5, "payments", "ACKED");
        for (int i = 0; i < historyPerKey; i++) {
            event(4, "orders", "BUSINESS", "local", NOW.minusSeconds(historyPerKey - i), "old-orders");
            event(5, "payments", "BUSINESS", "local", NOW.minusSeconds(historyPerKey - i), "old-payments");
        }
        event(4, "orders", "BUSINESS", "local", NOW, "latest-orders");
        event(5, "payments", "BUSINESS", "local", NOW, "latest-payments");

        List<ActiveAlertState> active = repository.findActive(SCOPE, List.of(rule(4), rule(5)));

        assertThat(active).extracting(item -> item.labels().get("resource"))
                .containsExactlyInAnyOrder("latest-orders", "latest-payments");
        // Count the actual mapper result before the repository's first-per-key reduction.
        assertThat(metadataRows.queries).isEqualTo(1);
        assertThat(metadataRows.rows).isEqualTo(2);
    }

    @Test
    void keepsScopedLatestMetadataAndMissingEventSemanticsTest() {
        state(4, "a", "FIRING");
        state(4, "b", "ACKED");
        state(5, "c", "FIRING");
        state(6, "d", "ACKED");
        state(7, "missing", "FIRING");
        state(8, "resolved", "RESOLVED");
        event(4, "a", "BUSINESS", "local", NOW, "same-time-first");
        event(4, "a", "BUSINESS", "local", NOW, "same-time-last");
        event(4, "a", "BUSINESS", "local", NOW.minusDays(1), "larger-id-older-time");
        event(4, "a", "BUSINESS", "local", null, "larger-id-null-time");
        event(4, "b", "BUSINESS", "local", NOW, "other-fingerprint");
        event(5, "c", "BUSINESS", "local", null, "null-first");
        event(5, "c", "BUSINESS", "local", null, "null-last");
        event(6, "d", "BUSINESS", "local", NOW, "other-rule");
        event(5, "a", "BUSINESS", "local", NOW.plusYears(1), "inactive-cross-pair");
        event(4, "a", "CLUSTER", "local", NOW.plusYears(1), "other-domain");
        event(4, "a", "BUSINESS", "elsewhere", NOW.plusYears(1), "other-instance");
        event(8, "resolved", "BUSINESS", "local", NOW, "not-active");

        Map<AlertStateKey, ActiveAlertState> active = repository.findActive(SCOPE,
                List.of(rule(4), rule(5), rule(6), rule(7), rule(8))).stream()
                .collect(Collectors.toMap(ActiveAlertState::key, item -> item));

        assertThat(active).hasSize(4);
        assertThat(active.get(new AlertStateKey(4L, "a")).labels()).containsEntry("resource", "same-time-last");
        assertThat(active.get(new AlertStateKey(4L, "b")).labels()).containsEntry("resource", "other-fingerprint");
        assertThat(active.get(new AlertStateKey(5L, "c")).labels()).containsEntry("resource", "null-last");
        assertThat(active.get(new AlertStateKey(6L, "d")).labels()).containsEntry("resource", "other-rule");
        assertThat(active.values()).allSatisfy(item -> assertThat(item.instanceId()).isEqualTo("local"));
        assertThat(active.get(new AlertStateKey(4L, "b")).state().status()).isEqualTo(AlertStateStatus.ACKED);
    }

    @Test
    void skipsMetadataQueriesWithoutActiveScopedStatesTest() {
        assertThat(repository.findActive(null, List.of(rule(4)))).isEmpty();
        assertThat(repository.findActive(SCOPE, List.of())).isEmpty();
        assertThat(repository.findActive(SCOPE, List.of(rule(4)))).isEmpty();
        state(4, "resolved", "RESOLVED");
        session.clearCache();
        assertThat(repository.findActive(SCOPE, List.of(rule(4)))).isEmpty();
        assertThat(metadataRows.queries).isZero();
    }

    private void state(long ruleId, String fingerprint, String status) {
        jdbc.update("INSERT INTO rmq_alert_state (rule_id,fingerprint,status,consecutive_hits,version) VALUES (?,?,?,?,?)",
                ruleId, fingerprint, status, 1, 0);
    }

    private void event(long ruleId, String fingerprint, String domain, String instance,
            LocalDateTime time, String resource) {
        jdbc.update("INSERT INTO rmq_system_alert (id,rule_id,fingerprint,domain,instance_id,time,description,labels_json) "
                        + "VALUES (?,?,?,?,?,?,?,?)", ++eventId, ruleId, fingerprint, domain, instance, time,
                "historical-description-".repeat(50), "{\"resource\":\"" + resource + "\"}");
    }

    private static AlertRuleVO rule(long id) {
        return AlertRuleVO.builder().id(id).domain(AlertDomain.BUSINESS).enabled(true)
                .instanceId("local").metric("consumer.lag.total").build();
    }

    @Intercepts(@Signature(type = Executor.class, method = "query",
            args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    private static class MetadataRows implements Interceptor {
        private int queries;
        private int rows;

        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            Object result = invocation.proceed();
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            if (statement.getId().startsWith(RmqSystemAlertMapper.class.getName() + ".")) {
                queries++;
                rows += ((List<?>) result).size();
            }
            return result;
        }
    }
}
