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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class MetricProfileVOTest {

    @Test
    void builderDefaultsDescribeEmptyProfile() {
        MetricProfileVO vo = MetricProfileVO.builder().build();

        assertNull(vo.getId());
        assertNull(vo.getName());
        assertNull(vo.getDescription());
        assertNull(vo.getMetrics());
    }

    @Test
    void allArgsCarryProfileWithNestedMappings() {
        MetricProfileVO.MetricMappingVO mapping = MetricProfileVO.MetricMappingVO.builder()
            .semanticMetric("consumer_lag_messages")
            .name("Consumer lag")
            .unit("messages")
            .prometheusMetric("rocketmq_consumer_lag")
            .promql("max(rocketmq_consumer_lag)")
            .labels(List.of("group", "topic"))
            .build();

        MetricProfileVO vo = MetricProfileVO.builder()
            .id("rocketmq5-native")
            .name("RocketMQ 5.x Native")
            .description("native metrics")
            .metrics(List.of(mapping))
            .build();

        assertEquals("rocketmq5-native", vo.getId());
        assertEquals("RocketMQ 5.x Native", vo.getName());
        assertEquals(List.of(mapping), vo.getMetrics());
        assertEquals("consumer_lag_messages", mapping.getSemanticMetric());
        assertEquals(List.of("group", "topic"), mapping.getLabels());
    }
}
