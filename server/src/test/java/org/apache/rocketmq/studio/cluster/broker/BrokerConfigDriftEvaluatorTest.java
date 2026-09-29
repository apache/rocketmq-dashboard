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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO.ConfigDifferenceVO;
import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO.ConfigValueVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BrokerConfigDriftEvaluatorTest {

    private BrokerConfigDriftEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new BrokerConfigDriftEvaluator();
    }

    @Test
    void synchronizedClusterShouldProducePerfectScoreTest() {
        BrokerConfigDriftEvaluator.EvaluationResult result =
                evaluator.evaluate(Collections.emptyList(), 10);

        assertThat(result.consistencyScore()).isEqualTo(100.0);
        assertThat(result.clusterPosture()).isEqualTo(BrokerConfigDriftEvaluator.POSTURE_SYNCHRONIZED);
        assertThat(result.operationalSuggestions()).singleElement()
                .satisfies(s -> assertThat(s).contains("completely synchronized"));
    }

    @Test
    void nullDifferencesShouldYieldSynchronizedPostureTest() {
        BrokerConfigDriftEvaluator.EvaluationResult result =
                evaluator.evaluate(null, 10);

        assertThat(result.consistencyScore()).isEqualTo(100.0);
        assertThat(result.clusterPosture()).isEqualTo(BrokerConfigDriftEvaluator.POSTURE_SYNCHRONIZED);
    }

    @Test
    void flushDiskTypeDivergenceShouldTriggerCriticalPostureTest() {
        ConfigDifferenceVO diff = ConfigDifferenceVO.builder()
                .field("flushDiskType")
                .brokerProperty("flushDiskType")
                .values(List.of(
                        ConfigValueVO.builder().brokerName("b1").value("ASYNC_FLUSH").configured(true).build(),
                        ConfigValueVO.builder().brokerName("b2").value("SYNC_FLUSH").configured(true).build()
                ))
                .build();

        BrokerConfigDriftEvaluator.EvaluationResult result =
                evaluator.evaluate(new ArrayList<>(List.of(diff)), 10);

        assertThat(result.clusterPosture()).isEqualTo(BrokerConfigDriftEvaluator.POSTURE_CRITICAL_DRIFT);
        assertThat(result.consistencyScore()).isEqualTo(90.0);
        assertThat(diff.getSeverity()).isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_CRITICAL);
        assertThat(diff.getImpactDescription()).contains("durability");
        assertThat(diff.getRemediationAdvice()).contains("flushDiskType");
        assertThat(result.operationalSuggestions()).hasSize(1);
    }

    @Test
    void highAndMediumDivergenceShouldTriggerDriftDetectedPostureTest() {
        ConfigDifferenceVO diffTopic = ConfigDifferenceVO.builder()
                .field("autoCreateTopicEnable")
                .brokerProperty("autoCreateTopicEnable")
                .values(List.of(
                        ConfigValueVO.builder().brokerName("b1").value("true").configured(true).build(),
                        ConfigValueVO.builder().brokerName("b2").value("false").configured(true).build()
                ))
                .build();
        ConfigDifferenceVO diffRetention = ConfigDifferenceVO.builder()
                .field("fileReservedTime")
                .brokerProperty("fileReservedTime")
                .values(List.of(
                        ConfigValueVO.builder().brokerName("b1").value("72").configured(true).build(),
                        ConfigValueVO.builder().brokerName("b2").value("48").configured(true).build()
                ))
                .build();

        BrokerConfigDriftEvaluator.EvaluationResult result =
                evaluator.evaluate(new ArrayList<>(List.of(diffTopic, diffRetention)), 10);

        assertThat(result.clusterPosture()).isEqualTo(BrokerConfigDriftEvaluator.POSTURE_DRIFT_DETECTED);
        assertThat(result.consistencyScore()).isEqualTo(80.0);
        assertThat(diffTopic.getSeverity()).isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_HIGH);
        assertThat(diffRetention.getSeverity()).isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_MEDIUM);
        assertThat(result.operationalSuggestions()).hasSize(2);
    }

    @Test
    void classifySeverityShouldCoverAllKnownFieldsTest() {
        assertThat(evaluator.classifySeverity("flushDiskType"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_CRITICAL);
        assertThat(evaluator.classifySeverity("autoCreateTopicEnable"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_HIGH);
        assertThat(evaluator.classifySeverity("autoCreateSubscriptionGroup"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_HIGH);
        assertThat(evaluator.classifySeverity("maxMessageSize"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_HIGH);
        assertThat(evaluator.classifySeverity("brokerPermission"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_HIGH);
        assertThat(evaluator.classifySeverity("fileReservedTime"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_MEDIUM);
        assertThat(evaluator.classifySeverity("deleteWhen"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_MEDIUM);
        assertThat(evaluator.classifySeverity("unknownField"))
                .isEqualTo(BrokerConfigDriftEvaluator.SEVERITY_MEDIUM);
    }

    @Test
    void describeImpactShouldReturnInformativeTextTest() {
        assertThat(evaluator.describeImpact("flushDiskType")).contains("durability");
        assertThat(evaluator.describeImpact("maxMessageSize")).contains("MESSAGE_ILLEGAL");
        assertThat(evaluator.describeImpact("brokerPermission")).contains("rejection");
        assertThat(evaluator.describeImpact(null)).contains("Property divergence");
        assertThat(evaluator.describeImpact("other")).contains("Property divergence");
    }
}
