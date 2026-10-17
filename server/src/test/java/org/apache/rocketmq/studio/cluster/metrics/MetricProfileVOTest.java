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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the metric profile view of {@link MetricProfileVO}: the profile identity and its metric
 * mappings round-trip, and the identifier is documented as not a database primary key - it is
 * the catalog key the UI selects profiles by.
 */
class MetricProfileVOTest {

    @Test
    void theProfileAndItsMappingsRoundTrip() {
        MetricProfileVO.MetricMappingVO mapping = MetricProfileVO.MetricMappingVO.builder().build();
        MetricProfileVO profile = MetricProfileVO.builder()
                .id("rocketmq5-native")
                .name("RocketMQ 5.x native")
                .description("native broker metrics")
                .metrics(List.of(mapping))
                .build();

        assertThat(profile.getId()).isEqualTo("rocketmq5-native");
        assertThat(profile.getName()).isEqualTo("RocketMQ 5.x native");
        assertThat(profile.getDescription()).isEqualTo("native broker metrics");
        assertThat(profile.getMetrics()).hasSize(1);
    }

    @Test
    void aFreshProfileCarriesNoMappings() {
        MetricProfileVO profile = new MetricProfileVO();
        assertThat(profile.getId()).isNull();
        assertThat(profile.getMetrics()).isNull();
    }
}
