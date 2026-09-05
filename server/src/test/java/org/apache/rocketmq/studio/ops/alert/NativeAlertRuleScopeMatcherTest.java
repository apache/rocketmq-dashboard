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

    @Test
    void blankInstanceIdNeverMatchesTest() {
        AlertRuleVO blank = AlertRuleVO.builder().instanceId("  ").clusterName("*").brokerName("*").build();
        AlertRuleVO missing = AlertRuleVO.builder().brokerName("*").build();

        assertThat(NativeAlertRuleScopeMatcher.matches(blank, sample("cluster-a", "broker-a"))).isFalse();
        assertThat(NativeAlertRuleScopeMatcher.matches(missing, sample("cluster-a", "broker-a"))).isFalse();
    }

    @Test
    void concreteSelectorRejectsSampleWithoutThatLabelTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").consumerGroup("orders")
                .brokerName("*").clusterName("*").build();
        MetricSample noGroupLabel = new MetricSample("topic.backlog.total", AlertDomain.BUSINESS, "local",
                "cluster-a", Map.of("topic", "orders-topic"), 42D,
                MetricAvailability.AVAILABLE, Instant.now());

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, noGroupLabel)).isFalse();
    }

    @Test
    void wildcardSelectorAcceptsSampleWithoutThatLabelTest() {
        AlertRuleVO rule = AlertRuleVO.builder().instanceId("local").consumerGroup("*")
                .brokerName("*").clusterName("*").build();
        MetricSample noGroupLabel = new MetricSample("topic.backlog.total", AlertDomain.BUSINESS, "local",
                "cluster-a", Map.of("topic", "orders-topic"), 42D,
                MetricAvailability.AVAILABLE, Instant.now());

        assertThat(NativeAlertRuleScopeMatcher.matches(rule, noGroupLabel)).isTrue();
    }

    @Test
    void selectorWhitespaceAndBlankSelectorsAreToleratedTest() {
        AlertRuleVO trimmed = AlertRuleVO.builder().instanceId("local").brokerName(" broker-a ")
                .clusterName(" cluster-a ").build();
        AlertRuleVO blankSelectors = AlertRuleVO.builder().instanceId("local").brokerName(" ")
                .clusterName(" ").build();

        assertThat(NativeAlertRuleScopeMatcher.matches(trimmed, sample("cluster-a", "broker-a"))).isTrue();
        assertThat(NativeAlertRuleScopeMatcher.matches(trimmed, sample("cluster-a", "broker-b"))).isFalse();
        assertThat(NativeAlertRuleScopeMatcher.matches(blankSelectors, sample("cluster-b", "broker-b"))).isTrue();
    }

    private static MetricSample sample(String clusterId, String brokerName) {
        return new MetricSample("broker.availability", AlertDomain.CLUSTER, "local", clusterId,
                Map.of("brokerName", brokerName), 1D, MetricAvailability.AVAILABLE, Instant.now());
    }
}
