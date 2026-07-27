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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InMemoryBranchCompensateAlertRuleRepositoryTest {

    private InMemoryBranchCompensateAlertRuleRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryBranchCompensateAlertRuleRepository();
    }

    @Test
    void findAllRulesShouldReturnEmptyWhenNoRules() {
        List<BranchCompensateAlertRuleVO> rules = repository.findAllRules();

        assertThat(rules).isEmpty();
    }

    @Test
    void saveRuleShouldStoreAndReturnRule() {
        BranchCompensateAlertRuleVO rule = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Test Rule").brokerName("broker-a")
                .clusterName("DefaultCluster").lagThreshold(1024)
                .lagThresholdUnit("MB").duration("5m").severity("critical")
                .enabled(true).build();

        BranchCompensateAlertRuleVO saved = repository.saveRule(rule);

        assertThat(saved).isSameAs(rule);
        assertThat(repository.findAllRules()).hasSize(1);
        assertThat(repository.findAllRules().get(0).getId()).isEqualTo("rule-1");
    }

    @Test
    void saveRuleShouldUpdateExistingRule() {
        BranchCompensateAlertRuleVO rule = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Original").brokerName("broker-a")
                .lagThreshold(512).lagThresholdUnit("MB").build();
        repository.saveRule(rule);

        BranchCompensateAlertRuleVO updated = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Updated").brokerName("broker-a")
                .lagThreshold(1024).lagThresholdUnit("MB").build();
        repository.saveRule(updated);

        assertThat(repository.findAllRules()).hasSize(1);
        assertThat(repository.findRuleById("rule-1").getName()).isEqualTo("Updated");
        assertThat(repository.findRuleById("rule-1").getLagThreshold()).isEqualTo(1024);
    }

    @Test
    void findRuleByIdShouldReturnRuleWhenExists() {
        BranchCompensateAlertRuleVO rule = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Test Rule").brokerName("broker-a").build();
        repository.saveRule(rule);

        BranchCompensateAlertRuleVO found = repository.findRuleById("rule-1");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo("rule-1");
        assertThat(found.getName()).isEqualTo("Test Rule");
    }

    @Test
    void findRuleByIdShouldReturnNullWhenNotExists() {
        BranchCompensateAlertRuleVO found = repository.findRuleById("non-existent");

        assertThat(found).isNull();
    }

    @Test
    void deleteRuleShouldRemoveRule() {
        BranchCompensateAlertRuleVO rule = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Test Rule").brokerName("broker-a").build();
        repository.saveRule(rule);

        repository.deleteRule("rule-1");

        assertThat(repository.findAllRules()).isEmpty();
        assertThat(repository.findRuleById("rule-1")).isNull();
    }

    @Test
    void deleteRuleShouldNotThrowWhenRuleNotExists() {
        repository.deleteRule("non-existent");

        assertThat(repository.findAllRules()).isEmpty();
    }

    @Test
    void findAllRulesShouldReturnAllSavedRules() {
        BranchCompensateAlertRuleVO rule1 = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Rule 1").brokerName("broker-a").build();
        BranchCompensateAlertRuleVO rule2 = BranchCompensateAlertRuleVO.builder()
                .id("rule-2").name("Rule 2").brokerName("broker-b").build();
        BranchCompensateAlertRuleVO rule3 = BranchCompensateAlertRuleVO.builder()
                .id("rule-3").name("Rule 3").brokerName("broker-c").build();
        repository.saveRule(rule1);
        repository.saveRule(rule2);
        repository.saveRule(rule3);

        List<BranchCompensateAlertRuleVO> rules = repository.findAllRules();

        assertThat(rules).hasSize(3);
        assertThat(rules).extracting(BranchCompensateAlertRuleVO::getId)
                .containsExactlyInAnyOrder("rule-1", "rule-2", "rule-3");
    }

    @Test
    void saveAndDeleteShouldMaintainConsistentState() {
        BranchCompensateAlertRuleVO rule1 = BranchCompensateAlertRuleVO.builder()
                .id("rule-1").name("Rule 1").build();
        BranchCompensateAlertRuleVO rule2 = BranchCompensateAlertRuleVO.builder()
                .id("rule-2").name("Rule 2").build();
        repository.saveRule(rule1);
        repository.saveRule(rule2);

        repository.deleteRule("rule-1");

        assertThat(repository.findAllRules()).hasSize(1);
        assertThat(repository.findRuleById("rule-1")).isNull();
        assertThat(repository.findRuleById("rule-2")).isNotNull();
    }
}