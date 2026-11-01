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

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
@ActiveProfiles("dev")
class AlertBulkMutationTransactionTest {
    @Autowired
    private AlertService service;
    @Autowired
    private AlertRepository rules;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private AlertStateRepository states;

    @ParameterizedTest
    @CsvSource({"false,false", "false,true", "true,false", "true,true"})
    void failedItemRollsBackWhileOtherItemsCommitTest(boolean scoped, boolean delete) {
        Long failedId = insertRuleTest();
        Long healthyId = insertRuleTest();
        try {
            doAnswer(invocation -> {
                invocation.callRealMethod();
                throw new IllegalStateException("state cleanup failed");
            }).when(states).deleteByRuleId(failedId);
            List<Long> ids = List.of(failedId, healthyId);
            AlertRuleBulkResultVO result;
            if (delete) {
                result = scoped ? service.bulkDeleteRules(AlertDomain.BUSINESS, ids)
                        : service.bulkDeleteRules(ids);
            } else {
                result = scoped ? service.bulkToggleRules(AlertDomain.BUSINESS, ids, false)
                        : service.bulkToggleRules(ids, false);
            }
            assertThat(result.getSucceededIds()).containsExactly(healthyId);
            assertThat(result.getFailures()).containsOnlyKeys(failedId);
            assertThat(result.getUpdatedRules()).extracting(AlertRuleVO::getId)
                    .containsExactlyElementsOf(delete ? List.of() : List.of(healthyId));
            assertThat(rules.findRuleById(failedId)).isPresent();
            assertThat(rules.findRuleById(failedId).orElseThrow().isEnabled()).isTrue();
            assertThat(stateCountTest(failedId)).isEqualTo(1);
            if (delete) {
                assertThat(rules.findRuleById(healthyId)).isEmpty();
            } else {
                assertThat(rules.findRuleById(healthyId).orElseThrow().isEnabled()).isFalse();
            }
            assertThat(stateCountTest(healthyId)).isZero();
        } finally {
            for (Long id : List.of(failedId, healthyId)) {
                jdbc.update("DELETE FROM rmq_alert_state WHERE rule_id = ?", id);
                rules.deleteRule(id);
            }
        }
    }

    private Long insertRuleTest() {
        AlertRuleVO rule = rules.insertRule(AlertRuleVO.builder().name("bulk-tx-" + UUID.randomUUID())
                .domain(AlertDomain.BUSINESS).metric("consumer.lag.total").operator(">")
                .threshold(10).enabled(true).instanceId("local").consumerGroup("orders-" + UUID.randomUUID()).build());
        jdbc.update("INSERT INTO rmq_alert_state (rule_id, fingerprint, status, consecutive_hits) "
                + "VALUES (?, ?, 'FIRING', 1)", rule.getId(), "bulk-tx-state");
        return rule.getId();
    }

    private int stateCountTest(Long id) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM rmq_alert_state WHERE rule_id = ?", Integer.class, id);
    }
}
