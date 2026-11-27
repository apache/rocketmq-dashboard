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
package org.apache.rocketmq.studio.common.util;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class SystemTopicFilterTest {

    @Test
    void shouldRecognizeCanonicalSystemTopicsAndBrokerNamesTest() {
        Set<String> brokerNames = Set.of("broker-prod-a", "broker-prod-b");

        assertThat(SystemTopicFilter.isSystem(null, brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("RMQ_SYS_TRANS_HALF_TOPIC", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("rmq_sys_TRACE_DATA", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("SCHEDULE_TOPIC_XXXX", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("%RETRY%consumer-a", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("%DLQ%consumer-a", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("TBW102", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("SELF_TEST_TOPIC", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("OFFSET_MOVED_EVENT", brokerNames)).isTrue();
        assertThat(SystemTopicFilter.isSystem("broker-prod-a", brokerNames)).isTrue();

        assertThat(SystemTopicFilter.isSystem("orders", brokerNames)).isFalse();
        assertThat(SystemTopicFilter.isSystem("CID_orders", brokerNames)).isFalse();
        assertThat(SystemTopicFilter.isSystem("broker_events", brokerNames)).isFalse();
        assertThat(SystemTopicFilter.isSystem("BenchmarkTestOrders", brokerNames)).isFalse();
        assertThat(SystemTopicFilter.isSystem("SCHEDULE_TOPIC_orders", brokerNames)).isFalse();
    }

    @Test
    void shouldRecognizeTheTopicsTheBrokerGeneratesForItsOwnClusterTest() {
        Set<String> brokerNames = Set.of("broker-prod-a");
        String clusterName = "DefaultCluster";

        // Both are created by TopicConfigManager#registerSystemTopicConfig and registered as system
        // topics there, but in the broker's own JVM, so the studio has to rule them out itself.
        assertThat(SystemTopicFilter.isSystem("DefaultCluster", brokerNames, clusterName)).isTrue();
        assertThat(SystemTopicFilter.isSystem("DefaultCluster_REPLY_TOPIC", brokerNames, clusterName)).isTrue();

        // Only the owning cluster's names are matched: the reply suffix alone, or another
        // cluster's name, still describes a user topic.
        assertThat(SystemTopicFilter.isSystem("orders_REPLY_TOPIC", brokerNames, clusterName)).isFalse();
        assertThat(SystemTopicFilter.isSystem("StagingCluster", brokerNames, clusterName)).isFalse();
        assertThat(SystemTopicFilter.isSystem("StagingCluster_REPLY_TOPIC", brokerNames, clusterName)).isFalse();
        assertThat(SystemTopicFilter.isSystem("orders", brokerNames, clusterName)).isFalse();

        // The base rules keep working, and an unknown cluster adds no names.
        assertThat(SystemTopicFilter.isSystem("RMQ_SYS_TRANS_HALF_TOPIC", brokerNames, null)).isTrue();
        assertThat(SystemTopicFilter.isSystem("DefaultCluster", brokerNames, null)).isFalse();
        assertThat(SystemTopicFilter.isSystem("DefaultCluster", brokerNames, "")).isFalse();
    }
}
