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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlertServiceTest {

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private OperationAuditService operationAuditService;

    private AlertService alertService;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        alertService = new AlertService(alertRepository, new AlertRuleAssetService(), operationAuditService);
    }

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
                .contains("RocketMQBrokerDown")
                .contains("up{job=~\".*rocketmq.*broker.*\"} == 0")
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
                .enabled(true)
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
    void exportPrometheusRulesYamlShouldEmitSingleGroupForSameTeamRules() {
        AlertRuleVO first = AlertRuleVO.builder()
                .name("Lag Alert A")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1000)
                .duration("3m")
                .description("First lag alert")
                .enabled(true)
                .build();
        AlertRuleVO second = AlertRuleVO.builder()
                .name("Lag Alert B")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(2000)
                .duration("5m")
                .description("Second lag alert")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(first, second));

        String result = alertService.exportPrometheusRulesYaml();

        long groupOccurrences = result.split("- name: rocketmq-consumer.rules", -1).length - 1;
        assertThat(groupOccurrences).isEqualTo(1);
        assertThat(result)
                .contains("# Rule 1: LagAlertA")
                .contains("# Rule 2: LagAlertB")
                .contains("expr: rocketmq_consumer_lag_messages > 1000")
                .contains("expr: rocketmq_consumer_lag_messages > 2000");
    }

    @Test
    void exportPrometheusRulesYamlShouldDisambiguateDuplicateAlertNames() {
        AlertRuleVO first = AlertRuleVO.builder()
                .name("High Lag")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1000)
                .enabled(true)
                .build();
        AlertRuleVO second = AlertRuleVO.builder()
                .name("High-Lag")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(2000)
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(first, second));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("- alert: HighLag\n")
                .contains("- alert: HighLag_2\n");
    }

    @Test
    void exportPrometheusRulesYamlShouldRenderReplicationLagRuleWithScopeAndSeverity() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Replication Lag High")
                .metric("rocketmq_broker_replication_lag_bytes")
                .operator(">")
                .threshold(104857600)
                .duration("5m")
                .brokerName("broker-a")
                .clusterName("DefaultCluster")
                .severity("critical")
                .description("Slave falls behind master")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("rocketmq-broker.rules")
                .contains("expr: rocketmq_broker_replication_lag_bytes{cluster=\"DefaultCluster\",broker=\"broker-a\"} > 104857600")
                .contains("for: 5m")
                .contains("severity: critical");
    }

    @Test
    void exportPrometheusRulesYamlShouldIgnoreWildcardScopeAndInvalidSeverity() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Replication Lag Any Broker")
                .metric("rocketmq_broker_replication_lag_bytes")
                .operator(">")
                .threshold(1024)
                .brokerName("*")
                .clusterName(" ")
                .severity("fatal")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("expr: rocketmq_broker_replication_lag_bytes > 1024")
                .contains("severity: warning");
    }

    @Test
    void exportPrometheusRulesYamlShouldReplaceInvalidPrometheusFields() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Malformed rule")
                .metric("up) or vector(1")
                .operator("> 0 or")
                .threshold(10)
                .duration("5xyz")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("expr: rocketmq_consumer_lag_messages > 10")
                .contains("for: 5m")
                .doesNotContain("vector(1", "> 0 or", "5xyz");
    }

    @Test
    void exportPrometheusRulesYamlShouldNormalizeSeverityIndependentlyOfDefaultLocale() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Informational Alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1)
                .severity("INFO")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));
        Locale originalLocale = Locale.getDefault();

        String result;
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            result = alertService.exportPrometheusRulesYaml();
        } finally {
            Locale.setDefault(originalLocale);
        }

        assertThat(result).contains("severity: info");
    }

    @Test
    void exportPrometheusRulesYamlShouldEscapeSpecialCharacters() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Scoped rule")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1)
                .clusterName("prod\"east\\dc\nline")
                .brokerName("broker\tone")
                .description("Summary \"quoted\" \\ path\nnext - Details\twith\rline")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result).contains(
                "expr: rocketmq_consumer_lag_messages{cluster=\"prod\\\"east\\\\dc\\nline\","
                        + "broker=\"broker\\tone\"} > 1");
        JsonNode exportedRule = new ObjectMapper(new YAMLFactory()).readTree(result)
                .path("groups").get(0).path("rules").get(0);
        assertThat(exportedRule.path("annotations").path("summary").asText())
                .isEqualTo("Summary \"quoted\" \\ path\nnext");
        assertThat(exportedRule.path("annotations").path("description").asText())
                .isEqualTo("Details\twith\rline");
    }

    @Test
    void exportPrometheusRulesYamlShouldUseFallbackWhenAlertNameHasNoValidCharacters() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name(" - !")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1)
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        JsonNode exportedRule = new ObjectMapper(new YAMLFactory()).readTree(result)
                .path("groups").get(0).path("rules").get(0);
        assertThat(exportedRule.path("alert").asText()).isEqualTo("RocketMQAlert");
    }

    @Test
    void exportPrometheusRulesYamlShouldExcludeDisabledRules() {
        AlertRuleVO enabled = AlertRuleVO.builder()
                .name("Enabled Lag Alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1000)
                .enabled(true)
                .build();
        AlertRuleVO disabled = AlertRuleVO.builder()
                .name("Disabled Lag Alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(2000)
                .enabled(false)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(enabled, disabled));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("EnabledLagAlert")
                .doesNotContain("DisabledLagAlert")
                .doesNotContain(" > 2000");
    }

    @Test
    void exportPrometheusRulesYamlShouldUseDefaultRulesWhenAllConfiguredRulesAreDisabled() {
        AlertRuleVO disabled = AlertRuleVO.builder()
                .name("Disabled Lag Alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(2000)
                .enabled(false)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(disabled));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("RocketMQBrokerDown")
                .doesNotContain("DisabledLagAlert");
    }

    @Test
    void createRuleShouldPreserveReplicationScopeFields() {
        AlertRuleVO input = AlertRuleVO.builder()
                .name("Replication Lag High")
                .metric("rocketmq_broker_replication_lag_bytes")
                .operator(">")
                .threshold(104857600)
                .thresholdUnit("B")
                .brokerName("broker-a")
                .clusterName("DefaultCluster")
                .severity("critical")
                .build();
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.createRule(input);

        assertThat(result.getId()).isNotNull().isNotEmpty();
        assertThat(result.getBrokerName()).isEqualTo("broker-a");
        assertThat(result.getClusterName()).isEqualTo("DefaultCluster");
        assertThat(result.getSeverity()).isEqualTo("critical");
        verify(alertRepository).saveRule(result);
        verify(operationAuditService).record(eq("CREATE_ALERT_RULE"), eq("ALERT_RULE"), eq(result.getId()),
                eq(null), eq("name=Replication Lag High"), eq("SUCCESS"), eq(null));
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
    void createRuleShouldRejectNullRequest() {
        assertThatThrownBy(() -> alertService.createRule(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).saveRule(any());
    }

    @Test
    void updateRuleShouldUpdateExistingRule() {
        AlertRuleVO update = AlertRuleVO.builder().id("rule-1").name("CPU Alert").threshold(90.0).build();
        when(alertRepository.replaceRule(update)).thenReturn(true);

        AlertRuleVO result = alertService.updateRule(update);

        assertThat(result.getId()).isEqualTo("rule-1");
        assertThat(result.getThreshold()).isEqualTo(90.0);
        verify(alertRepository).replaceRule(update);
        verify(operationAuditService).record(eq("UPDATE_ALERT_RULE"), eq("ALERT_RULE"), eq("rule-1"),
                eq(null), eq("name=CPU Alert"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateRuleShouldRejectNullRequest() {
        assertThatThrownBy(() -> alertService.updateRule(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldRejectNullId() {
        AlertRuleVO update = AlertRuleVO.builder().name("CPU Alert").build();

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldRejectBlankId() {
        AlertRuleVO update = AlertRuleVO.builder().id("  ").name("CPU Alert").build();

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldRejectUnknownId() {
        AlertRuleVO update = AlertRuleVO.builder().id("missing").name("CPU Alert").build();
        when(alertRepository.replaceRule(update)).thenReturn(false);

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule not found: missing")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
        verify(alertRepository).replaceRule(update);
        verify(alertRepository, never()).saveRule(any());
    }

    @Test
    void toggleRuleShouldEnableRule() {
        AlertRuleVO existing = AlertRuleVO.builder().id("rule-1").name("CPU Alert").enabled(false).build();
        when(alertRepository.findAllRules()).thenReturn(List.of(existing));
        when(alertRepository.saveRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.toggleRule("rule-1", true);

        assertThat(result.isEnabled()).isTrue();
        verify(alertRepository).saveRule(result);
        verify(operationAuditService).record(eq("TOGGLE_ALERT_RULE"), eq("ALERT_RULE"), eq("rule-1"),
                eq(null), eq("enabled=true"), eq("SUCCESS"), eq(null));
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
        when(alertRepository.deleteRule("rule-1")).thenReturn(true);

        alertService.deleteRule("rule-1");

        verify(alertRepository).deleteRule("rule-1");
        verify(operationAuditService).record(eq("DELETE_ALERT_RULE"), eq("ALERT_RULE"), eq("rule-1"),
                eq(null), eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteRuleShouldRejectUnknownRule() {
        when(alertRepository.deleteRule("missing")).thenReturn(false);

        assertThatThrownBy(() -> alertService.deleteRule("missing"))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
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
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenReturn(true);

        SystemAlertVO result = alertService.acknowledgeAlert("a1");

        assertThat(result.isAcknowledged()).isTrue();
        verify(alertRepository).acknowledgeAlert(result);
        verify(operationAuditService).record(eq("ACKNOWLEDGE_SYSTEM_ALERT"), eq("SYSTEM_ALERT"), eq("a1"),
                eq(null), eq("acknowledged=true"), eq("SUCCESS"), eq(null));
    }

    @Test
    void acknowledgeAlertShouldThrowWhenAlertNotFound() {
        when(alertRepository.findAlerts(null)).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> alertService.acknowledgeAlert("non-existent"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System alert not found: non-existent");
    }

    @Test
    void acknowledgeAlertShouldRejectConcurrentRemoval() {
        SystemAlertVO existing = SystemAlertVO.builder().id("a1").level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();
        when(alertRepository.findAlerts(null)).thenReturn(List.of(existing));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenReturn(false);

        assertThatThrownBy(() -> alertService.acknowledgeAlert("a1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("System alert not found: a1")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));

        verify(operationAuditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void clearAcknowledgedShouldReturnDeletedCount() {
        when(alertRepository.deleteAcknowledgedAlerts()).thenReturn(3);

        int result = alertService.clearAcknowledged();

        assertThat(result).isEqualTo(3);
        verify(alertRepository).deleteAcknowledgedAlerts();
        verify(operationAuditService).record(eq("CLEAR_ACKNOWLEDGED_SYSTEM_ALERTS"), eq("SYSTEM_ALERT"),
                eq(null), eq(null), eq("deleted=3"), eq("SUCCESS"), eq(null));
    }

    @Test
    void clearAcknowledgedShouldReturnZeroWhenNoneAcknowledged() {
        when(alertRepository.deleteAcknowledgedAlerts()).thenReturn(0);

        int result = alertService.clearAcknowledged();

        assertThat(result).isZero();
    }
}
