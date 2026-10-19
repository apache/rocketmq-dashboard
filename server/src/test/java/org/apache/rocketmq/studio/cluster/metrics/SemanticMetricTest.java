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

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire vocabulary of {@link SemanticMetric}: the keys are the identifiers metric
 * series are matched by, so a renamed or duplicated key silently breaks a dashboard chart
 * (unmatched series) or double-counts one (duplicate key). The display names and units are
 * what the charts render.
 */
class SemanticMetricTest {

    @Test
    void everyConstantExposesItsStableWireKey() {
        assertThat(SemanticMetric.MESSAGE_IN_TPS.getKey()).isEqualTo("message_in_tps");
        assertThat(SemanticMetric.MESSAGE_OUT_TPS.getKey()).isEqualTo("message_out_tps");
        assertThat(SemanticMetric.THROUGHPUT_IN.getKey()).isEqualTo("throughput_in");
        assertThat(SemanticMetric.THROUGHPUT_OUT.getKey()).isEqualTo("throughput_out");
        assertThat(SemanticMetric.CONSUMER_LAG_MESSAGES.getKey()).isEqualTo("consumer_lag_messages");
        assertThat(SemanticMetric.CONSUMER_LAG_LATENCY.getKey()).isEqualTo("consumer_lag_latency");
        assertThat(SemanticMetric.TOPIC_NUMBER.getKey()).isEqualTo("topic_number");
        assertThat(SemanticMetric.CONSUMER_GROUP_NUMBER.getKey()).isEqualTo("consumer_group_number");
        assertThat(SemanticMetric.BROKER_HEALTH.getKey()).isEqualTo("broker_health");
    }

    @Test
    void everyConstantExposesItsChartDisplayName() {
        assertThat(SemanticMetric.MESSAGE_IN_TPS.getDisplayName()).isEqualTo("Message In TPS");
        assertThat(SemanticMetric.MESSAGE_OUT_TPS.getDisplayName()).isEqualTo("Message Out TPS");
        assertThat(SemanticMetric.THROUGHPUT_IN.getDisplayName()).isEqualTo("Throughput In");
        assertThat(SemanticMetric.THROUGHPUT_OUT.getDisplayName()).isEqualTo("Throughput Out");
        assertThat(SemanticMetric.CONSUMER_LAG_MESSAGES.getDisplayName()).isEqualTo("Consumer Lag");
        assertThat(SemanticMetric.CONSUMER_LAG_LATENCY.getDisplayName()).isEqualTo("Consumer Lag Latency");
        assertThat(SemanticMetric.TOPIC_NUMBER.getDisplayName()).isEqualTo("Topic Count");
        assertThat(SemanticMetric.CONSUMER_GROUP_NUMBER.getDisplayName())
                .isEqualTo("Consumer Group Count");
        assertThat(SemanticMetric.BROKER_HEALTH.getDisplayName()).isEqualTo("Broker Health");
    }

    @Test
    void everyConstantExposesItsUnitOfMeasure() {
        assertThat(SemanticMetric.MESSAGE_IN_TPS.getUnit()).isEqualTo("messages/s");
        assertThat(SemanticMetric.MESSAGE_OUT_TPS.getUnit()).isEqualTo("messages/s");
        assertThat(SemanticMetric.THROUGHPUT_IN.getUnit()).isEqualTo("bytes/s");
        assertThat(SemanticMetric.THROUGHPUT_OUT.getUnit()).isEqualTo("bytes/s");
        assertThat(SemanticMetric.CONSUMER_LAG_MESSAGES.getUnit()).isEqualTo("messages");
        assertThat(SemanticMetric.CONSUMER_LAG_LATENCY.getUnit()).isEqualTo("ms");
        assertThat(SemanticMetric.TOPIC_NUMBER.getUnit()).isEmpty();
        assertThat(SemanticMetric.CONSUMER_GROUP_NUMBER.getUnit()).isEmpty();
        assertThat(SemanticMetric.BROKER_HEALTH.getUnit()).isEqualTo("up");
    }

    /**
     * The keys are the join point between scraped series and the semantic layer: they must stay
     * unique and non-blank, or two metrics collapse into one chart and a blank key matches
     * nothing.
     */
    @Test
    void wireKeysAreUniqueAndNotBlank() {
        Set<String> keys = Arrays.stream(SemanticMetric.values())
                .map(SemanticMetric::getKey)
                .collect(Collectors.toSet());
        assertThat(keys).hasSize(SemanticMetric.values().length);
        assertThat(keys).allSatisfy(key -> assertThat(key).isNotBlank());
    }
}
