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

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.proxy.ProxyHealthProbe;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class ApacheRocketMqProxyMetricsCollectorTest {

    @Test
    void probesOnlyProxiesDiscoveredForTheSelectedInstanceTest() {
        ClusterService clusterService = mock(ClusterService.class);
        ProxyHealthProbe probe = mock(ProxyHealthProbe.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876")
                .vendor(InstanceVendor.APACHE).build();
        when(clusterService.listClusters("local")).thenReturn(List.of(ClusterVO.builder().id("cluster-a")
                .proxies(List.of(ProxyVO.builder().addr("proxy-a:8080").grpcPort(8081).build(),
                        ProxyVO.builder().addr("[2001:db8::5]:8080").grpcPort(8081).build()))
                .build()));
        when(probe.probe("proxy-a", 8081, 2_000)).thenReturn(ProxyHealthProbe.ProbeResult.reachable(3));
        when(probe.probe("2001:db8::5", 8081, 2_000)).thenReturn(ProxyHealthProbe.ProbeResult.unreachable());

        List<MetricSample> samples = new ApacheRocketMqProxyMetricsCollector(clusterService, probe).collect(instance);

        assertThat(samples).hasSize(2);
        assertThat(samples).filteredOn(sample -> sample.labels().get("proxyAddr").equals("proxy-a:8080"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.metricKey()).isEqualTo("proxy.availability");
                    assertThat(sample.clusterId()).isEqualTo("cluster-a");
                    assertThat(sample.value()).isEqualTo(1D);
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
                });
        assertThat(samples).filteredOn(sample -> sample.labels().get("proxyAddr").contains("2001:db8"))
                .singleElement().extracting(MetricSample::availability).isEqualTo(MetricAvailability.UNAVAILABLE);
        verify(clusterService).listClusters("local");
    }

    @Test
    void recordsUnavailableSampleForMalformedDiscoveredProxyTest() {
        ClusterService clusterService = mock(ClusterService.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876").build();
        when(clusterService.listClusters("local")).thenReturn(List.of(ClusterVO.builder().name("cluster-a")
                .proxies(List.of(ProxyVO.builder().addr("bad-address").grpcPort(8081).build())).build()));

        List<MetricSample> samples = new ApacheRocketMqProxyMetricsCollector(clusterService,
                mock(ProxyHealthProbe.class)).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.clusterId()).isEqualTo("cluster-a");
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
            assertThat(sample.value()).isNull();
        });
    }

    @Test
    void recordsUnavailableSampleWhenProxyDiscoveryFailsTest() {
        ClusterService clusterService = mock(ClusterService.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876").build();
        doThrow(new IllegalStateException("nameserver unavailable")).when(clusterService).listClusters("local");

        List<MetricSample> samples = new ApacheRocketMqProxyMetricsCollector(clusterService,
                mock(ProxyHealthProbe.class)).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.metricKey()).isEqualTo("proxy.availability");
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
            assertThat(sample.labels()).containsEntry("proxyAddr", "unknown");
        });
    }

    @Test
    void supportsApacheAndVendorLessInstancesWithNameAndEndpoint() {
        ApacheRocketMqProxyMetricsCollector collector = new ApacheRocketMqProxyMetricsCollector(
                mock(ClusterService.class), mock(ProxyHealthProbe.class));

        assertThat(collector.supports(InstanceVO.builder().name("local").endpoint("localhost:9876")
                .vendor(InstanceVendor.APACHE).build())).isTrue();
        assertThat(collector.supports(InstanceVO.builder().name("local").endpoint("localhost:9876").build()))
                .isTrue();
        assertThat(collector.supports(InstanceVO.builder().name("cloud").endpoint("x")
                .vendor(InstanceVendor.ALIYUN).build())).isFalse();
        assertThat(collector.supports(InstanceVO.builder().name("local")
                .vendor(InstanceVendor.APACHE).build())).isFalse();
        assertThat(collector.supports(null)).isFalse();
    }

    @Test
    void exposesTheProxyAvailabilityMetricKey() {
        assertThat(new ApacheRocketMqProxyMetricsCollector(mock(ClusterService.class),
                mock(ProxyHealthProbe.class)).metricKeys()).containsExactly("proxy.availability");
    }

    @Test
    void collectsAcrossClustersAndSkipsClustersWithoutProxiesTest() {
        ClusterService clusterService = mock(ClusterService.class);
        ProxyHealthProbe probe = mock(ProxyHealthProbe.class);
        InstanceVO instance = InstanceVO.builder().name("local").endpoint("localhost:9876")
                .vendor(InstanceVendor.APACHE).build();
        when(clusterService.listClusters("local")).thenReturn(List.of(
                ClusterVO.builder().id("empty-cluster").build(),
                ClusterVO.builder().id("cluster-a")
                        .proxies(List.of(ProxyVO.builder().addr("proxy-a:8080").grpcPort(8081).build()))
                        .build()));
        when(probe.probe("proxy-a", 8081, 2_000)).thenReturn(ProxyHealthProbe.ProbeResult.reachable(3));

        List<MetricSample> samples = new ApacheRocketMqProxyMetricsCollector(clusterService, probe)
                .collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.clusterId()).isEqualTo("cluster-a");
            assertThat(sample.value()).isEqualTo(1D);
            assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
        });
    }
}
