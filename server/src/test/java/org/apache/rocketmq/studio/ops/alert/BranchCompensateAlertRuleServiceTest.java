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

import org.apache.rocketmq.studio.common.exception.BusinessException;
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
class BranchCompensateAlertRuleServiceTest {

    @Mock
    private BranchCompensateAlertRuleRepository branchCompensateAlertRuleRepository;

    @InjectMocks
    private BranchCompensateAlertRuleService branchCompensateAlertRuleService;

    @Test
    void listRulesShouldReturnAllRules() {
        BranchCompensateAlertRuleVO rule1 = BranchCompensateAlertRuleVO.builder()
                .id("1").name("Broker-A Lag Alert").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(1024).lagThresholdUnit("MB")
                .duration("5m").severity("critical").enabled(true).build();
        BranchCompensateAlertRuleVO rule2 = BranchCompensateAlertRuleVO.builder()
                .id("2").name("Broker-B Lag Alert").brokerName("broker-b")
                .clusterName("DefaultCluster").lagThreshold(512).lagThresholdUnit("MB")
                .duration("10m").severity("warning").enabled(false).build();
        when(branchCompensateAlertRuleRepository.findAllRules()).thenReturn(Arrays.asList(rule1, rule2));

        List<BranchCompensateAlertRuleVO> result = branchCompensateAlertRuleService.listRules();

        assertThat(result).hasSize(2);
        assertThat(result.get(0).getName()).isEqualTo("Broker-A Lag Alert");
        assertThat(result.get(0).isEnabled()).isTrue();
        assertThat(result.get(0).getLagThreshold()).isEqualTo(1024);
        assertThat(result.get(1).getName()).isEqualTo("Broker-B Lag Alert");
        assertThat(result.get(1).isEnabled()).isFalse();
    }

    @Test
    void listRulesShouldReturnEmptyListWhenNoRules() {
        when(branchCompensateAlertRuleRepository.findAllRules()).thenReturn(Collections.emptyList());

        List<BranchCompensateAlertRuleVO> result = branchCompensateAlertRuleService.listRules();

        assertThat(result).isEmpty();
    }

    @Test
    void createRuleShouldAssignIdAndTimestamps() {
        BranchCompensateAlertRuleVO input = BranchCompensateAlertRuleVO.builder()
                .name("New Rule").brokerName("broker-c")
                .clusterName("TestCluster").lagThreshold(2048)
                .lagThresholdUnit("GB").duration("15m").severity("info").build();
        when(branchCompensateAlertRuleRepository.saveRule(any(BranchCompensateAlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BranchCompensateAlertRuleVO result = branchCompensateAlertRuleService.createRule(input);

        assertThat(result.getId()).isNotNull().isNotEmpty();
        assertThat(result.getName()).isEqualTo("New Rule");
        assertThat(result.getBrokerName()).isEqualTo("broker-c");
        assertThat(result.getCreatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getCreatedAt()).isEqualTo(result.getUpdatedAt());
        verify(branchCompensateAlertRuleRepository).saveRule(result);
    }

    @Test
    void createRuleShouldGenerateUniqueIds() {
        BranchCompensateAlertRuleVO input1 = BranchCompensateAlertRuleVO.builder().name("Rule 1").build();
        BranchCompensateAlertRuleVO input2 = BranchCompensateAlertRuleVO.builder().name("Rule 2").build();
        when(branchCompensateAlertRuleRepository.saveRule(any(BranchCompensateAlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BranchCompensateAlertRuleVO result1 = branchCompensateAlertRuleService.createRule(input1);
        BranchCompensateAlertRuleVO result2 = branchCompensateAlertRuleService.createRule(input2);

        assertThat(result1.getId()).isNotEqualTo(result2.getId());
    }

    @Test
    void updateRuleShouldPreserveCreatedAtAndUpdateTimestamp() {
        BranchCompensateAlertRuleVO existing = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Old Name").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(512)
                .lagThresholdUnit("MB").duration("5m").severity("warning")
                .enabled(true).createdAt("2024-01-01 00:00:00").updatedAt("2024-01-01 00:00:00").build();
        BranchCompensateAlertRuleVO updateInput = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Updated Name").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(1024)
                .lagThresholdUnit("MB").duration("10m").severity("critical").build();
        when(branchCompensateAlertRuleRepository.findRuleById("rule-1")).thenReturn(existing);
        when(branchCompensateAlertRuleRepository.saveRule(any(BranchCompensateAlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BranchCompensateAlertRuleVO result = branchCompensateAlertRuleService.updateRule(updateInput);

        assertThat(result.getName()).isEqualTo("Updated Name");
        assertThat(result.getLagThreshold()).isEqualTo(1024);
        assertThat(result.getCreatedAt()).isEqualTo("2024-01-01 00:00:00");
        assertThat(result.getUpdatedAt()).isNotNull();
        assertThat(result.getUpdatedAt()).isNotEqualTo("2024-01-01 00:00:00");
        verify(branchCompensateAlertRuleRepository).saveRule(result);
    }

    @Test
    void updateRuleShouldThrowWhenRuleNotFound() {
        BranchCompensateAlertRuleVO updateInput = BranchCompensateAlertRuleVO.builder()
                .id("non-existent").name("Updated").build();
        when(branchCompensateAlertRuleRepository.findRuleById("non-existent")).thenReturn(null);

        assertThatThrownBy(() -> branchCompensateAlertRuleService.updateRule(updateInput))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Branch compensate alert rule not found: non-existent");
    }

    @Test
    void toggleRuleShouldEnableRule() {
        BranchCompensateAlertRuleVO existing = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Lag Alert").enabled(false).build();
        when(branchCompensateAlertRuleRepository.findRuleById("rule-1")).thenReturn(existing);
        when(branchCompensateAlertRuleRepository.saveRule(any(BranchCompensateAlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BranchCompensateAlertRuleVO result = branchCompensateAlertRuleService.toggleRule("rule-1", true);

        assertThat(result.isEnabled()).isTrue();
        assertThat(result.getUpdatedAt()).isNotNull();
        verify(branchCompensateAlertRuleRepository).saveRule(result);
    }

    @Test
    void toggleRuleShouldDisableRule() {
        BranchCompensateAlertRuleVO existing = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Lag Alert").enabled(true).build();
        when(branchCompensateAlertRuleRepository.findRuleById("rule-1")).thenReturn(existing);
        when(branchCompensateAlertRuleRepository.saveRule(any(BranchCompensateAlertRuleVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        BranchCompensateAlertRuleVO result = branchCompensateAlertRuleService.toggleRule("rule-1", false);

        assertThat(result.isEnabled()).isFalse();
    }

    @Test
    void toggleRuleShouldThrowWhenRuleNotFound() {
        when(branchCompensateAlertRuleRepository.findRuleById("non-existent")).thenReturn(null);

        assertThatThrownBy(() -> branchCompensateAlertRuleService.toggleRule("non-existent", true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Branch compensate alert rule not found: non-existent");
    }

    @Test
    void deleteRuleShouldCallRepository() {
        doNothing().when(branchCompensateAlertRuleRepository).deleteRule("rule-1");

        branchCompensateAlertRuleService.deleteRule("rule-1");

        verify(branchCompensateAlertRuleRepository).deleteRule("rule-1");
    }
}