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

import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ApacheRocketMqClusterMetricsCollectorTest {

    @Test
    void collectsBrokerAvailabilityAndNormalizesDiskUsageTest() throws Exception {
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        MQAdminExt admin = mock(MQAdminExt.class);
        InstanceVO instance = apacheInstance();
        ClusterInfo topology = new ClusterInfo();
        topology.setBrokerAddrTable(Map.of("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "broker-a:10911")))));
        KVTable runtime = new KVTable();
        runtime.setTable(new HashMap<>(Map.of("commitLogDiskRatio", "75", "jvmMemoryHeapUsed", "768",
                "jvmMemoryHeapMax", "1024", "sendThreadPoolQueueSize", "250",
                "sendThreadPoolQueueCapacity", "1000")));
        when(admin.examineBrokerClusterInfo()).thenReturn(topology);
        when(admin.fetchBrokerRuntimeStats("broker-a:10911")).thenReturn(runtime);
        when(resolver.execute(eq(instance), any(MqAdminExtFactory.AdminAction.class)))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(admin));

        List<MetricSample> samples = new ApacheRocketMqClusterMetricsCollector(resolver).collect(instance);

        assertThat(samples).extracting(MetricSample::metricKey)
                .containsExactlyInAnyOrder("nameserver.availability", "broker.availability", "broker.disk.usage_ratio",
                        "broker.jvm.heap.usage_ratio", "broker.send_queue.usage_ratio");
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("broker.disk.usage_ratio"))
                .singleElement().satisfies(sample -> {
                    assertThat(sample.value()).isEqualTo(0.75D);
                    assertThat(sample.availability()).isEqualTo(MetricAvailability.AVAILABLE);
                    assertThat(sample.labels()).containsEntry("brokerName", "broker-a");
                });
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("broker.jvm.heap.usage_ratio"))
                .singleElement().extracting(MetricSample::value).isEqualTo(0.75D);
        assertThat(samples).filteredOn(sample -> sample.metricKey().equals("broker.send_queue.usage_ratio"))
                .singleElement().extracting(MetricSample::value).isEqualTo(0.25D);
    }

    @Test
    void recordsUnavailableNameserverWhenTopologyCollectionFailsTest() {
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        InstanceVO instance = apacheInstance();
        when(resolver.execute(eq(instance), any(MqAdminExtFactory.AdminAction.class)))
                .thenThrow(new IllegalStateException("connection refused"));

        List<MetricSample> samples = new ApacheRocketMqClusterMetricsCollector(resolver).collect(instance);

        assertThat(samples).singleElement().satisfies(sample -> {
            assertThat(sample.metricKey()).isEqualTo("nameserver.availability");
            assertThat(sample.value()).isNull();
            assertThat(sample.availability()).isEqualTo(MetricAvailability.UNAVAILABLE);
        });
    }

    @Test
    void skipsUnsupportedVendorTest() {
        InstanceVO instance = apacheInstance();
        instance.setVendor(InstanceVendor.ALIYUN);

        assertThat(new ApacheRocketMqClusterMetricsCollector(mock(RuntimeAdminClientResolver.class)).collect(instance))
                .isEmpty();
    }

    private static InstanceVO apacheInstance() {
        return InstanceVO.builder().name("local").endpoint("localhost:9876").vendor(InstanceVendor.APACHE).build();
    }
}
