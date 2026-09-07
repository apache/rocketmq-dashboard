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
package org.apache.rocketmq.studio.cluster.metrics.collectors;

import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.dlq.DLQGroupVO;
import org.apache.rocketmq.studio.instance.dlq.DLQProvider;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ApacheRocketMqDlqMetricsCollectorTest {

    @Test
    void collectsAvailableAndUnavailableDlqGroupSamplesTest() {
        DLQProvider provider = mock(DLQProvider.class);
        when(provider.listDLQGroups("local")).thenReturn(List.of(
                DLQGroupVO.builder().groupName("orders").messageCount(12).statsAvailable(true).build(),
                DLQGroupVO.builder().groupName("payments").statsAvailable(false).build()));

        List<MetricSample> samples = new ApacheRocketMqDlqMetricsCollector(provider).collect(apacheInstance());

        assertThat(samples).hasSize(2);
        assertThat(samples).filteredOn(sample -> "orders".equals(sample.labels().get("consumerGroup")))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.value()).isEqualTo(12D);
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
                });
        assertThat(samples).filteredOn(sample -> "payments".equals(sample.labels().get("consumerGroup")))
                .singleElement().satisfies(sample -> assertThat(sample.availability())
                        .isEqualTo(MetricAvailability.UNAVAILABLE));
    }

    @Test
    void reportsUnavailableWhenDlqListingFailsTest() {
        DLQProvider provider = mock(DLQProvider.class);
        when(provider.listDLQGroups("local")).thenThrow(new IllegalStateException("offline"));

        assertThat(new ApacheRocketMqDlqMetricsCollector(provider).collect(apacheInstance())).singleElement()
                .satisfies(sample -> {
                    assertThat(sample.value()).isNull();
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
                });
    }

    @Test
    void skipsUnsupportedVendorTest() {
        InstanceVO instance = apacheInstance();
        instance.setVendor(InstanceVendor.TENCENT);

        assertThat(new ApacheRocketMqDlqMetricsCollector(mock(DLQProvider.class)).collect(instance)).isEmpty();
    }

    @Test
    void supportsLegacyInstancesWithoutAnExplicitVendorTest() {
        DLQProvider provider = mock(DLQProvider.class);
        when(provider.listDLQGroups("local")).thenReturn(List.of());
        InstanceVO legacyInstance = apacheInstance();
        legacyInstance.setVendor(null);

        assertThat(new ApacheRocketMqDlqMetricsCollector(provider).collect(legacyInstance)).isEmpty();
        verify(provider).listDLQGroups("local");
    }

    private static InstanceVO apacheInstance() {
        return InstanceVO.builder().name("local").endpoint("localhost:9876").vendor(InstanceVendor.APACHE).build();
    }
}
