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
package org.apache.rocketmq.studio.cluster.metrics;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.ops.alert.AlertSchemaMigration;
import org.apache.rocketmq.studio.persistence.entity.RmqMetricSnapshot;
import org.apache.rocketmq.studio.persistence.mapper.RmqMetricSnapshotMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusMetricSnapshotRepositoryTest {

    @Mock
    private RmqMetricSnapshotMapper mapper;

    @Test
    void saveAllShouldUseOneTransactionForTheWholeSampleBatchTest() throws Exception {
        Method saveAll = MybatisPlusMetricSnapshotRepository.class
                .getMethod("saveAll", List.class);

        assertThat(saveAll.isAnnotationPresent(Transactional.class)).isTrue();
    }

    @Test
    void nullClusterScopeShouldOnlyReadUnscopedSnapshotsTest() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MybatisPlusMetricSnapshotRepository repository =
                new MybatisPlusMetricSnapshotRepository(mapper, new ObjectMapper());
        MetricSample scope = new MetricSample("broker.availability", AlertDomain.CLUSTER,
                "local", null, Map.of(), 1D, MetricAvailability.AVAILABLE, Instant.now());

        repository.findRecent(scope, Instant.EPOCH);

        ArgumentCaptor<Wrapper<RmqMetricSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(queryCaptor.capture());
        QueryWrapper<RmqMetricSnapshot> query = (QueryWrapper<RmqMetricSnapshot>) queryCaptor.getValue();
        assertThat(query.getSqlSegment()).contains("cluster_id IS NULL");
    }

    @Test
    void recentSnapshotsStopAtTheSecondAlignedEvaluatedSampleTimeTest() {
        when(mapper.selectList(any(Wrapper.class))).thenReturn(List.of());
        MybatisPlusMetricSnapshotRepository repository =
                new MybatisPlusMetricSnapshotRepository(mapper, new ObjectMapper());
        // DATETIME storage rounds a .700s fraction up, so the bound must cover the next whole second.
        Instant collectedAt = Instant.parse("2026-09-29T10:00:00.700Z");
        Instant since = collectedAt.minusSeconds(300);
        MetricSample scope = new MetricSample("consumer.lag.total", AlertDomain.BUSINESS,
                "local", null, Map.of("consumerGroup", "orders"), 10D,
                MetricAvailability.AVAILABLE, collectedAt);

        repository.findRecent(scope, since);

        ArgumentCaptor<Wrapper<RmqMetricSnapshot>> queryCaptor = ArgumentCaptor.forClass(Wrapper.class);
        verify(mapper).selectList(queryCaptor.capture());
        QueryWrapper<RmqMetricSnapshot> query = (QueryWrapper<RmqMetricSnapshot>) queryCaptor.getValue();
        assertThat(query.getSqlSegment()).contains("collected_at >=", "collected_at <=");
        assertThat(query.getParamNameValuePairs().values()).contains(
                LocalDateTime.ofInstant(since, ZoneOffset.UTC),
                LocalDateTime.ofInstant(collectedAt, ZoneOffset.UTC)
                        .truncatedTo(ChronoUnit.SECONDS).plusSeconds(1));
    }

    @Test
    void findRecentReturnsTheEvaluatedSampleStoredOnASecondGranularityColumnTest() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:metric-snapshot-granularity;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE");
        dataSource.setUser("sa");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE rmq_alert_rule (id BIGINT PRIMARY KEY, name VARCHAR(128))");
            statement.execute("CREATE TABLE rmq_system_alert (id BIGINT PRIMARY KEY, time TIMESTAMP)");
            statement.execute("CREATE TABLE rmq_alert_notification_outbox (id BIGINT PRIMARY KEY, alert_id BIGINT, "
                    + "channel VARCHAR(32), status VARCHAR(16), next_attempt_at TIMESTAMP)");
        }
        new AlertSchemaMigration(dataSource).run(new DefaultApplicationArguments());

        MybatisSqlSessionFactoryBean factoryBean = new MybatisSqlSessionFactoryBean();
        factoryBean.setDataSource(dataSource);
        SqlSessionFactory factory = factoryBean.getObject();
        factory.getConfiguration().addMapper(RmqMetricSnapshotMapper.class);
        try (SqlSession session = factory.openSession(true)) {
            MybatisPlusMetricSnapshotRepository repository =
                    new MybatisPlusMetricSnapshotRepository(session.getMapper(RmqMetricSnapshotMapper.class),
                            new ObjectMapper());
            // H2 rounds the fractional seconds up exactly like the MySQL DATETIME column does.
            Instant collectedAt = Instant.parse("2026-09-29T10:00:00.700Z");
            MetricSample sample = new MetricSample("consumer.lag.total", AlertDomain.BUSINESS,
                    "local", null, Map.of("consumerGroup", "orders"), 10D,
                    MetricAvailability.AVAILABLE, collectedAt);

            repository.saveAll(List.of(sample));

            List<MetricSample> window = repository.findRecent(sample, collectedAt.minusSeconds(300));
            assertThat(window).singleElement().satisfies(stored -> {
                assertThat(stored.value()).isEqualTo(10D);
                assertThat(stored.collectedAt()).isEqualTo(Instant.parse("2026-09-29T10:00:01Z"));
            });
        }
    }
}
