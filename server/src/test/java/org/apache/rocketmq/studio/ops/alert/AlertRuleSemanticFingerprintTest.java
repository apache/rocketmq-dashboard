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

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AlertRuleSemanticFingerprintTest {

    @Test
    void runtimeDurationEquivalenceKeepsPersistedIdentityFormatTest() {
        AlertRuleVO seconds = AlertRuleVO.builder().duration("60s").build();
        AlertRuleVO minutes = AlertRuleVO.builder().duration("1m").build();

        assertThat(AlertRuleSemanticFingerprint.evaluationIdentity(seconds))
                .isEqualTo(AlertRuleSemanticFingerprint.evaluationIdentity(minutes));
        assertThat(AlertRuleSemanticFingerprint.of(seconds))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(minutes));
    }

    @Test
    void runtimeIdentityRetainsMalformedLegacyDurationsTest() {
        AlertRuleVO first = AlertRuleVO.builder().duration(" invalid ").build();
        AlertRuleVO same = AlertRuleVO.builder().duration("INVALID").build();
        AlertRuleVO different = AlertRuleVO.builder().duration("other-invalid").build();

        assertThat(AlertRuleSemanticFingerprint.evaluationIdentity(first))
                .isEqualTo(AlertRuleSemanticFingerprint.evaluationIdentity(same))
                .isNotEqualTo(AlertRuleSemanticFingerprint.evaluationIdentity(different));
    }

    @Test
    void ignoresPresentationAndDeliverySettingsTest() {
        AlertRuleVO first = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER).name("Disk warning").instanceId("local")
                .metric("broker.disk.usage_ratio").operator(">=").threshold(85)
                .duration("5m").aggregation("LAST").channels(List.of("email"))
                .severity("warning").enabled(true).reminderInterval("30m").build();
        AlertRuleVO second = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER).name("Disk critical").instanceId(" local ")
                .metric(" broker.disk.usage_ratio ").operator(" >= ").threshold(85.0)
                .duration("5M").channels(List.of("dingtalk", "sms"))
                .severity("critical").enabled(false).reminderInterval("1h").build();

        assertThat(AlertRuleSemanticFingerprint.of(second))
                .isEqualTo(AlertRuleSemanticFingerprint.of(first));
    }

    @Test
    void changesWhenTheEvaluationScopeOrWindowChangesTest() {
        AlertRuleVO base = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).instanceId("local").metric("consumer.lag.total")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a").build();
        AlertRuleVO differentScope = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).instanceId("local").metric("consumer.lag.total")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-b").build();
        AlertRuleVO differentWindow = AlertRuleVO.builder()
                .domain(AlertDomain.BUSINESS).instanceId("local").metric("consumer.lag.total")
                .operator(">=").threshold(1000).duration("5m").consumerGroup("group-a")
                .windowSeconds(60).build();

        assertThat(AlertRuleSemanticFingerprint.of(differentScope))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(base));
        assertThat(AlertRuleSemanticFingerprint.of(differentWindow))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(base));
    }

    @Test
    void ratioThresholdsShouldUseTheirNormalizedValueForSemanticIdentityTest() {
        AlertRuleVO percentage = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(85)
                .thresholdUnit("%")
                .build();
        AlertRuleVO fraction = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(0.85)
                .thresholdUnit(null)
                .build();
        AlertRuleVO rawValue = AlertRuleVO.builder()
                .domain(AlertDomain.CLUSTER)
                .metric("broker.disk.usage_ratio")
                .operator(">=")
                .threshold(85)
                .thresholdUnit(null)
                .build();

        assertThat(AlertRuleSemanticFingerprint.of(percentage))
                .isEqualTo(AlertRuleSemanticFingerprint.of(fraction))
                .isNotEqualTo(AlertRuleSemanticFingerprint.of(rawValue));
    }
}
