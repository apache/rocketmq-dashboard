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
 * Pins the wire vocabulary of {@link MetricProfile}: the ids are what instance/cluster metric
 * configuration is matched by, so a renamed id silently detaches a cluster from its profile.
 */
class MetricProfileTest {

    @Test
    void idsAreTheStableWireIdentifiers() {
        assertThat(MetricProfile.ROCKETMQ_4_EXPORTER.getId()).isEqualTo("rocketmq4-exporter");
        assertThat(MetricProfile.ROCKETMQ_5_NATIVE.getId()).isEqualTo("rocketmq5-native");
    }

    @Test
    void displayNamesMatchTheGenerationTheyDescribe() {
        assertThat(MetricProfile.ROCKETMQ_4_EXPORTER.getDisplayName()).isEqualTo("RocketMQ 4.x Exporter");
        assertThat(MetricProfile.ROCKETMQ_5_NATIVE.getDisplayName()).isEqualTo("RocketMQ 5.x Native");
    }

    @Test
    void descriptionsExplainTheScrapingPath() {
        assertThat(MetricProfile.ROCKETMQ_4_EXPORTER.getDescription()).isEqualTo(
                "RocketMQ 4.x clusters scraped through the standalone rocketmq-exporter");
        assertThat(MetricProfile.ROCKETMQ_5_NATIVE.getDescription()).isEqualTo(
                "RocketMQ 5.1+ native OpenTelemetry and Prometheus metrics");
    }

    /**
     * Exactly two profiles, one per supported generation, with distinct ids: a third value would
     * change the profile matrix every consumer iterates over, and a duplicate id would make the
     * two generations indistinguishable to configuration matching.
     */
    @Test
    void exactlyTwoProfilesWithDistinctIds() {
        assertThat(MetricProfile.values()).hasSize(2);
        Set<String> ids = Arrays.stream(MetricProfile.values())
                .map(MetricProfile::getId)
                .collect(Collectors.toSet());
        assertThat(ids).hasSize(2);
    }
}
