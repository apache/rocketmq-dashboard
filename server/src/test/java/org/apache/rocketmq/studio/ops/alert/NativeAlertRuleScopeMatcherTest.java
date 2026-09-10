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

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class NativeAlertRuleScopeMatcherTest {
    @Test
    void matchesExactBrokerAndClusterSelectorsTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").brokerName("broker-a")
                .clusterName("cluster-a").build();

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, sample("cluster-a", "broker-a"))).isTrue();
        assertThat(NativeAlertRuleScopeMatcher.matches(rule, sample("cluster-a", "broker-b"))).isFalse();
        assertThat(NativeAlertRuleScopeMatcher.matches(rule, sample("cluster-b", "broker-a"))).isFalse();
    }

    @Test
    void wildcardSelectorsMatchAllAvailableResourcesTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").brokerName("*").clusterName("*").build();

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, sample("cluster-a", "broker-a"))).isTrue();
    }

    @Test
    void matchesTopicAndConsumerGroupSelectorsTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").consumerGroup("orders")
                .topic("orders-topic").build();
        MetricSample matching = new MetricSample("topic.backlog.total", AlertDomain.BUSINESS, "local", "cluster-a",
                Map.of("consumerGroup", "orders", "topic", "orders-topic"), 42D,
                MetricAvailability.AVAILABLE, Instant.now());
        MetricSample otherTopic = new MetricSample("topic.backlog.total", AlertDomain.BUSINESS, "local", "cluster-a",
                Map.of("consumerGroup", "orders", "topic", "payments-topic"), 42D,
                MetricAvailability.AVAILABLE, Instant.now());

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, matching)).isTrue();
        assertThat(NativeAlertRuleScopeMatcher.matches(rule, otherTopic)).isFalse();
    }

    private static MetricSample sample(String clusterId, String brokerName) {
        return new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", clusterId,
                Map.of("brokerName", brokerName), 1D, MetricAvailability.AVAILABLE, Instant.now());
    }

    @Test
    void requiresAnInstanceSelectorThatMatchesTest() {
        MetricSample sample = sample("cluster-a", "broker-a");

        assertThat(NativeAlertRuleScopeMatcher.matches(AlertRuleVO.builder().build(), sample)).isFalse();
        assertThat(NativeAlertRuleScopeMatcher.matches(
                AlertRuleVO.builder().instanceId(" local ").build(), sample)).isTrue();
        assertThat(NativeAlertRuleScopeMatcher.matches(
                AlertRuleVO.builder().instanceId("other").build(), sample)).isFalse();
    }

    @Test
    void blankSelectorsMatchAnyValueIncludingMissingLabelsTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").brokerName("  ")
                .clusterName("").consumerGroup(" ").topic(null).build();
        MetricSample withoutBroker = new MetricSample("broker.availability", AlertDomain.CLUSTER, "local",
                null, Map.of("brokerName", "broker-a"), 1D, MetricAvailability.AVAILABLE, Instant.now());

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, withoutBroker)).isTrue();
    }

    @Test
    void trimsSelectorsBeforeComparisonTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").brokerName(" broker-a ")
                .clusterName(" cluster-a ").build();

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, sample("cluster-a", "broker-a"))).isTrue();
        assertThat(NativeAlertRuleScopeMatcher.matches(rule, sample(null, "broker-a"))).isFalse();
    }
}
