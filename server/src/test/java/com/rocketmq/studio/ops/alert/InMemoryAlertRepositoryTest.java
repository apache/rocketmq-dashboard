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

class InMemoryAlertRepositoryTest {

    private InMemoryAlertRepository repository;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAlertRepository();
    }

    @Test
    void saveRuleShouldStoreAndReturnRule() {
        AlertRuleVO rule = AlertRuleVO.builder().id("rule-1").alert("TestAlert")
                .group("broker").expr("cpu > 90").severity("critical").build();

        AlertRuleVO saved = repository.saveRule(rule);

        assertThat(saved).isNotNull();
        assertThat(saved.getId()).isEqualTo("rule-1");
        assertThat(saved.getAlert()).isEqualTo("TestAlert");
    }

    @Test
    void findAllRulesShouldReturnAllSavedRules() {
        AlertRuleVO rule1 = AlertRuleVO.builder().id("r1").alert("Alert1").build();
        AlertRuleVO rule2 = AlertRuleVO.builder().id("r2").alert("Alert2").build();
        repository.saveRule(rule1);
        repository.saveRule(rule2);

        List<AlertRuleVO> rules = repository.findAllRules();

        assertThat(rules).hasSize(2);
    }

    @Test
    void findAllRulesShouldReturnEmptyListWhenNoRules() {
        List<AlertRuleVO> rules = repository.findAllRules();

        assertThat(rules).isEmpty();
    }

    @Test
    void findRuleByIdShouldReturnRuleWhenExists() {
        AlertRuleVO rule = AlertRuleVO.builder().id("rule-1").alert("TestAlert").build();
        repository.saveRule(rule);

        AlertRuleVO found = repository.findRuleById("rule-1");

        assertThat(found).isNotNull();
        assertThat(found.getId()).isEqualTo("rule-1");
        assertThat(found.getAlert()).isEqualTo("TestAlert");
    }

    @Test
    void findRuleByIdShouldReturnNullWhenNotExists() {
        AlertRuleVO found = repository.findRuleById("non-existent");

        assertThat(found).isNull();
    }

    @Test
    void deleteRuleShouldRemoveRule() {
        AlertRuleVO rule = AlertRuleVO.builder().id("rule-1").alert("TestAlert").build();
        repository.saveRule(rule);

        repository.deleteRule("rule-1");

        assertThat(repository.findRuleById("rule-1")).isNull();
        assertThat(repository.findAllRules()).isEmpty();
    }

    @Test
    void deleteRuleShouldNotThrowWhenRuleNotFound() {
        repository.deleteRule("non-existent");
        // No exception expected
    }

    @Test
    void saveRuleShouldUpdateExistingRule() {
        AlertRuleVO rule = AlertRuleVO.builder().id("rule-1").alert("Original").build();
        repository.saveRule(rule);

        AlertRuleVO updated = AlertRuleVO.builder().id("rule-1").alert("Updated").build();
        repository.saveRule(updated);

        AlertRuleVO found = repository.findRuleById("rule-1");
        assertThat(found.getAlert()).isEqualTo("Updated");
        assertThat(repository.findAllRules()).hasSize(1);
    }

    @Test
    void findAllRulesShouldReturnDefensiveCopy() {
        AlertRuleVO rule = AlertRuleVO.builder().id("rule-1").alert("TestAlert").build();
        repository.saveRule(rule);

        List<AlertRuleVO> rules = repository.findAllRules();
        rules.clear();

        assertThat(repository.findAllRules()).hasSize(1);
    }
}