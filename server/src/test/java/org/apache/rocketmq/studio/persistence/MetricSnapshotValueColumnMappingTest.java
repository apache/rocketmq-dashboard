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

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import org.apache.rocketmq.studio.persistence.entity.RmqMetricSnapshot;
import org.apache.rocketmq.studio.persistence.mapper.RmqMetricSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Uses the application's mapper and canonical schema with H2 in MySQL mode. The datasource
 * override is explicit so CI exercises H2 even when its default backend uses MySQL.
 */
@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:metric-snapshot-mapping;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.sql.init.mode=always",
    "spring.sql.init.schema-locations=classpath:db/schema.sql",
    "studio.auth.login-required=false"
})
@Transactional
class MetricSnapshotValueColumnMappingTest {

    @Autowired
    private RmqMetricSnapshotMapper mapper;

    @Test
    void metricSnapshotValueRoundTripsThroughTheSchemaTest() {
        String metricKey = "broker.availability." + UUID.randomUUID();
        mapper.insert(snapshot(metricKey, 42.5D));

        RmqMetricSnapshot stored = mapper.selectOne(new QueryWrapper<RmqMetricSnapshot>()
                .eq("metric_key", metricKey));

        assertThat(stored).isNotNull();
        assertThat(stored.getValue()).isEqualTo(42.5D);
    }

    @Test
    void metricSnapshotBatchValuesRoundTripThroughTheSchemaTest() {
        String metricKey = "broker.tps." + UUID.randomUUID();
        mapper.insert(snapshot(metricKey, 1.0D));
        mapper.insert(snapshot(metricKey, 2.0D));

        List<RmqMetricSnapshot> stored = mapper.selectList(new QueryWrapper<RmqMetricSnapshot>()
                .eq("metric_key", metricKey)
                .orderByAsc("id"));

        assertThat(stored).hasSize(2);
        assertThat(stored).extracting(RmqMetricSnapshot::getValue).containsExactly(1.0D, 2.0D);
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
