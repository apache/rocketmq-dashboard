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
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.AlertLevel;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
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
    @Mock
    private AlertStateRepository alertStateRepository;

    private AlertService alertService;

    @org.junit.jupiter.api.BeforeEach
    void setUpTest() {
        alertService = new AlertService(alertRepository, alertStateRepository, new AlertRuleAssetService(),
                operationAuditService);
    }

    @Test
    void listRulesShouldReturnAllRulesTest() {
        AlertRuleVO rule1 = AlertRuleVO.builder().id(1L).name("High CPU").metric("cpu_usage")
                .operator(">").threshold(90.0).enabled(true).build();
        AlertRuleVO rule2 = AlertRuleVO.builder().id(2L).name("Low Disk").metric("disk_free")
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
    void listRulesShouldReturnEmptyListWhenNoRulesTest() {
        when(alertRepository.findAllRules()).thenReturn(Collections.emptyList());

        List<AlertRuleVO> result = alertService.listRules();

        assertThat(result).isEmpty();
    }

    @Test
    void listRulesShouldNormalizeSearchAndDelegateFiltersToRepository() {
        PageResult<AlertRuleVO> repositoryPage = PageResult.of(List.of(), 0, 2, 20);
        when(alertRepository.findRulePage("lag", true, 2, 20)).thenReturn(repositoryPage);

        PageResult<AlertRuleVO> result = alertService.listRules("  lag  ", true, 2, 20);

        assertThat(result).isSameAs(repositoryPage);
        verify(alertRepository).findRulePage("lag", true, 2, 20);
    }

    @Test
    void listRulesShouldRejectInvalidPaginationBeforeRepositoryAccess() {
        assertThatThrownBy(() -> alertService.listRules("lag", true, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("page must be greater than zero");
        assertThatThrownBy(() -> alertService.listRules("lag", true, 1, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be between 1 and 100");
        assertThatThrownBy(() -> alertService.listRules("lag", true, 1, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be between 1 and 100");

        verify(alertRepository, never()).findRulePage(any(), any(), anyInt(), anyInt());
    }

    @Test
    void listRulesShouldSeparateBusinessAndClusterDomainsTest() {
        AlertRuleVO business = AlertRuleVO.builder().id(1L).name("Consumer lag").build();
        AlertRuleVO cluster = AlertRuleVO.builder()
                .id(2L).name("Broker unavailable").domain(AlertDomain.CLUSTER).build();
        when(alertRepository.findAllRules()).thenReturn(List.of(business, cluster));

        assertThat(alertService.listRules(AlertDomain.BUSINESS)).containsExactly(business);
        assertThat(alertService.listRules(AlertDomain.CLUSTER)).containsExactly(cluster);
    }

    @Test
    void listRulesPageShouldDelegateFiltersWithoutReadingAllRulesTest() {
        PageResult<AlertRuleVO> expected = PageResult.of(List.of(), 0, 2, 10);
        when(alertRepository.findRulesPage(any(AlertRuleQuery.class))).thenReturn(expected);

        assertThat(alertService.listRules(AlertDomain.BUSINESS, " lag ", true, 2, 10)).isSameAs(expected);

        verify(alertRepository).findRulesPage(argThat(query -> query.domain() == AlertDomain.BUSINESS
                && "lag".equals(query.search()) && Boolean.TRUE.equals(query.enabled())
                && query.page() == 2 && query.pageSize() == 10));
        verify(alertRepository, never()).findAllRules();
    }

    @Test
    void listRulesPageShouldRejectInvalidPageBoundsTest() {
        assertThatThrownBy(() -> alertService.listRules(AlertDomain.BUSINESS, null, null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid page or pageSize");
        assertThatThrownBy(() -> alertService.listRules(AlertDomain.CLUSTER, null, null, 1, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Invalid page or pageSize");
        verify(alertRepository, never()).findRulesPage(any(AlertRuleQuery.class));
    }

    @Test
    void clusterRuleOperationsShouldRejectBusinessRulesTest() {
        AlertRuleVO business = AlertRuleVO.builder().id(1L).name("Consumer lag").build();
        when(alertRepository.findRuleById(1L)).thenReturn(Optional.of(business));

        assertThatThrownBy(() -> alertService.toggleRule(AlertDomain.CLUSTER, 1L, false))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule not found: 1")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));

        verify(alertRepository, never()).replaceRule(any());
        verify(alertRepository, never()).findAllRules();
    }

    @Test
    void clusterRuleOperationsShouldUpdateClusterRulesOnlyTest() {
        AlertRuleVO cluster = AlertRuleVO.builder().id(2L).name("Broker unavailable")
                .domain(AlertDomain.CLUSTER).enabled(false).build();
        when(alertRepository.findRuleById(2L)).thenReturn(Optional.of(cluster));
        when(alertRepository.replaceRule(cluster)).thenReturn(true);

        AlertRuleVO updated = alertService.toggleRule(AlertDomain.CLUSTER, 2L, true);

        assertThat(updated.isEnabled()).isTrue();
        verify(alertRepository).replaceRule(cluster);
        verify(alertRepository, never()).findAllRules();
        verify(alertStateRepository).deleteByRuleId(2L);
    }

    @Test
    void updatingRuleShouldResetItsPreviousEvaluationStateTest() {
        AlertRuleVO rule = AlertRuleVO.builder().id(4L).name("Lag").metric("consumer.lag.total")
                .operator(">").threshold(100).instanceId("local").build();
        when(alertRepository.replaceRule(rule)).thenReturn(true);

        alertService.updateRule(rule);

        verify(alertStateRepository).deleteByRuleId(4L);
    }

    @Test
    void deletingRuleShouldRemoveItsStoredEvaluationStateTest() {
        when(alertRepository.deleteRule(4L)).thenReturn(true);

        alertService.deleteRule(4L);

        verify(alertStateRepository).deleteByRuleId(4L);
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
    void exportPrometheusRulesYamlShouldConvertConfiguredRulesTest() {
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
    void exportPrometheusRulesYamlShouldExcludeNativeClusterRulesTest() {
        AlertRuleVO legacy = AlertRuleVO.builder()
                .name("High Lag Alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(5000)
                .enabled(true)
                .build();
        AlertRuleVO nativeRule = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .name("Native Disk Usage")
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(85)
                .thresholdUnit("%")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(legacy, nativeRule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result)
                .contains("HighLagAlert")
                .doesNotContain("NativeDiskUsage");
    }

    @Test
    void exportPrometheusRulesYamlShouldEmitSingleGroupForSameTeamRulesTest() {
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
    void exportPrometheusRulesYamlShouldDisambiguateDuplicateAlertNamesTest() {
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
    void exportPrometheusRulesYamlShouldInferTeamWithoutMetricCaseSensitivityTest() {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Uppercase lag")
                .metric("ROCKETMQ_CONSUMER_LAG_MESSAGES")
                .operator(">")
                .threshold(10)
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        assertThat(result).contains("- name: rocketmq-consumer.rules");
    }

    @Test
    void exportPrometheusRulesYamlShouldRenderReplicationLagRuleWithScopeAndSeverityTest() {
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
    void exportPrometheusRulesYamlShouldIgnoreWildcardScopeAndInvalidSeverityTest() {
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
    void exportPrometheusRulesYamlShouldReplaceInvalidPrometheusFieldsTest() {
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
    void exportPrometheusRulesYamlShouldNormalizeSeverityIndependentlyOfDefaultLocaleTest() {
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
    void exportPrometheusRulesYamlShouldNotEmitABlankSummaryTest() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("Lag alert")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1)
                .description(" - consumer is behind")
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        JsonNode exportedRule = new ObjectMapper(new YAMLFactory())
                .readTree(alertService.exportPrometheusRulesYaml())
                .path("groups").get(0).path("rules").get(0);

        assertThat(exportedRule.path("annotations").path("summary").asText()).isEqualTo("Lag alert");
        assertThat(exportedRule.path("annotations").path("description").asText())
                .isEqualTo("consumer is behind");
    }

    @Test
    void exportPrometheusRulesYamlShouldEscapeSpecialCharactersTest() throws Exception {
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
    void exportPrometheusRulesYamlShouldUseFallbackWhenAlertNameHasNoValidCharactersTest() throws Exception {
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
    void exportPrometheusRulesYamlShouldPrefixAlertNamesStartingWithDigitTest() throws Exception {
        AlertRuleVO rule = AlertRuleVO.builder()
                .name("5xx spike")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(1)
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(rule));

        String result = alertService.exportPrometheusRulesYaml();

        JsonNode exportedRule = new ObjectMapper(new YAMLFactory()).readTree(result)
                .path("groups").get(0).path("rules").get(0);
        assertThat(exportedRule.path("alert").asText()).isEqualTo("A_5xxspike");
        assertThat(result).contains("# Rule 1: A_5xxspike");
    }

    @Test
    void exportPrometheusRulesYamlShouldReplaceNonFiniteThresholdsTest() throws Exception {
        AlertRuleVO nanRule = AlertRuleVO.builder()
                .name("Bad Threshold A")
                .metric("rocketmq_consumer_lag_messages")
                .operator(">")
                .threshold(Double.NaN)
                .enabled(true)
                .build();
        AlertRuleVO infinityRule = AlertRuleVO.builder()
                .name("Bad Threshold B")
                .metric("rocketmq_consumer_lag_messages")
                .operator("<")
                .threshold(Double.POSITIVE_INFINITY)
                .enabled(true)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(nanRule, infinityRule));

        String result = alertService.exportPrometheusRulesYaml();

        JsonNode rules = new ObjectMapper(new YAMLFactory()).readTree(result).path("groups").get(0).path("rules");
        assertThat(rules.get(0).path("expr").asText()).isEqualTo("rocketmq_consumer_lag_messages > 0");
        assertThat(rules.get(1).path("expr").asText()).isEqualTo("rocketmq_consumer_lag_messages < 0");
        assertThat(result).doesNotContain("NaN", "Infinity");
    }

    @Test
    void exportPrometheusRulesYamlShouldExcludeDisabledRulesTest() {
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
    void exportPrometheusRulesYamlShouldUseDefaultRulesWhenAllConfiguredRulesAreDisabledTest() {
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
    void createRuleShouldPreserveReplicationScopeFieldsTest() {
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
        when(alertRepository.insertRule(any(AlertRuleVO.class))).thenAnswer(invocation -> {
            AlertRuleVO saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });

        AlertRuleVO result = alertService.createRule(input);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getBrokerName()).isEqualTo("broker-a");
        assertThat(result.getClusterName()).isEqualTo("DefaultCluster");
        assertThat(result.getSeverity()).isEqualTo("critical");
        verify(alertRepository).insertRule(result);
        verify(operationAuditService).record(eq("CREATE_ALERT_RULE"), eq("ALERT_RULE"), eq("1"),
                eq(null), eq("name=Replication Lag High"), eq("SUCCESS"), eq(null));
    }

    @Test
    void createRuleShouldAssignIdTest() {
        AlertRuleVO input = AlertRuleVO.builder().name("New Rule").metric("tps")
                .operator(">").threshold(1000.0).build();
        when(alertRepository.insertRule(any(AlertRuleVO.class))).thenAnswer(invocation -> {
            AlertRuleVO saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(1L);
            }
            return saved;
        });

        AlertRuleVO result = alertService.createRule(input);

        assertThat(result.getId()).isNotNull();
        assertThat(result.getName()).isEqualTo("New Rule");
        assertThat(result.getMetric()).isEqualTo("tps");
        verify(alertRepository).insertRule(result);
    }

    @Test
    void createRuleShouldGenerateUniqueIdsTest() {
        AlertRuleVO input1 = AlertRuleVO.builder().name("Rule 1").build();
        AlertRuleVO input2 = AlertRuleVO.builder().name("Rule 2").build();
        java.util.concurrent.atomic.AtomicLong sequence = new java.util.concurrent.atomic.AtomicLong();
        when(alertRepository.insertRule(any(AlertRuleVO.class))).thenAnswer(invocation -> {
            AlertRuleVO saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(sequence.incrementAndGet());
            }
            return saved;
        });

        AlertRuleVO result1 = alertService.createRule(input1);
        AlertRuleVO result2 = alertService.createRule(input2);

        assertThat(result1.getId()).isNotEqualTo(result2.getId());
    }

    @Test
    void createRuleShouldRejectNullRequestTest() {
        assertThatThrownBy(() -> alertService.createRule(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).insertRule(any());
    }

    @Test
    void createRuleShouldRejectProvidedIdBeforePersistenceTest() {
        AlertRuleVO input = AlertRuleVO.builder().id(7L).name("Existing rule").metric("tps").build();

        assertThatThrownBy(() -> alertService.createRule(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID must not be provided when creating a rule")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).insertRule(any());
        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void createRuleShouldRejectBlankNameTest() {
        AlertRuleVO input = AlertRuleVO.builder().name(" ").metric("tps").build();

        assertThatThrownBy(() -> alertService.createRule(input))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule name is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).insertRule(any());
        verify(operationAuditService, never()).record(anyString(), anyString(), anyString(),
                any(), any(), anyString(), any());
    }

    @Test
    void updateRuleShouldRejectBlankNameBeforeRepositoryUpdateTest() {
        AlertRuleVO update = AlertRuleVO.builder().id(1L).name(" ").metric("tps").build();

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule name is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).replaceRule(any());
        verify(operationAuditService, never()).record(anyString(), anyString(), anyString(),
                any(), any(), anyString(), any());
    }

    @Test
    void createRuleShouldNormalizeNameBeforeSavingAndAuditingTest() {
        AlertRuleVO input = AlertRuleVO.builder().name(" CPU Alert ").metric("tps").build();
        when(alertRepository.insertRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AlertRuleVO result = alertService.createRule(input);

        assertThat(result.getName()).isEqualTo("CPU Alert");
        verify(alertRepository).insertRule(argThat(rule -> "CPU Alert".equals(rule.getName())));
        verify(operationAuditService).record(eq("CREATE_ALERT_RULE"), eq("ALERT_RULE"), anyString(),
                eq(null), eq("name=CPU Alert"), eq("SUCCESS"), eq(null));
    }

    @Test
    void createRuleShouldRejectDuplicateEvaluationConditionsTest() {
        AlertRuleVO existing = AlertRuleVO.builder()
                .id(1L)
                .domain(AlertDomain.BUSINESS)
                .name("Existing rule")
                .metric("consumer_lag")
                .operator(">")
                .threshold(1000)
                .duration("5m")
                .aggregation("LAST")
                .windowSeconds(0)
                .consumerGroup("group-a")
                .enabled(true)
                .build();
        AlertRuleVO duplicate = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS)
                .name("Another label")
                .metric(" consumer_lag ")
                .operator(" > ")
                .threshold(1000.0)
                .duration("5M")
                .consumerGroup(" group-a ")
                .channels(List.of("email"))
                .severity("critical")
                .enabled(false)
                .build();
        when(alertRepository.findAllRules()).thenReturn(List.of(existing));

        assertThatThrownBy(() -> alertService.createRule(duplicate))
                .isInstanceOf(BusinessException.class)
                .hasMessage("An alert rule with the same evaluation conditions already exists")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));

        verify(alertRepository, never()).insertRule(any());
    }

    @Test
    void createRuleShouldAllowRulesWithDifferentEvaluationScopeTest() {
        AlertRuleVO existing = AlertRuleVO.builder()
                .id(1L).domain(AlertDomain.BUSINESS).name("Group A").metric("consumer_lag")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a").build();
        AlertRuleVO distinct = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).name("Group B").metric("consumer_lag")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-b").build();
        when(alertRepository.findAllRules()).thenReturn(List.of(existing));
        when(alertRepository.insertRule(any(AlertRuleVO.class))).thenAnswer(invocation -> invocation.getArgument(0));

        alertService.createRule(distinct);

        verify(alertRepository).insertRule(distinct);
    }

    @Test
    void updateRuleShouldRejectAnotherRulesEvaluationConditionsTest() {
        AlertRuleVO existing = AlertRuleVO.builder()
                .id(1L).domain(AlertDomain.BUSINESS).name("Existing").metric("consumer_lag")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a").build();
        AlertRuleVO update = AlertRuleVO.builder()
                .id(2L).domain(AlertDomain.BUSINESS).name("Updated").metric("consumer_lag")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a").build();
        when(alertRepository.findAllRules()).thenReturn(List.of(existing, update));

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("An alert rule with the same evaluation conditions already exists")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(409));

        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldNormalizeNameBeforeReplacingAndAuditingTest() {
        AlertRuleVO update = AlertRuleVO.builder()
                .id(1L)
                .name(" CPU Alert ")
                .threshold(90.0)
                .build();
        when(alertRepository.replaceRule(any(AlertRuleVO.class))).thenReturn(true);

        AlertRuleVO result = alertService.updateRule(update);

        assertThat(result.getName()).isEqualTo("CPU Alert");
        verify(alertRepository).replaceRule(argThat(rule -> "CPU Alert".equals(rule.getName())));
        verify(operationAuditService).record(eq("UPDATE_ALERT_RULE"), eq("ALERT_RULE"), eq("1"),
                eq(null), eq("name=CPU Alert"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateRuleShouldUpdateExistingRuleTest() {
        AlertRuleVO update = AlertRuleVO.builder().id(1L).name("CPU Alert").threshold(90.0).build();
        when(alertRepository.replaceRule(update)).thenReturn(true);

        AlertRuleVO result = alertService.updateRule(update);

        assertThat(result.getId()).isEqualTo(1L);
        assertThat(result.getThreshold()).isEqualTo(90.0);
        verify(alertRepository).replaceRule(update);
        verify(operationAuditService).record(eq("UPDATE_ALERT_RULE"), eq("ALERT_RULE"), eq("1"),
                eq(null), eq("name=CPU Alert"), eq("SUCCESS"), eq(null));
    }

    @Test
    void updateRuleShouldRejectNullRequestTest() {
        assertThatThrownBy(() -> alertService.updateRule(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldRejectNullIdTest() {
        AlertRuleVO update = AlertRuleVO.builder().name("CPU Alert").build();

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldRejectBlankIdTest() {
        AlertRuleVO update = AlertRuleVO.builder().id(null).name("CPU Alert").build();

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        verify(alertRepository, never()).replaceRule(any());
    }

    @Test
    void updateRuleShouldRejectUnknownIdTest() {
        AlertRuleVO update = AlertRuleVO.builder().id(999L).name("CPU Alert").build();
        when(alertRepository.replaceRule(update)).thenReturn(false);

        assertThatThrownBy(() -> alertService.updateRule(update))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule not found: 999")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
        verify(alertRepository).replaceRule(update);
        verify(alertRepository, never()).insertRule(any());
    }

    @Test
    void toggleRuleShouldEnableRuleTest() {
        AlertRuleVO existing = AlertRuleVO.builder().id(1L).name("CPU Alert").enabled(false).build();
        when(alertRepository.findRuleById(1L)).thenReturn(Optional.of(existing));
        when(alertRepository.replaceRule(existing)).thenReturn(true);

        AlertRuleVO result = alertService.toggleRule(1L, true);

        assertThat(result.isEnabled()).isTrue();
        verify(alertRepository).replaceRule(result);
        verify(alertRepository, never()).findAllRules();
        verify(operationAuditService).record(eq("TOGGLE_ALERT_RULE"), eq("ALERT_RULE"), eq("1"),
                eq(null), eq("enabled=true"), eq("SUCCESS"), eq(null));
    }

    @Test
    void toggleRuleShouldDisableRuleTest() {
        AlertRuleVO existing = AlertRuleVO.builder().id(1L).name("CPU Alert").enabled(true).build();
        when(alertRepository.findRuleById(1L)).thenReturn(Optional.of(existing));
        when(alertRepository.replaceRule(existing)).thenReturn(true);

        AlertRuleVO result = alertService.toggleRule(1L, false);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void toggleRuleShouldRejectBlankIdBeforeLoadingRulesTest() {
        assertThatThrownBy(() -> alertService.toggleRule(null, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).findAllRules();
        verify(alertRepository, never()).findRuleById(any());
    }

    @Test
    void toggleRuleShouldUseADirectIdLookupWhenRuleIsMissing() {
        when(alertRepository.findRuleById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.toggleRule(999L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule not found: 999");
    }

    @Test
    void toggleRuleShouldThrowWhenRuleNotFound() {
        when(alertRepository.findRuleById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> alertService.toggleRule(999L, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Alert rule not found: 999");
    }

    @Test
    void deleteRuleShouldRejectBlankIdBeforeDeletingTest() {
        assertThatThrownBy(() -> alertService.deleteRule(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alert rule ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).deleteRule(any());
    }

    @Test
    void deleteRuleShouldCallRepositoryTest() {
        when(alertRepository.deleteRule(1L)).thenReturn(true);

        alertService.deleteRule(1L);

        verify(alertRepository).deleteRule(1L);
        verify(operationAuditService).record(eq("DELETE_ALERT_RULE"), eq("ALERT_RULE"), eq("1"),
                eq(null), eq(null), eq("SUCCESS"), eq(null));
    }

    @Test
    void deleteRuleShouldRejectUnknownRuleTest() {
        when(alertRepository.deleteRule(999L)).thenReturn(false);

        assertThatThrownBy(() -> alertService.deleteRule(999L))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void bulkToggleShouldDeduplicateIdsAndReportMissingRulesTest() {
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).name("High CPU").enabled(false).build();
        when(alertRepository.findRulesByIds(List.of(1L, 999L))).thenReturn(List.of(rule));
        when(alertRepository.replaceRule(any(AlertRuleVO.class))).thenReturn(true);

        AlertRuleBulkResultVO result = alertService.bulkToggleRules(
                List.of(1L, 999L, 1L), true);

        assertThat(result.getSucceededIds()).containsExactly(1L);
        assertThat(result.getFailures()).containsEntry(999L, "Alert rule not found");
        assertThat(result.getUpdatedRules()).singleElement()
                .extracting(AlertRuleVO::isEnabled).isEqualTo(true);
        verify(alertRepository).replaceRule(rule);
        verify(alertRepository, never()).findAllRules();
        verify(alertStateRepository).deleteByRuleId(1L);
    }

    @Test
    void domainBulkOperationsShouldResetEachMutatedRuleStateTest() {
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).name("Broker unavailable")
                .domain(AlertDomain.CLUSTER).enabled(false).build();
        when(alertRepository.findRulesByIds(List.of(1L))).thenReturn(List.of(rule));
        when(alertRepository.replaceRule(rule)).thenReturn(true);
        when(alertRepository.deleteRule(1L)).thenReturn(true);

        alertService.bulkToggleRules(AlertDomain.CLUSTER, List.of(1L), true);
        alertService.bulkDeleteRules(AlertDomain.CLUSTER, List.of(1L));

        verify(alertStateRepository, org.mockito.Mockito.times(2)).deleteByRuleId(1L);
        verify(alertRepository, never()).findAllRules();
    }

    @Test
    void bulkToggleShouldReportRulesDeletedConcurrentlyInsteadOfRecreatingThemTest() {
        AlertRuleVO rule = AlertRuleVO.builder().id(1L).name("High CPU").enabled(false).build();
        when(alertRepository.findRulesByIds(List.of(1L))).thenReturn(List.of(rule));
        when(alertRepository.replaceRule(any(AlertRuleVO.class))).thenReturn(false);

        AlertRuleBulkResultVO result = alertService.bulkToggleRules(List.of(1L), true);

        assertThat(result.getSucceededIds()).isEmpty();
        assertThat(result.getFailures()).containsEntry(1L, "Alert rule not found");
        assertThat(result.getUpdatedRules()).isEmpty();
        verify(alertRepository, never()).insertRule(any(AlertRuleVO.class));
    }

    @Test
    void bulkDeleteShouldPreservePartialFailureDetailsTest() {
        when(alertRepository.deleteRule(1L)).thenReturn(true);
        when(alertRepository.deleteRule(999L)).thenReturn(false);
        when(alertRepository.deleteRule(2L))
                .thenThrow(new IllegalStateException("database unavailable"));

        AlertRuleBulkResultVO result = alertService.bulkDeleteRules(
                List.of(1L, 999L, 2L));

        assertThat(result.getSucceededIds()).containsExactly(1L);
        assertThat(result.getFailures())
                .containsEntry(999L, "Alert rule not found")
                .containsEntry(2L, "database unavailable");
        assertThat(result.getUpdatedRules()).isEmpty();
        verify(alertStateRepository).deleteByRuleId(1L);
    }

    @Test
    void listAlertsShouldReturnAlertsForLevelTest() {
        SystemAlertVO alert1 = SystemAlertVO.builder().id(1L).level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();
        SystemAlertVO alert2 = SystemAlertVO.builder().id(2L).level(AlertLevel.error)
                .title("High Latency").acknowledged(false).build();
        when(alertRepository.findAlerts("error")).thenReturn(Arrays.asList(alert1, alert2));

        List<SystemAlertVO> result = alertService.listAlerts("error");

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getLevel()).isEqualTo(AlertLevel.error);
        assertThat(result.get(0).getTitle()).isEqualTo("Broker Down");
        verify(alertRepository).findAlerts("error");
    }

    @Test
    void listAlertsShouldReturnAllAlertsWhenLevelIsNullTest() {
        SystemAlertVO alert = SystemAlertVO.builder().id(1L).level(AlertLevel.warning)
                .title("Slow Consumer").build();
        when(alertRepository.findAlerts(null)).thenReturn(List.of(alert));

        List<SystemAlertVO> result = alertService.listAlerts(null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getLevel()).isEqualTo(AlertLevel.warning);
        verify(alertRepository).findAlerts(null);
    }

    @Test
    void listAlertsPageShouldReturnTheRequestedPage() {
        SystemAlertVO alert = SystemAlertVO.builder().id(7L).level(AlertLevel.warning)
                .title("Slow Consumer").build();
        when(alertRepository.findAlerts("warning", 2, 20))
                .thenReturn(PageResult.of(List.of(alert), 21, 2, 20));

        PageResult<SystemAlertVO> result = alertService.listAlerts(" warning ", 2, 20);

        assertThat(result.getItems()).containsExactly(alert);
        assertThat(result.getTotal()).isEqualTo(21);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(20);
        verify(alertRepository).findAlerts("warning", 2, 20);
    }

    @Test
    void listAlertsPageShouldRejectInvalidPaginationBeforeRepositoryAccess() {
        assertThatThrownBy(() -> alertService.listAlerts(null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("page must be greater than 0");
        assertThatThrownBy(() -> alertService.listAlerts(null, 1, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be between 1 and 100");

        verify(alertRepository, never()).findAlerts(any(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void relatedAlertsShouldReturnOtherDomainFiringEventsInTheSameInstanceAndScopeTest() {
        LocalDateTime eventTime = LocalDateTime.of(2026, 8, 23, 12, 0);
        SystemAlertVO source = SystemAlertVO.builder().id(1L).domain(AlertDomain.BUSINESS)
                .instanceId("local").time(eventTime).labels(Map.of("brokerName", "broker-a")).build();
        SystemAlertVO related = SystemAlertVO.builder().id(2L).domain(AlertDomain.CLUSTER)
                .instanceId("local").time(eventTime).transition("FIRING")
                .labels(Map.of("brokerName", "broker-a")).build();
        SystemAlertVO differentBroker = SystemAlertVO.builder().id(3L).domain(AlertDomain.CLUSTER)
                .instanceId("local").time(eventTime).transition("FIRING")
                .labels(Map.of("brokerName", "broker-b")).build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(source));
        when(alertRepository.findAlertsPage(any(SystemAlertQuery.class)))
                .thenReturn(PageResult.of(List.of(related, differentBroker), 2, 1, 100));

        List<SystemAlertVO> result = alertService.findRelatedAlerts(1L);

        assertThat(result).containsExactly(related);
        verify(alertRepository).findAlertsPage(argThat(query -> query.domain() == AlertDomain.CLUSTER
                && "local".equals(query.instanceId()) && "FIRING".equals(query.transition())
                && eventTime.minusMinutes(30).equals(query.from()) && eventTime.plusMinutes(30).equals(query.to())));
    }

    @Test
    void relatedAlertsShouldIncludeTheExplicitSuppressionCauseOutsideTheDisplayWindowTest() {
        SystemAlertVO source = SystemAlertVO.builder().id(1L).domain(AlertDomain.BUSINESS)
                .instanceId("local").suppressionCauseAlertId(12L)
                .labels(Map.of("brokerName", "broker-a")).build();
        SystemAlertVO cause = SystemAlertVO.builder().id(12L).domain(AlertDomain.CLUSTER)
                .instanceId("local").transition("FIRING")
                .labels(Map.of("brokerName", "broker-a")).build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(source));
        when(alertRepository.findAlertById(12L)).thenReturn(Optional.of(cause));

        assertThat(alertService.findRelatedAlerts(1L)).containsExactly(cause);
    }

    @Test
    void acknowledgeAlertShouldSetAcknowledgedTrue() {
        SystemAlertVO existing = SystemAlertVO.builder().id(1L).level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();
        when(alertRepository.findAlertById(1L)).thenReturn(java.util.Optional.of(existing));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenReturn(true);

        SystemAlertVO result = alertService.acknowledgeAlert(1L);

        assertThat(result.isAcknowledged()).isTrue();
        assertThat(result.getAcknowledgedBy()).isEqualTo("system");
        assertThat(result.getAcknowledgedAt()).isNotNull();
        verify(alertRepository).acknowledgeAlert(result);
        verify(operationAuditService).record(eq("ACKNOWLEDGE_SYSTEM_ALERT"), eq("SYSTEM_ALERT"), eq("1"),
                eq(null), eq("acknowledged=true"), eq("SUCCESS"), eq(null));
    }

    @Test
    void acknowledgeNativeAlertShouldAcknowledgeItsActiveRuleStateTest() {
        SystemAlertVO alert = SystemAlertVO.builder().id(1L).ruleId(7L).fingerprint("fingerprint")
                .time(LocalDateTime.of(2026, 8, 22, 12, 0))
                .transition("FIRING").acknowledged(false).build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(alert));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenReturn(true);

        alertService.acknowledgeAlert(1L);

        verify(alertStateRepository).acknowledge(new AlertStateKey(7L, "fingerprint"),
                LocalDateTime.of(2026, 8, 22, 12, 0).toInstant(ZoneOffset.UTC));
    }

    @Test
    void acknowledgingResolvedEventMustNotAcknowledgeANewerFiringStateTest() {
        SystemAlertVO resolved = SystemAlertVO.builder().id(1L).ruleId(7L).fingerprint("fingerprint")
                .transition("RESOLVED").acknowledged(false).build();
        when(alertRepository.findAlertById(1L)).thenReturn(Optional.of(resolved));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenReturn(true);

        alertService.acknowledgeAlert(1L);

        verify(alertStateRepository, never()).acknowledge(any(AlertStateKey.class), any());
    }

    @Test
    void acknowledgeAlertShouldRejectBlankIdBeforeLoadingAlertsTest() {
        assertThatThrownBy(() -> alertService.acknowledgeAlert(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("System alert ID is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verify(alertRepository, never()).findAlertById(any());
    }

    @Test
    void acknowledgeAlertShouldIgnorePersistedAlertsWithNullIds() {
        when(alertRepository.findAlertById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledgeAlert(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("System alert not found: 999");
    }

    @Test
    void acknowledgeAlertShouldThrowWhenAlertNotFound() {
        when(alertRepository.findAlertById(999L)).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> alertService.acknowledgeAlert(999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System alert not found: 999");
    }

    @Test
    void acknowledgeAlertShouldRejectConcurrentRemovalTest() {
        SystemAlertVO existing = SystemAlertVO.builder().id(1L).level(AlertLevel.error)
                .title("Broker Down").acknowledged(false).build();
        when(alertRepository.findAlertById(1L)).thenReturn(java.util.Optional.of(existing));
        when(alertRepository.acknowledgeAlert(any(SystemAlertVO.class))).thenReturn(false);

        assertThatThrownBy(() -> alertService.acknowledgeAlert(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("System alert not found: 1")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));

        verify(operationAuditService, never()).record(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void clearAcknowledgedShouldReturnDeletedCountTest() {
        when(alertRepository.deleteAcknowledgedAlerts()).thenReturn(3);

        int result = alertService.clearAcknowledged();

        assertThat(result).isEqualTo(3);
        verify(alertRepository).deleteAcknowledgedAlerts();
        verify(operationAuditService).record(eq("CLEAR_ACKNOWLEDGED_SYSTEM_ALERTS"), eq("SYSTEM_ALERT"),
                eq(null), eq(null), eq("deleted=3"), eq("SUCCESS"), eq(null));
    }

    @Test
    void clearAcknowledgedShouldReturnZeroWhenNoneAcknowledgedTest() {
        when(alertRepository.deleteAcknowledgedAlerts()).thenReturn(0);

        int result = alertService.clearAcknowledged();

        assertThat(result).isZero();
    }
}
