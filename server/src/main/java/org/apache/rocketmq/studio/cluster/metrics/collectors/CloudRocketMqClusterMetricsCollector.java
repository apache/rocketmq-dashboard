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
import org.apache.rocketmq.studio.cluster.metrics.ClusterMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.apache.rocketmq.studio.provider.CloudInstanceDetailVO;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/** Collects the cloud provider's managed-instance lifecycle status for Aliyun and Tencent. */
@Slf4j
@Component
@RequiredArgsConstructor
public class CloudRocketMqClusterMetricsCollector implements ClusterMetricsCollector {
    static final String CLOUD_INSTANCE_AVAILABILITY = "cloud.instance.availability";

    private final InstanceProviderRegistry providerRegistry;

    @Override
    public boolean supports(InstanceVO instance) {
        return instance != null && (instance.getVendor() == InstanceVendor.ALIYUN
                || instance.getVendor() == InstanceVendor.TENCENT) && StringUtils.hasText(instance.getName())
                && instance.getCredentialId() != null && StringUtils.hasText(instance.getRegionId())
                && StringUtils.hasText(instance.getCloudInstanceId());
    }

    @Override
    public Set<String> metricKeys() {
        return Set.of(CLOUD_INSTANCE_AVAILABILITY);
    }

    @Override
    public java.util.List<MetricSample> collect(InstanceVO instance) {
        if (!supports(instance)) {
            return java.util.List.of();
        }
        Instant collectedAt = Instant.now();
        Map<String, String> labels = Map.of("cloudInstanceId", instance.getCloudInstanceId());
        try {
            CloudInstanceDetailVO detail = providerRegistry.catalogFor(instance.getVendor()).getCloudInstance(
                    instance.getCredentialId(), instance.getRegionId(), instance.getCloudInstanceId());
            String status = detail == null ? null : detail.getStatus();
            Map<String, String> statusLabels = StringUtils.hasText(status)
                    ? Map.of("cloudInstanceId", instance.getCloudInstanceId(), "cloudStatus", status)
                    : labels;
            return java.util.List.of("RUNNING".equalsIgnoreCase(status)
                    ? available(instance, statusLabels, collectedAt)
                    : unavailable(instance, statusLabels, collectedAt));
        } catch (RuntimeException error) {
            log.warn("Failed to collect cloud instance status for {}: {}", instance.getName(), error.getMessage());
            return java.util.List.of(unavailable(instance, labels, collectedAt));
        }
    }

    private static MetricSample available(InstanceVO instance, Map<String, String> labels, Instant collectedAt) {
        return new MetricSample(CLOUD_INSTANCE_AVAILABILITY, AlertDomain.CLUSTER, instance.getName(), null, labels,
                1D, MetricAvailability.AVAILABLE, collectedAt);
    }

    private static MetricSample unavailable(InstanceVO instance, Map<String, String> labels, Instant collectedAt) {
        return new MetricSample(CLOUD_INSTANCE_AVAILABILITY, AlertDomain.CLUSTER, instance.getName(), null, labels,
                null, MetricAvailability.UNAVAILABLE, collectedAt);
    }
}
