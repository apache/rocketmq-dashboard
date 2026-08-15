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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.apache.rocketmq.studio.cluster.nameserver.NameServerVO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;

import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClusterType;
import org.apache.rocketmq.studio.common.domain.enums.FlushDiskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class ClusterRepositoryImpl implements ClusterRepository {

    private final Map<String, ClusterVO> store = new ConcurrentHashMap<>();

    public ClusterRepositoryImpl(@Value("${studio.cluster.seed-demo-data:false}") boolean seedDemoData) {
        if (seedDemoData) {
            initStubData();
            log.info("Initialized demo cluster data for Studio cluster repository");
        }
    }

    @Override
    public List<ClusterVO> findAll() {
        return store.values().stream()
                .map(this::defensiveCopy)
                .sorted(Comparator
                        .comparing(ClusterVO::getName, Comparator.nullsLast(String::compareToIgnoreCase))
                        .thenComparing(ClusterVO::getId, Comparator.nullsLast(String::compareTo)))
                .toList();
    }

    @Override
    public Optional<ClusterVO> findById(String id) {
        return Optional.ofNullable(store.get(id)).map(this::defensiveCopy);
    }

    @Override
    public void updateConfig(String clusterId, ClusterConfigVO config) {
        // Atomically replace the stored cluster with a fresh copy so concurrent readers never
        // observe a partially updated snapshot (new config, old updatedAt).
        store.computeIfPresent(clusterId, (id, cluster) -> {
            ClusterVO updated = defensiveCopy(cluster);
            updated.setConfig(copyConfig(config));
            updated.setUpdatedAt(LocalDateTime.now());
            log.info("Config updated for cluster: {}", clusterId);
            return updated;
        });
    }

    /**
     * Returns an independent copy so callers cannot mutate the cached cluster and concurrent
     * readers never share a partially-updated object. Nested lists are copied as immutable lists.
     */
    private ClusterVO defensiveCopy(ClusterVO cluster) {
        ClusterVO copy = ClusterVO.builder()
                .name(cluster.getName())
                .nsClusterName(cluster.getNsClusterName())
                .type(cluster.getType())
                .endpoint(cluster.getEndpoint())
                .status(cluster.getStatus())
                .version(cluster.getVersion())
                .brokers(cluster.getBrokers() == null ? null : new ArrayList<>(cluster.getBrokers().stream()
                        .map(this::copyBroker)
                        .toList()))
                .proxies(cluster.getProxies() == null ? null : new ArrayList<>(cluster.getProxies().stream()
                        .map(this::copyProxy)
                        .toList()))
                .nameServers(cluster.getNameServers() == null ? null : new ArrayList<>(cluster.getNameServers().stream()
                        .map(this::copyNameServer)
                        .toList()))
                .config(copyConfig(cluster.getConfig()))
                .topicCount(cluster.getTopicCount())
                .groupCount(cluster.getGroupCount())
                .tpsHistory(cluster.getTpsHistory() == null ? null : new ArrayList<>(cluster.getTpsHistory()))
                .build();
        copy.setId(cluster.getId());
        copy.setCreatedAt(cluster.getCreatedAt());
        copy.setUpdatedAt(cluster.getUpdatedAt());
        return copy;
    }

    private BrokerVO copyBroker(BrokerVO broker) {
        if (broker == null) {
            return null;
        }
        return BrokerVO.builder()
                .name(broker.getName())
                .addr(broker.getAddr())
                .version(broker.getVersion())
                .status(broker.getStatus())
                .diskUsage(broker.getDiskUsage())
                .tpsIn(broker.getTpsIn())
                .tpsOut(broker.getTpsOut())
                .runtimeStatsAvailable(broker.isRuntimeStatsAvailable())
                .build();
    }

    private ProxyVO copyProxy(ProxyVO proxy) {
        if (proxy == null) {
            return null;
        }
        return ProxyVO.builder()
                .addr(proxy.getAddr())
                .status(proxy.getStatus())
                .connections(proxy.getConnections())
                .grpcPort(proxy.getGrpcPort())
                .remotingPort(proxy.getRemotingPort())
                .build();
    }

    private NameServerVO copyNameServer(NameServerVO nameServer) {
        if (nameServer == null) {
            return null;
        }
        return NameServerVO.builder()
                .addr(nameServer.getAddr())
                .status(nameServer.getStatus())
                .build();
    }

    private ClusterConfigVO copyConfig(ClusterConfigVO config) {
        if (config == null) {
            return null;
        }
        return ClusterConfigVO.builder()
                .writeQueueNums(config.getWriteQueueNums())
                .readQueueNums(config.getReadQueueNums())
                .maxMessageSize(config.getMaxMessageSize())
                .msgTraceTopicName(config.getMsgTraceTopicName())
                .autoCreateTopicEnable(config.isAutoCreateTopicEnable())
                .autoCreateSubscriptionGroup(config.isAutoCreateSubscriptionGroup())
                .deleteWhen(config.getDeleteWhen())
                .fileReservedTime(config.getFileReservedTime())
                .flushDiskType(config.getFlushDiskType())
                .brokerPermission(config.getBrokerPermission())
                .build();
    }

    private void initStubData() {
        ClusterConfigVO config = ClusterConfigVO.builder()
                .writeQueueNums(16)
                .readQueueNums(16)
                .maxMessageSize(4194304)
                .msgTraceTopicName("RMQ_SYS_TRACE_TOPIC")
                .autoCreateTopicEnable(true)
                .autoCreateSubscriptionGroup(true)
                .deleteWhen("04")
                .fileReservedTime(72)
                .flushDiskType(FlushDiskType.ASYNC_FLUSH)
                .brokerPermission(6)
                .build();

        ClusterVO cluster1 = ClusterVO.builder()
                .name("rmq-cluster-prod")
                .nsClusterName("rmq-cluster-prod-ns")
                .type(ClusterType.V5_PROXY_CLUSTER)
                .endpoint("10.0.0.1:9876")
                .status(ClusterStatus.healthy)
                .version("5.2.0")
                .brokers(List.of(
                        BrokerVO.builder()
                                .name("broker-a")
                                .addr("10.0.0.1:10911")
                                .version("5.2.0")
                                .status(BrokerStatus.running)
                                .diskUsage(45.2)
                                .tpsIn(1200)
                                .tpsOut(800)
                                .build(),
                        BrokerVO.builder()
                                .name("broker-b")
                                .addr("10.0.0.2:10911")
                                .version("5.2.0")
                                .status(BrokerStatus.running)
                                .diskUsage(38.7)
                                .tpsIn(980)
                                .tpsOut(750)
                                .build()
                ))
                .proxies(List.of(
                        ProxyVO.builder()
                                .addr("10.0.0.10:8081")
                                .status(ClusterStatus.healthy)
                                .connections(156)
                                .grpcPort(8081)
                                .remotingPort(10911)
                                .build()
                ))
                .nameServers(List.of(
                        NameServerVO.builder()
                                .addr("10.0.0.20:9876")
                                .status(ClusterStatus.healthy)
                                .build(),
                        NameServerVO.builder()
                                .addr("10.0.0.21:9876")
                                .status(ClusterStatus.healthy)
                                .build()
                ))
                .config(config)
                .topicCount(128)
                .groupCount(45)
                .tpsHistory(List.of(1200, 1350, 1100, 1450, 1280, 1500, 1380, 1420, 1300, 1550))
                .build();
        cluster1.setId("cluster-001");
        cluster1.setCreatedAt(LocalDateTime.now().minusDays(30));
        cluster1.setUpdatedAt(LocalDateTime.now());

        ClusterVO cluster2 = ClusterVO.builder()
                .name("rmq-cluster-staging")
                .nsClusterName("rmq-cluster-staging-ns")
                .type(ClusterType.V5_PROXY_LOCAL)
                .endpoint("10.1.0.1:9876")
                .status(ClusterStatus.warning)
                .version("5.1.4")
                .brokers(List.of(
                        BrokerVO.builder()
                                .name("broker-staging-0")
                                .addr("10.1.0.1:10911")
                                .version("5.1.4")
                                .status(BrokerStatus.running)
                                .diskUsage(72.1)
                                .tpsIn(320)
                                .tpsOut(210)
                                .build()
                ))
                .proxies(List.of(
                        ProxyVO.builder()
                                .addr("10.1.0.10:8081")
                                .status(ClusterStatus.warning)
                                .connections(23)
                                .grpcPort(8081)
                                .remotingPort(10911)
                                .build()
                ))
                .nameServers(List.of(
                        NameServerVO.builder()
                                .addr("10.1.0.20:9876")
                                .status(ClusterStatus.healthy)
                                .build()
                ))
                .config(ClusterConfigVO.builder()
                        .writeQueueNums(8)
                        .readQueueNums(8)
                        .maxMessageSize(4194304)
                        .msgTraceTopicName("RMQ_SYS_TRACE_TOPIC")
                        .autoCreateTopicEnable(true)
                        .autoCreateSubscriptionGroup(false)
                        .deleteWhen("04")
                        .fileReservedTime(48)
                        .flushDiskType(FlushDiskType.SYNC_FLUSH)
                        .brokerPermission(6)
                        .build())
                .topicCount(32)
                .groupCount(12)
                .tpsHistory(List.of(320, 280, 350, 310, 290, 340, 300, 330, 310, 350))
                .build();
        cluster2.setId("cluster-002");
        cluster2.setCreatedAt(LocalDateTime.now().minusDays(15));
        cluster2.setUpdatedAt(LocalDateTime.now().minusHours(3));

        store.put(cluster1.getId(), cluster1);
        store.put(cluster2.getId(), cluster2);
    }
}
