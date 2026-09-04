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

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.metrics.MetricSnapshotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@SpringBootTest
class NativeAlertEvaluationTransactionTest {
    @Autowired
    private NativeAlertEvaluationService evaluationService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @MockBean
    private AlertRuleEvaluator evaluator;
    @MockBean
    private AlertStateMachine stateMachine;
    @MockBean
    private AlertStateRepository stateRepository;
    @MockBean
    private MetricSnapshotRepository snapshotRepository;
    @MockBean
    private AlertRepository alertRepository;
    @MockBean
    private NotificationOutboxService outbox;
    @MockBean
    private AlertNotificationSuppressionService suppression;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS native_alert_tx_probe (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("DELETE FROM native_alert_tx_probe");
        when(evaluator.evaluate(any(), any())).thenReturn(
                new AlertEvaluationResult(true, true, 20D, MetricAvailability.AVAILABLE));
        when(stateRepository.find(any())).thenReturn(Optional.empty());
        when(stateMachine.advance(any(), any(), any(Integer.class), any(), any(), any()))
                .thenReturn(new AlertStateUpdate(new AlertRuleState(AlertStateStatus.FIRING, 1, 20D, null,
                        Instant.now(), Instant.now(), null), AlertStateTransition.FIRING));
        when(stateRepository.save(any(), any())).thenAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO native_alert_tx_probe (id) VALUES (1)");
            return true;
        });
        when(suppression.findSuppressingClusterAlert(any())).thenReturn(Optional.empty());
    }

    @Test
    void failedEvaluationRollsBackItsWritesButNextEvaluationCommits() {
        AtomicBoolean fail = new AtomicBoolean(true);
        when(alertRepository.saveAlert(any())).thenAnswer(invocation -> {
            if (fail.get()) {
                throw new IllegalStateException("event insert failed");
            }
            return invocation.getArgument(0);
        });
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).domain(AlertDomain.BUSINESS).name("Orders")
                .metric("consumer.lag.total").operator(">").threshold(10).enabled(true).instanceId("local")
                .consumerGroup("orders").build();
        MetricSample sample = new MetricSample("consumer.lag.total", AlertDomain.BUSINESS, "local", null,
                Map.of("consumerGroup", "orders"), 20D, MetricAvailability.AVAILABLE, Instant.now());

        assertThatThrownBy(() -> evaluationService.evaluate(rule, sample)).isInstanceOf(IllegalStateException.class);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM native_alert_tx_probe", Integer.class))
                .isZero();

        fail.set(false);
        evaluationService.evaluate(rule, sample);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM native_alert_tx_probe", Integer.class))
                .isEqualTo(1);
    }
}
