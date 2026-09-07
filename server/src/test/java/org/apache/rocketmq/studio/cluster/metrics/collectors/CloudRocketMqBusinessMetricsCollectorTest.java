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
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudRocketMqBusinessMetricsCollectorTest {
    @Test
    void collectsTotalAndMaximumLagFromACloudProviderTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        InstanceProvider provider = mock(InstanceProvider.class);
        InstanceVO instance = InstanceVO.builder().name("aliyun").vendor(InstanceVendor.ALIYUN).build();
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setClusterId("cloud-a");
        when(registry.byInstanceId("aliyun")).thenReturn(Optional.of(provider));
        when(provider.listConsumerGroups("aliyun", null)).thenReturn(List.of(group));
        when(provider.getGroupProgress("aliyun", "orders")).thenReturn(List.of(
                QueueProgressVO.builder().topic("orders-topic").diffTotal(12).build(), QueueProgressVO.builder()
                .topic("orders-topic").diffTotal(30).build()));

        List<MetricSample> samples = new CloudRocketMqBusinessMetricsCollector(registry).collect(instance);

        assertThat(samples).hasSize(3).allSatisfy(sample -> assertThat(sample.availability())
                .isEqualTo(MetricAvailability.AVAILABLE));
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.total"))
                .singleElement().extracting(MetricSample::value).isEqualTo(42D);
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("consumer.lag.max_queue"))
                .singleElement().extracting(MetricSample::value).isEqualTo(30D);
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("topic.backlog.total"))
                .singleElement().extracting(MetricSample::value).isEqualTo(42D);
    }

    @Test
    void skipsApacheInstancesHandledByTheApacheCollectorTest() {
        InstanceVO instance = InstanceVO.builder().name("local").vendor(InstanceVendor.APACHE).build();

        assertThat(new CloudRocketMqBusinessMetricsCollector(mock(InstanceProviderRegistry.class)).collect(instance))
                .isEmpty();
    }
}
