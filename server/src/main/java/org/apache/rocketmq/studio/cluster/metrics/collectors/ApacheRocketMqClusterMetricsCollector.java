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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.metrics.ClusterMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Native Apache RocketMQ health collection through Studio's managed admin client. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApacheRocketMqClusterMetricsCollector implements ClusterMetricsCollector {

    static final String NAMESERVER_AVAILABILITY = "nameserver.availability";
    static final String BROKER_AVAILABILITY = "broker.availability";
    static final String BROKER_DISK_USAGE_RATIO = "broker.disk.usage_ratio";
    static final String BROKER_JVM_HEAP_USAGE_RATIO = "broker.jvm.heap.usage_ratio";
    static final String BROKER_SEND_QUEUE_USAGE_RATIO = "broker.send_queue.usage_ratio";

    private final RuntimeAdminClientResolver adminClientResolver;

    @Override
    public boolean supports(InstanceVO instance) {
        return instance != null && (instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE)
                && StringUtils.hasText(instance.getName()) && StringUtils.hasText(instance.getEndpoint());
    }

    @Override
    public List<MetricSample> collect(InstanceVO instance) {
        if (!supports(instance)) {
            return List.of();
        }
        Instant collectedAt = Instant.now();
        try {
            return adminClientResolver.execute(instance,
                    admin -> collectFromTopology(instance, admin, admin.examineBrokerClusterInfo(), collectedAt));
        } catch (RuntimeException error) {
            log.warn("Failed to collect native metrics for instance {}: {}", instance.getName(), error.getMessage());
            return List.of(unavailable(NAMESERVER_AVAILABILITY, instance, null, Map.of(), collectedAt));
        }
    }

    private List<MetricSample> collectFromTopology(InstanceVO instance, MQAdminExt admin, ClusterInfo topology,
            Instant collectedAt) {
        if (topology == null) {
            return List.of(unavailable(NAMESERVER_AVAILABILITY, instance, null, Map.of(), collectedAt));
        }
        List<MetricSample> samples = new ArrayList<>();
        samples.add(available(NAMESERVER_AVAILABILITY, instance, null, Map.of(), 1D, collectedAt));
        Map<String, BrokerData> brokers = topology.getBrokerAddrTable() == null
                ? Map.of() : topology.getBrokerAddrTable();
        for (Map.Entry<String, BrokerData> entry : brokers.entrySet()) {
            collectBroker(instance, admin, entry.getKey(), entry.getValue(), collectedAt, samples);
        }
        return samples;
    }

    private void collectBroker(InstanceVO instance, MQAdminExt admin, String brokerName, BrokerData broker,
            Instant collectedAt,
            List<MetricSample> samples) {
        String clusterId = broker == null ? null : broker.getCluster();
        String address = broker == null || broker.getBrokerAddrs() == null ? null : broker.getBrokerAddrs().get(0L);
        Map<String, String> labels = StringUtils.hasText(address)
                ? Map.of("brokerName", brokerName, "brokerAddr", address)
                : Map.of("brokerName", brokerName);
        if (!StringUtils.hasText(address)) {
            samples.add(unavailable(BROKER_AVAILABILITY, instance, clusterId, labels, collectedAt));
            return;
        }
        try {
            KVTable runtime = admin.fetchBrokerRuntimeStats(address);
            if (runtime == null || runtime.getTable() == null) {
                samples.add(unavailable(BROKER_AVAILABILITY, instance, clusterId, labels, collectedAt));
                return;
            }
            samples.add(available(BROKER_AVAILABILITY, instance, clusterId, labels, 1D, collectedAt));
            parseDiskUsage(runtime.getTable().get("commitLogDiskRatio"))
                    .ifPresent(value -> samples.add(available(BROKER_DISK_USAGE_RATIO, instance, clusterId,
                            labels, value, collectedAt)));
            parseHeapUsage(runtime.getTable().get("jvmMemoryHeapUsed"), runtime.getTable().get("jvmMemoryHeapMax"))
                    .ifPresent(value -> samples.add(available(BROKER_JVM_HEAP_USAGE_RATIO, instance, clusterId,
                            labels, value, collectedAt)));
            parseUsageRatio(runtime.getTable().get("sendThreadPoolQueueSize"),
                    runtime.getTable().get("sendThreadPoolQueueCapacity"))
                    .ifPresent(value -> samples.add(available(BROKER_SEND_QUEUE_USAGE_RATIO, instance, clusterId,
                            labels, value, collectedAt)));
        } catch (Exception error) {
            log.warn("Failed to collect runtime metrics for broker {} on instance {}: {}", brokerName,
                    instance.getName(), error.getMessage());
            samples.add(unavailable(BROKER_AVAILABILITY, instance, clusterId, labels, collectedAt));
        }
    }

    private static java.util.Optional<Double> parseDiskUsage(String raw) {
        if (!StringUtils.hasText(raw)) {
            return java.util.Optional.empty();
        }
        try {
            double value = Double.parseDouble(raw.trim());
            if (!Double.isFinite(value) || value < 0 || value > 100) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(value > 1D ? value / 100D : value);
        } catch (NumberFormatException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static java.util.Optional<Double> parseHeapUsage(String usedRaw, String maxRaw) {
        return parseUsageRatio(usedRaw, maxRaw);
    }

    private static java.util.Optional<Double> parseUsageRatio(String usedRaw, String capacityRaw) {
        try {
            double used = Double.parseDouble(usedRaw);
            double capacity = Double.parseDouble(capacityRaw);
            if (!Double.isFinite(used) || !Double.isFinite(capacity) || used < 0 || capacity <= 0
                    || used > capacity) {
                return java.util.Optional.empty();
            }
            return java.util.Optional.of(used / capacity);
        } catch (RuntimeException ignored) {
            return java.util.Optional.empty();
        }
    }

    private static MetricSample available(String key, InstanceVO instance, String clusterId,
            Map<String, String> labels, double value, Instant collectedAt) {
        return new MetricSample(key, AlertDomain.CLUSTER, instance.getName(), clusterId, labels, value,
                MetricAvailability.AVAILABLE, collectedAt);
    }

    private static MetricSample unavailable(String key, InstanceVO instance, String clusterId,
            Map<String, String> labels, Instant collectedAt) {
        return new MetricSample(key, AlertDomain.CLUSTER, instance.getName(), clusterId, labels, null,
                MetricAvailability.UNAVAILABLE, collectedAt);
    }
}
