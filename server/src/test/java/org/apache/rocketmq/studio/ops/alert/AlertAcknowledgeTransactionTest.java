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

import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.metrics.MetricProfileService;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Acknowledging a system alert is two writes: the alert row is marked acknowledged, then the
 * active rule state is transitioned to ACKED. This class pins the property that the two writes
 * are one unit, by letting the second one fail against a real database.
 *
 * <p>A mock-only test cannot observe this at all - without a Spring-demarcated transaction there
 * is no rollback to assert on, and with a mocked repository the first write would just be a mock
 * call whose side effect disappears. Here the first write inserts a probe row through
 * {@link JdbcTemplate}, so a real rollback removes it, and a missing rollback leaves it behind.
 *
 * <p>The {@code dev} profile is deliberate: it is H2 in MySQL compatibility mode fed by
 * {@code classpath:db/schema.sql}, so the probe row really is written and the rollback really is a
 * database rollback.
 */
@SpringBootTest
@ActiveProfiles("dev")
class AlertAcknowledgeTransactionTest {

    @MockitoBean
    private AlertRepository alertRepository;
    @MockitoBean
    private AlertStateRepository alertStateRepository;
    @MockitoBean
    private OperationAuditService operationAuditService;

    @Autowired
    private AlertService alertService;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private MetricProfileService metricProfileService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_ack_tx_probe (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("DELETE FROM alert_ack_tx_probe");
    }

    @Test
    void failedStateAcknowledgeRollsBackTheAlertAcknowledge() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(42L).ruleId(7L).fingerprint("fp").level(AlertLevel.error)
                .title("Broker Down").transition("FIRING")
                .time(LocalDateTime.of(2026, 8, 22, 12, 0))
                .acknowledged(false).build();
        when(alertRepository.findAlertById(42L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO alert_ack_tx_probe (id) VALUES (1)");
            return true;
        });
        when(alertStateRepository.acknowledge(any(AlertStateKey.class), any()))
                .thenThrow(new IllegalStateException("state acknowledge failed"));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("state acknowledge failed");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM alert_ack_tx_probe", Integer.class))
                .as("the alert-row write must roll back when the state transition fails")
                .isZero();
    }

    @Test
    void successfulStateAcknowledgeCommitsTheAlertAcknowledge() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(43L).ruleId(8L).fingerprint("fp2").level(AlertLevel.error)
                .title("Disk Full").transition("FIRING")
                .time(LocalDateTime.of(2026, 8, 22, 13, 0))
                .acknowledged(false).build();
        when(alertRepository.findAlertById(43L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO alert_ack_tx_probe (id) VALUES (2)");
            return true;
        });

        alertService.acknowledgeAlert(43L);

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM alert_ack_tx_probe", Integer.class))
                .as("the alert-row write must commit when the whole acknowledgement succeeds")
                .isEqualTo(1);
    }
}
