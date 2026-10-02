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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.rocketmq.studio.persistence.entity.RmqMetricSnapshot;
import org.apache.rocketmq.studio.persistence.mapper.RmqMetricSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The development profile runs the canonical {@code db/schema.sql} on embedded H2 in MySQL
 * compatibility mode, where {@code VALUE} is a reserved word. Every column the MyBatis-Plus
 * entity mapping produces therefore has to be usable by H2 exactly as written in the schema.
 */
class MetricSnapshotValueColumnMappingTest {

    @Test
    void metricSnapshotValueRoundTripsThroughTheSchemaTest() throws Exception {
        SqlSessionFactory factory = newSqlSessionFactory("metric-snapshot-value-single");

        try (SqlSession session = factory.openSession(true)) {
            RmqMetricSnapshotMapper mapper = session.getMapper(RmqMetricSnapshotMapper.class);
            mapper.insert(snapshot("broker.availability", 42.5D));

            RmqMetricSnapshot stored = mapper.selectOne(new QueryWrapper<RmqMetricSnapshot>()
                    .eq("metric_key", "broker.availability"));

            assertThat(stored).isNotNull();
            assertThat(stored.getValue()).isEqualTo(42.5D);
        }
    }

    @Test
    void metricSnapshotBatchValuesRoundTripThroughTheSchemaTest() throws Exception {
        SqlSessionFactory factory = newSqlSessionFactory("metric-snapshot-value-batch");

        try (SqlSession session = factory.openSession(true)) {
            RmqMetricSnapshotMapper mapper = session.getMapper(RmqMetricSnapshotMapper.class);
            mapper.insert(snapshot("broker.tps", 1.0D));
            mapper.insert(snapshot("broker.tps", 2.0D));

            List<RmqMetricSnapshot> stored = mapper.selectList(new QueryWrapper<RmqMetricSnapshot>()
                    .eq("metric_key", "broker.tps")
                    .orderByAsc("id"));

            assertThat(stored).hasSize(2);
            assertThat(stored).extracting(RmqMetricSnapshot::getValue).containsExactly(1.0D, 2.0D);
        }
    }

    private SqlSessionFactory newSqlSessionFactory(String database) throws Exception {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:" + database + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "");
        try (Connection connection = dataSource.getConnection()) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("db/schema.sql"));
        }

        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.setEnvironment(new Environment("metric-snapshot-value",
                new JdbcTransactionFactory(), dataSource));
        configuration.addMapper(RmqMetricSnapshotMapper.class);
        return new MybatisSqlSessionFactoryBuilder().build(configuration);
    }

    private static RmqMetricSnapshot snapshot(String metricKey, double value) {
        RmqMetricSnapshot snapshot = new RmqMetricSnapshot();
        snapshot.setInstanceId("local");
        snapshot.setMetricKey(metricKey);
        snapshot.setDomain("BROKER");
        snapshot.setLabelsHash("0".repeat(64));
        snapshot.setLabelsJson("{}");
        snapshot.setValue(value);
        snapshot.setAvailability("AVAILABLE");
        snapshot.setCollectedAt(LocalDateTime.of(2026, 1, 1, 0, 0));
        return snapshot;
    }
}
