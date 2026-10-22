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

import org.apache.rocketmq.studio.cluster.metrics.BusinessMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// NON_KEYWORDS isolates this regression from the already reported H2 mapping defect #5131.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:fresh-rule-test;MODE=MySQL;"
        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE")
@ActiveProfiles("dev")
class NativeAlertRuleFreshSampleIntegrationTest {
    @Autowired
    private MetricSnapshotRepository snapshots;

    @Autowired
    private JdbcTemplate jdbc;

    private final String instanceId = "fresh-sample-" + UUID.randomUUID();

    @AfterEach
    void removeSnapshots() {
        jdbc.update("DELETE FROM rmq_metric_snapshot WHERE instance_id = ?", instanceId);
    }

    @ParameterizedTest
    @CsvSource({"LAST, 100, true", "AVG, 55, true", "SUM, 110, true", "MAX, 100, true", "MIN, 10, false"})
    void ruleTestIncludesFreshObservationWithoutSavingItTest(String aggregation, double expected,
            boolean conditionMet) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        MetricSample current = sample(instanceId, 100, now);
        MetricSample previous = sample(instanceId, 10, now.minusSeconds(30));
        snapshots.saveAll(List.of(previous));
        InstanceVO instance = InstanceVO.builder().name(instanceId).build();
        InstanceRepository instances = mock(InstanceRepository.class);
        BusinessMetricsCollector collector = mock(BusinessMetricsCollector.class);
        when(instances.findByIdentifier(instanceId)).thenReturn(Optional.of(instance));
        when(collector.supports(instance)).thenReturn(true);
        when(collector.collect(instance)).thenReturn(List.of(current));
        AlertRuleVO rule = AlertRuleVO.builder().domain(AlertDomain.BUSINESS).metric(current.metricKey())
                .instanceId(instanceId).consumerGroup("orders").operator(">").threshold(50)
                .aggregation(aggregation).windowSeconds(300).build();
        NativeAlertRuleTestService service = new NativeAlertRuleTestService(instances, List.of(),
                List.of(collector), new AlertRuleEvaluator(), snapshots);

        assertThat(service.test(rule).samples()).singleElement().satisfies(result -> {
            assertThat(result.currentValue()).isEqualTo(expected);
            assertThat(result.conditionMet()).isEqualTo(conditionMet);
        });
        assertThat(snapshots.findRecent(current, now.minusSeconds(300)))
                .singleElement().satisfies(stored -> assertThat(stored.value()).isEqualTo(10));
    }

    private static MetricSample sample(String instanceId, double value, Instant time) {
        return new MetricSample("consumer.lag.total", AlertDomain.BUSINESS, instanceId, null,
                Map.of("consumerGroup", "orders"), value, MetricAvailability.AVAILABLE, time);
    }
}
