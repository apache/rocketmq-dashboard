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
import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.broker.ClusterVO;
import org.apache.rocketmq.studio.cluster.metrics.ClusterMetricsCollector;
import org.apache.rocketmq.studio.cluster.metrics.MetricAvailability;
import org.apache.rocketmq.studio.cluster.metrics.MetricSample;
import org.apache.rocketmq.studio.cluster.proxy.ProxyHealthProbe;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.alert.AlertDomain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Collects TCP reachability for Proxies discovered from the selected Studio instance. */
@Slf4j
@Component
@RequiredArgsConstructor
public class ApacheRocketMqProxyMetricsCollector implements ClusterMetricsCollector {

    static final String PROXY_AVAILABILITY = "proxy.availability";
    private static final int PROBE_TIMEOUT_MILLIS = 2_000;

    private final ClusterService clusterService;
    private final ProxyHealthProbe healthProbe;

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
            List<MetricSample> samples = new ArrayList<>();
            for (ClusterVO cluster : clusterService.listClusters(instance.getName())) {
                if (cluster.getProxies() == null) {
                    continue;
                }
                for (ProxyVO proxy : cluster.getProxies()) {
                    samples.add(collectProxy(instance, cluster, proxy, collectedAt));
                }
            }
            return samples;
        } catch (RuntimeException error) {
            log.warn("Failed to discover proxies for instance {}: {}", instance.getName(), error.getMessage());
            return List.of(unavailable(instance, null, Map.of("proxyAddr", "unknown"), collectedAt));
        }
    }

    private MetricSample collectProxy(InstanceVO instance, ClusterVO cluster, ProxyVO proxy, Instant collectedAt) {
        String proxyAddr = proxy == null ? null : proxy.getAddr();
        String clusterId = StringUtils.hasText(cluster.getId()) ? cluster.getId() : cluster.getName();
        Map<String, String> labels = StringUtils.hasText(proxyAddr)
                ? Map.of("proxyAddr", proxyAddr) : Map.of("proxyAddr", "unknown");
        HostAndPort target = resolveGrpcTarget(proxy);
        if (target == null) {
            return unavailable(instance, clusterId, labels, collectedAt);
        }
        try {
            ProxyHealthProbe.ProbeResult result = healthProbe.probe(target.host(), target.port(), PROBE_TIMEOUT_MILLIS);
            return result.reachable()
                    ? available(instance, clusterId, labels, collectedAt)
                    : unavailable(instance, clusterId, labels, collectedAt);
        } catch (RuntimeException error) {
            log.warn("Failed to probe proxy {} for instance {}: {}", proxyAddr, instance.getName(), error.getMessage());
            return unavailable(instance, clusterId, labels, collectedAt);
        }
    }

    private static HostAndPort resolveGrpcTarget(ProxyVO proxy) {
        if (proxy == null || !StringUtils.hasText(proxy.getAddr()) || proxy.getGrpcPort() < 1
                || proxy.getGrpcPort() > 65_535) {
            return null;
        }
        String address = proxy.getAddr().trim();
        int separator = address.lastIndexOf(':');
        if (separator <= 0 || separator == address.length() - 1) {
            return null;
        }
        String host = address.substring(0, separator);
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        return StringUtils.hasText(host) ? new HostAndPort(host, proxy.getGrpcPort()) : null;
    }

    private static MetricSample available(InstanceVO instance, String clusterId, Map<String, String> labels,
            Instant collectedAt) {
        return new MetricSample(PROXY_AVAILABILITY, AlertDomain.CLUSTER, instance.getName(), clusterId, labels, 1D,
                MetricAvailability.AVAILABLE, collectedAt);
    }

    private static MetricSample unavailable(InstanceVO instance, String clusterId, Map<String, String> labels,
            Instant collectedAt) {
        return new MetricSample(PROXY_AVAILABILITY, AlertDomain.CLUSTER, instance.getName(), clusterId, labels, null,
                MetricAvailability.UNAVAILABLE, collectedAt);
    }

    private record HostAndPort(String host, int port) {
    }
}
