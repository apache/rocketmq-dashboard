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
package org.apache.rocketmq.studio.cluster.metrics;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link SemanticMetric}, the catalog of semantically-named metrics that
 * maps to the Prometheus keys/units surfaced in the metrics explorer.
 */
class SemanticMetricTest {

    @Test
    void everyEntryCarriesDistinctNonBlankKeysAndDisplayNames() {
        Set<String> keys = new HashSet<>();
        Set<String> displayNames = new HashSet<>();
        for (SemanticMetric metric : SemanticMetric.values()) {
            assertThat(metric.getKey()).isNotBlank();
            assertThat(metric.getDisplayName()).isNotBlank();
            assertThat(keys.add(metric.getKey()))
                    .as("duplicate key %s", metric.getKey()).isTrue();
            assertThat(displayNames.add(metric.getDisplayName()))
                    .as("duplicate display name %s", metric.getDisplayName()).isTrue();
        }
    }

    @Test
    void exposesKnownMetricMetadata() {
        assertThat(SemanticMetric.MESSAGE_IN_TPS.getKey()).isEqualTo("message_in_tps");
        assertThat(SemanticMetric.MESSAGE_IN_TPS.getDisplayName()).isEqualTo("Message In TPS");
        assertThat(SemanticMetric.MESSAGE_IN_TPS.getUnit()).isEqualTo("messages/s");
        assertThat(SemanticMetric.CONSUMER_LAG_MESSAGES.getUnit()).isEqualTo("messages");
        assertThat(SemanticMetric.BROKER_HEALTH.getUnit()).isEqualTo("up");
        assertThat(SemanticMetric.TOPIC_NUMBER.getUnit()).isEmpty();
    }

    @Test
    void coversBothThroughputAndHealthFamilies() {
        assertThat(SemanticMetric.values()).extracting(SemanticMetric::getKey)
                .contains("message_in_tps", "message_out_tps", "throughput_in", "throughput_out",
                        "consumer_lag_messages", "consumer_lag_latency", "topic_number",
                        "consumer_group_number", "broker_health");
    }
}
