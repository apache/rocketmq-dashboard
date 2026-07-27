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
package com.rocketmq.studio.ops.alert;

import com.rocketmq.studio.common.domain.enums.AlertLevel;
import com.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @InjectMocks
    private AlertService alertService;

    @Test
    void listRulesShouldReturnAllRules() {
        AlertRuleVO rule1 = AlertRuleVO.builder().id("1").alert("HighCpuAlert").group("broker")
                .expr("cpu_usage > 90").severity("critical").enabled(true).build();
        AlertRuleVO rule2 = AlertRuleVO.builder().id("2").alert("LowDiskAlert").group("broker")
                .expr("disk_free < 10").severity("warning").enabled(false).build();
        when(alertRepository.findAllRules()).thenReturn(Arrays.asList(rule1, rule2));

        List<AlertRuleVO> result = alertService.listRules();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getAlert()).isEqualTo("HighCpuAlert");
        assertThat(result.get(0).isEnabled()).isTrue();
        assertThat(result.get(1).getAlert()).isEqualTo("LowDiskAlert");
        assertThat(result.get(1).isEnabled()).isFalse();
    }

    @Test
    void listRulesShouldReturnEmptyListWhenNoRules() {
        when(alertRepository.findAllRules()).thenReturn(Collections.emptyList());

        List<AlertRuleVO> result = alertService.listRules();

        assertThat(result).isEmpty();
    }

    @Test
    void createRuleShouldAssignIdAndTimestamps() {
        AlertRuleVO input = AlertRuleVO.builder().alert("NewAlert").group("topic")
                .expr("lag > 1000").severity("warning").build();
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.createRule(input);

        assertThat(result.getId()).isNotNull().isNotEmpty();
        assertThat(result.getAlert()).isEqualTo("NewAlert");
        assertThat(result.getCreatedAt()).isNotNull().isNotEmpty();
        assertThat(result.getUpdatedAt()).isNotNull().isNotEmpty();
        assertThat(result.getCreatedAt()).isEqualTo(result.getUpdatedAt());
        verify(alertRepository).saveRule(result);
    }

    @Test
    void createRuleShouldGenerateUniqueIds() {
        AlertRuleVO input1 = AlertRuleVO.builder().alert("Alert1").build();
        AlertRuleVO input2 = AlertRuleVO.builder().alert("Alert2").build();
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result1 = alertService.createRule(input1);
        AlertRuleVO result2 = alertService.createRule(input2);

        assertThat(result1.getId()).isNotEqualTo(result2.getId());
    }

    @Test
    void updateRuleShouldPreserveCreatedAtAndUpdateTimestamp() {
        AlertRuleVO existing = AlertRuleVO.builder().id("rule-1").alert("OldAlert")
                .createdAt("2025-01-01 00:00:00").updatedAt("2025-01-01 00:00:00").build();
        AlertRuleVO input = AlertRuleVO.builder().id("rule-1").alert("UpdatedAlert")
                .group("consumer").expr("lag > 5000").severity("critical").build();
        when(alertRepository.findRuleById("rule-1")).thenReturn(existing);
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.updateRule(input);

        assertThat(result.getCreatedAt()).isEqualTo("2025-01-01 00:00:00");
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotEqualTo("2025-01-01 00:00:00");
        assertThat(result.getAlert()).isEqualTo("UpdatedAlert");
    }

    @Test
    void updateRuleShouldThrowWhenRuleNotFound() {
        AlertRuleVO input = AlertRuleVO.builder().id("non-existent").alert("Ghost").build();
        when(alertRepository.findRuleById("non-existent")).thenReturn(null);

        assertThatThrownBy(() -> alertService.updateRule(input))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Alert rule not found: non-existent");
    }

    @Test
    void toggleRuleShouldEnableRule() {
        AlertRuleVO existing = AlertRuleVO.builder().id("rule-1").alert("CPUAlert")
                .enabled(false).updatedAt("2025-01-01 00:00:00").build();
        when(alertRepository.findRuleById("rule-1")).thenReturn(existing);
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.toggleRule("rule-1", true);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(alertRepository).saveRule(result);
    }

    @Test
    void toggleRuleShouldDisableRule() {
        AlertRuleVO existing = AlertRuleVO.builder().id("rule-1").alert("CPUAlert")
                .enabled(true).updatedAt("2025-01-01 00:00:00").build();
        when(alertRepository.findRuleById("rule-1")).thenReturn(existing);
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.toggleRule("rule-1", false);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void toggleRuleShouldThrowWhenRuleNotFound() {
        when(alertRepository.findRuleById("non-existent")).thenReturn(null);

        assertThatThrownBy(() -> alertService.toggleRule("non-existent", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Alert rule not found: non-existent");
    }

    @Test
    void deleteRuleShouldCallRepository() {
        doNothing().when(alertRepository).deleteRule("rule-1");

        alertService.deleteRule("rule-1");

        verify(alertRepository).deleteRule("rule-1");
    }

    @Test
    void listAlertsShouldReturnAlertsForLevel() {
        SystemAlertVO alert1 = SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();
        SystemAlertVO alert2 = SystemAlertVO.builder().id("a2").level(AlertLevel.error)
                .title("High Latency").acknowledged(false).build();
        when(alertRepository.findAlerts("error")).thenReturn(Arrays.asList(alert1, alert2));

        List<SystemAlertVO> result = alertService.listAlerts("error");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLevel()).isEqualTo(AlertLevel.error);
        assertThat(result.get(0).getTitle()).isEqualTo("Broker Down");
        verify(alertRepository).findAlerts("error");
    }

    @Test
    void listAlertsShouldReturnAllAlertsWhenLevelIsNull() {
        SystemAlertVO alert = SystemAlertVO.builder().id("a1").level(AlertLevel.warning)
                .title("Slow Consumer").build();
        when(alertRepository.findAlerts(null)).thenReturn(List.of(alert));

        List<SystemAlertVO> result = alertService.listAlerts(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo(AlertLevel.warning);
        verify(alertRepository).findAlerts(null);
    }

    @Test
    void acknowledgeAlertShouldSetAcknowledgedTrue() {
        SystemAlertVO existing = SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();
        when(alertRepository.findAlerts(null)).thenReturn(List.of(existing));
        when(alertRepository.saveAlert(any(SystemAlertVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SystemAlertVO result = alertService.acknowledgeAlert("a1");

        assertThat(result.isAcknowledged()).isTrue();
        verify(alertRepository).saveAlert(result);
    }

    @Test
    void acknowledgeAlertShouldThrowWhenAlertNotFound() {
        when(alertRepository.findAlerts(null)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> alertService.acknowledgeAlert("non-existent"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System alert not found: non-existent");
    }

    @Test
    void clearAcknowledgedShouldReturnDeletedCount() {
        when(alertRepository.deleteAcknowledgedAlerts()).thenReturn(3);

        int result = alertService.clearAcknowledged();

        assertThat(result).isEqualTo(3);
        verify(alertRepository).deleteAcknowledgedAlerts();
    }

    @Test
    void clearAcknowledgedShouldReturnZeroWhenNoneAcknowledged() {
        when(alertRepository.deleteAcknowledgedAlerts()).thenReturn(0);

        int result = alertService.clearAcknowledged();

        assertThat(result).isZero();
    }
}