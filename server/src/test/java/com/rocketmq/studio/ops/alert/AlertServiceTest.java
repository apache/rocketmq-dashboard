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
        AlertRuleVO rule1 = AlertRuleVO.builder().id("1").name("High CPU").metric("cpu_usage")
                .operator(">").threshold(90.0).enabled(true).build();
        AlertRuleVO rule2 = AlertRuleVO.builder().id("2").name("Low Disk").metric("disk_free")
                .operator("<").threshold(10.0).enabled(false).build();
        when(alertRepository.findAllRules()).thenReturn(Arrays.asList(rule1, rule2));

        List<AlertRuleVO> result = alertService.listRules();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("High CPU");
        assertThat(result.get(0).isEnabled()).isTrue();
        assertThat(result.get(1).getName()).isEqualTo("Low Disk");
        assertThat(result.get(1).isEnabled()).isFalse();
    }

    @Test
    void listRulesShouldReturnEmptyListWhenNoRules() {
        when(alertRepository.findAllRules()).thenReturn(Collections.emptyList());

        List<AlertRuleVO> result = alertService.listRules();

        assertThat(result).isEmpty();
    }

    @Test
    void exportPrometheusRulesYamlShouldReturnDefaultRulesWhenRepositoryIsEmpty() {
        when(alertRepository.findAllRules()).thenReturn(Collections.emptyList());

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("groups:")
                .contains("# Rule 1: RocketMQBrokerDown")
                .contains("rocketmq_consumer_lag_messages > 100000")
                .contains("rocketmq_producer_send_to_back_rt > 1000")
                .contains("severity: critical");
    }

    @Test
    void exportPrometheusRulesYamlShouldConvertConfiguredRules() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("High Lag Alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(5000)
                .duration("3m")
                .description("Lag too high")
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("rocketmq-consumer.rules")
                .contains("# Rule 1: HighLagAlert")
                .contains("expr: rocketmq_consumer_lag_messages > 5000")
                .contains("for: 3m")
                .contains("description: \"Lag too high\"");
    }

    @Test
    void createRuleShouldAssignId() {
        AlertRuleVO input = AlertRuleVO.builder().name("New Rule").metric("tps")
                .operator(">").threshold(1000.0).build();
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.createRule(input);

        assertThat(result.getId()).isNotNull().isNotEmpty();
        assertThat(result.getName()).isEqualTo("New Rule");
        assertThat(result.getMetric()).isEqualTo("tps");
        verify(alertRepository).saveRule(result);
    }

    @Test
    void createRuleShouldGenerateUniqueIds() {
        AlertRuleVO input1 = AlertRuleVO.builder().name("Rule 1").build();
        AlertRuleVO input2 = AlertRuleVO.builder().name("Rule 2").build();
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result1 = alertService.createRule(input1);
        AlertRuleVO result2 = alertService.createRule(input2);

        assertThat(result1.getId()).isNotEqualTo(result2.getId());
    }

    @Test
    void toggleRuleShouldEnableRule() {
        AlertRuleVO existing = AlertRuleVO.builder().id("rule-1").name("CPU Alert").enabled(false).build();
        when(alertRepository.findAllRules()).thenReturn(List.of(existing));
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.toggleRule("rule-1", true);

        assertThat(result.isEnabled()).isTrue();
        verify(alertRepository).saveRule(result);
    }

    @Test
    void toggleRuleShouldDisableRule() {
        AlertRuleVO existing = AlertRuleVO.builder().id("rule-1").name("CPU Alert").enabled(true).build();
        when(alertRepository.findAllRules()).thenReturn(List.of(existing));
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.toggleRule("rule-1", false);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void toggleRuleShouldThrowWhenRuleNotFound() {
        when(alertRepository.findAllRules()).thenReturn(Collections.emptyList());

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
