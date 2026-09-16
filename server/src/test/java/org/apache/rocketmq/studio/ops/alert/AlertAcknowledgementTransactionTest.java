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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertAcknowledgementTransactionTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private AlertStateRepository alertStateRepository;

    @Mock
    private AlertRuleAssetService alertRuleAssetService;

    @Mock
    private OperationAuditService operationAuditService;

    @Mock
    private MetricProfileService metricProfileService;

    private AlertService alertService;

    @BeforeEach
    void setUp() {
        alertService = new AlertService(alertRepository, alertStateRepository, alertRuleAssetService,
                operationAuditService, metricProfileService);
    }

    @Test
    void acknowledgeAlertIsTransactionalLikeTheOtherWriteMethods() throws Exception {
        Method method = AlertService.class.getMethod("acknowledgeAlert", Long.class);
        assertThat(method.getAnnotation(Transactional.class)).isNotNull();
    }

    @Test
    void acknowledgeAlertUpdatesBothEventAndFiringState() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(1L)
                .level(AlertLevel.error)
                .title("Broker unavailable")
                .ruleId(7L)
                .fingerprint("broker-a")
                .transition("FIRING")
                .time(LocalDateTime.of(2026, 9, 9, 12, 0))
                .acknowledged(false)
                .build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any())).thenReturn(true);
        when(alertStateRepository.acknowledge(any(), any())).thenReturn(true);

        SystemAlertVO result = alertService.acknowledgeAlert(1L);

        assertThat(result.isAcknowledged()).isTrue();
        InOrder inOrder = inOrder(alertRepository, alertStateRepository);
        inOrder.verify(alertRepository).acknowledgeAlert(alert);
        inOrder.verify(alertStateRepository).acknowledge(any(), any());
    }

    @Test
    void stateFailurePropagatesSoTheCallerSeesTheTransactionRolledBack() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(1L)
                .ruleId(7L)
                .fingerprint("broker-a")
                .transition("FIRING")
                .time(LocalDateTime.of(2026, 9, 9, 12, 0))
                .acknowledged(false)
                .build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any())).thenReturn(true);
        when(alertStateRepository.acknowledge(any(), any()))
                .thenThrow(new IllegalStateException("alert state update failed"));

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("alert state update failed");
    }

    @Test
    void nonFiringTransitionSkipsStateAcknowledgement() {
        SystemAlertVO alert = SystemAlertVO.builder()
                .id(2L)
                .ruleId(7L)
                .fingerprint("broker-a")
                .transition("RESOLVED")
                .time(LocalDateTime.of(2026, 9, 9, 12, 0))
                .acknowledged(false)
                .build();
        when(alertRepository.findAlertById(2L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any())).thenReturn(true);

        alertService.acknowledgeAlert(2L);

        verify(alertStateRepository, never()).acknowledge(any(), any());
        verify(operationAuditService).record(
                org.mockito.ArgumentMatchers.eq("ACKNOWLEDGE_SYSTEM_ALERT"),
                org.mockito.ArgumentMatchers.eq("SYSTEM_ALERT"),
                org.mockito.ArgumentMatchers.eq("2"),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }
}
