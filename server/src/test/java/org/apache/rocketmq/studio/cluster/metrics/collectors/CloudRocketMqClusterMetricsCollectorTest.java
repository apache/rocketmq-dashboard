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
import org.apache.rocketmq.studio.provider.CloudCatalogProvider;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CloudRocketMqClusterMetricsCollectorTest {

    @Test
    void collectsRunningCloudInstanceAsAvailableTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        CloudCatalogProvider catalog = mock(CloudCatalogProvider.class);
        InstanceVO instance = cloudInstance(InstanceVendor.ALIYUN);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setStatus("RUNNING");
        when(registry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(7L, "cn-hangzhou", "rmq-cloud")).thenReturn(detail);

        List<MetricSample> samples = new CloudRocketMqClusterMetricsCollector(registry).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.metricKey()).isEqualTo("cloud.instance.availability");
            assertThat(sample.value()).isEqualTo(1D);
            assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
            assertThat(sample.labels()).containsEntry("cloudStatus", "RUNNING");
        });
    }

    @Test
    void recordsUnavailableForStoppedOrUnreachableCloudInstanceTest() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        CloudCatalogProvider catalog = mock(CloudCatalogProvider.class);
        InstanceVO instance = cloudInstance(InstanceVendor.TENCENT);
        CloudInstanceDetailVO detail = new CloudInstanceDetailVO();
        detail.setStatus("STOPPED");
        when(registry.catalogFor(InstanceVendor.TENCENT)).thenReturn(catalog);
        when(catalog.getCloudInstance(7L, "cn-hangzhou", "rmq-cloud")).thenReturn(detail);

        List<MetricSample> samples = new CloudRocketMqClusterMetricsCollector(registry).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.value()).isNull();
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
            assertThat(sample.labels()).containsEntry("cloudStatus", "STOPPED");
        });
    }

    private static InstanceVO cloudInstance(InstanceVendor vendor) {
        return InstanceVO.builder().name("cloud-local").vendor(vendor).credentialId(7L)
                .regionId("cn-hangzhou").cloudInstanceId("rmq-cloud").build();
    }

    @Test
    void supportsRequiresFullCloudCoordinates() {
        CloudRocketMqClusterMetricsCollector collector =
                new CloudRocketMqClusterMetricsCollector(mock(InstanceProviderRegistry.class));

        assertThat(collector.supports(cloudInstance(InstanceVendor.ALIYUN))).isTrue();
        assertThat(collector.supports(InstanceVO.builder().name("cloud-local")
                .vendor(InstanceVendor.ALIYUN).credentialId(7L).regionId("cn-hangzhou").build()))
                .isFalse();
        assertThat(collector.supports(InstanceVO.builder().name("cloud-local")
                .vendor(InstanceVendor.APACHE).credentialId(7L).regionId("cn-hangzhou")
                .cloudInstanceId("rmq-cloud").build())).isFalse();
        assertThat(collector.supports(null)).isFalse();
    }

    @Test
    void exposesItsClusterMetricKey() {
        assertThat(new CloudRocketMqClusterMetricsCollector(mock(InstanceProviderRegistry.class))
                .metricKeys()).containsExactly("cloud.instance.availability");
    }

    @Test
    void degradesToUnavailableWhenTheCatalogQueryFails() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        CloudCatalogProvider catalog = mock(CloudCatalogProvider.class);
        InstanceVO instance = cloudInstance(InstanceVendor.ALIYUN);
        when(registry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(7L, "cn-hangzhou", "rmq-cloud"))
                .thenThrow(new IllegalStateException("catalog down"));

        List<MetricSample> samples = new CloudRocketMqClusterMetricsCollector(registry).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
            assertThat(sample.value()).isNull();
            assertThat(sample.labels()).containsEntry("cloudInstanceId", "rmq-cloud");
            assertThat(sample.labels()).doesNotContainKey("cloudStatus");
        });
    }

    @Test
    void recordsUnavailableWhenTheStatusIsMissing() {
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        CloudCatalogProvider catalog = mock(CloudCatalogProvider.class);
        InstanceVO instance = cloudInstance(InstanceVendor.ALIYUN);
        when(registry.catalogFor(InstanceVendor.ALIYUN)).thenReturn(catalog);
        when(catalog.getCloudInstance(7L, "cn-hangzhou", "rmq-cloud")).thenReturn(null);

        List<MetricSample> samples = new CloudRocketMqClusterMetricsCollector(registry).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
            assertThat(sample.labels()).containsEntry("cloudInstanceId", "rmq-cloud");
            assertThat(sample.labels()).doesNotContainKey("cloudStatus");
        });
    }
}
