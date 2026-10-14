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
import org.apache.rocketmq.studio.cluster.metrics.PrometheusProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AlertRuleStateLifecycleTest {
    private final AlertRepository rules = mock(AlertRepository.class);
    private final AlertStateRepository states = mock(AlertStateRepository.class);
    private final AtomicReference<AlertRuleVO> storedRule = new AtomicReference<>();
    private final AtomicReference<AlertRuleRuntimeVO> runtime = new AtomicReference<>();
    private AlertService service;

    @BeforeEach
    void setUpTest() {
        service = new AlertService(rules, states, new AlertRuleAssetService(),
                mock(OperationAuditService.class), new MetricProfileService(new PrometheusProperties()));
        storedRule.set(rule(true, 100, "Lag"));
        runtime.set(AlertRuleRuntimeVO.builder().ruleId(4L).fingerprint("active-episode")
                .status(AlertStateStatus.FIRING).lastNotifiedAt(LocalDateTime.of(2026, 9, 1, 12, 0))
                .nextReminderAt(LocalDateTime.of(2026, 9, 1, 12, 5)).build());
        when(rules.findRuleById(4L)).thenAnswer(invocation -> Optional.ofNullable(storedRule.get()));
        when(rules.findRulesByIds(List.of(4L))).thenAnswer(invocation -> List.of(storedRule.get()));
        when(rules.findAllRules()).thenAnswer(invocation -> storedRule.get() == null
                ? List.of() : List.of(storedRule.get()));
        when(rules.replaceRule(any())).thenAnswer(invocation -> {
            storedRule.set(invocation.getArgument(0));
            return true;
        });
        when(rules.deleteRule(4L)).thenAnswer(invocation -> {
            storedRule.set(null);
            return true;
        });
        // Keep runtime visible even for disabled rules, like the real runtime query.
        when(states.findRuntimeByRuleIds(any())).thenAnswer(invocation -> runtime.get() == null
                ? List.of() : List.of(runtime.get()));
        doAnswer(invocation -> {
            runtime.set(null);
            return null;
        }).when(states).deleteByRuleId(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"toggle", "domainToggle", "bulk", "domainBulk", "edit", "domainEdit"})
    void disablingRulePurgesFiringRuntimeAndReminderTimesTest(String path) {
        assertThat(service.listRuleRuntime(AlertDomain.BUSINESS)).singleElement()
                .extracting(AlertRuleRuntimeVO::getStatus).isEqualTo(AlertStateStatus.FIRING);
        mutate(path, false, 100, "Lag");
        assertThat(storedRule.get().isEnabled()).isFalse();
        assertThat(service.listRuleRuntime(AlertDomain.BUSINESS)).isEmpty();
        verify(states).deleteByRuleId(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"toggle", "domainToggle", "bulk", "domainBulk", "edit", "domainEdit"})
    void enablingRulePreservesExistingRuntimeTest(String path) {
        storedRule.get().setEnabled(false);
        AlertRuleRuntimeVO before = runtime.get();
        mutate(path, true, 100, "Lag");
        assertThat(service.listRuleRuntime(AlertDomain.BUSINESS)).containsExactly(before);
        verify(states, never()).deleteByRuleId(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "domainEdit"})
    void semanticChangePurgesRuntimeTest(String path) {
        mutate(path, true, 200, "Lag");
        assertThat(service.listRuleRuntime(AlertDomain.BUSINESS)).isEmpty();
        verify(states).deleteByRuleId(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"edit", "domainEdit"})
    void presentationOnlyEditPreservesRuntimeTest(String path) {
        AlertRuleRuntimeVO before = runtime.get();
        mutate(path, true, 100, "Renamed");
        assertThat(service.listRuleRuntime(AlertDomain.BUSINESS)).containsExactly(before);
        verify(states, never()).deleteByRuleId(4L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "domainDelete", "bulkDelete", "domainBulkDelete"})
    void deletingRuleStillPurgesRuntimeTest(String path) {
        switch (path) {
            case "delete" -> service.deleteRule(4L);
            case "domainDelete" -> service.deleteRule(AlertDomain.BUSINESS, 4L);
            case "bulkDelete" -> service.bulkDeleteRules(List.of(4L));
            case "domainBulkDelete" -> service.bulkDeleteRules(AlertDomain.BUSINESS, List.of(4L));
            default -> throw new IllegalArgumentException(path);
        }
        assertThat(runtime.get()).isNull();
        verify(states).deleteByRuleId(4L);
    }

    private void mutate(String path, boolean enabled, double threshold, String name) {
        switch (path) {
            case "toggle" -> service.toggleRule(4L, enabled);
            case "domainToggle" -> service.toggleRule(AlertDomain.BUSINESS, 4L, enabled);
            case "bulk" -> service.bulkToggleRules(List.of(4L), enabled);
            case "domainBulk" -> service.bulkToggleRules(AlertDomain.BUSINESS, List.of(4L), enabled);
            case "edit" -> service.updateRule(rule(enabled, threshold, name));
            case "domainEdit" -> service.updateRule(AlertDomain.BUSINESS, rule(enabled, threshold, name));
            default -> throw new IllegalArgumentException(path);
        }
    }

    private static AlertRuleVO rule(boolean enabled, double threshold, String name) {
        return AlertRuleVO.builder().id(4L).name(name).domain(AlertDomain.BUSINESS)
                .metric("consumer.lag.total").operator(">").threshold(threshold)
                .instanceId("local").enabled(enabled).build();
    }
}
