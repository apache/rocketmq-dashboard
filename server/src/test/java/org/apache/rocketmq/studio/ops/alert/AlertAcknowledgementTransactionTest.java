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
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@JdbcTest(properties = "spring.sql.init.mode=never")
@Import(AlertService.class)
@EnableTransactionManagement
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class AlertAcknowledgementTransactionTest {

    @Autowired
    private AlertService alertService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockBean
    private AlertRepository alertRepository;

    @MockBean
    private AlertStateRepository alertStateRepository;

    @MockBean
    private AlertRuleAssetService alertRuleAssetService;

    @MockBean
    private OperationAuditService operationAuditService;

    @MockBean
    private MetricProfileService metricProfileService;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("CREATE TABLE IF NOT EXISTS alert_ack_tx_probe (id BIGINT PRIMARY KEY)");
        jdbcTemplate.update("DELETE FROM alert_ack_tx_probe");
    }

    @Test
    void stateFailureRollsBackAlertAcknowledgementButSuccessfulRetryCommitsTest() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(1L)
                .level(AlertLevel.error)
                .title("Broker unavailable")
                .ruleId(7L)
                .fingerprint("broker-a")
                .transition(AlertStateTransition.FIRING.name())
                .time(LocalDateTime.of(2026, 9, 9, 12, 0))
                .acknowledged(false)
                .build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any())).thenAnswer(invocation -> {
            jdbcTemplate.update("INSERT INTO alert_ack_tx_probe (id) VALUES (1)");
            return true;
        });

        AtomicBoolean failStateWrite = new AtomicBoolean(true);
        when(alertStateRepository.acknowledge(any(), any())).thenAnswer(invocation -> {
            if (failStateWrite.get()) {
                throw new IllegalStateException("alert state update failed");
            }
            jdbcTemplate.update("INSERT INTO alert_ack_tx_probe (id) VALUES (2)");
            return true;
        });

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("alert state update failed");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_ack_tx_probe", Integer.class)).isZero();

        failStateWrite.set(false);
        alertService.acknowledgeAlert(1L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM alert_ack_tx_probe", Integer.class)).isEqualTo(2);
    }
}
