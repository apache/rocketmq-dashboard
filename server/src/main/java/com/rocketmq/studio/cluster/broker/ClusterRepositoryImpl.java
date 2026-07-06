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
package com.rocketmq.studio.cluster.broker;

import com.rocketmq.studio.cluster.config.ClusterConfigVO;
import com.rocketmq.studio.cluster.nameserver.NameServerVO;
import com.rocketmq.studio.cluster.proxy.ProxyVO;

import com.rocketmq.studio.common.domain.enums.BrokerStatus;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import com.rocketmq.studio.common.domain.enums.ClusterType;
import com.rocketmq.studio.common.domain.enums.FlushDiskType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Repository
public class ClusterRepositoryImpl implements ClusterRepository {

    private final Map<String, ClusterVO> store = new ConcurrentHashMap<>();

    public ClusterRepositoryImpl() {
        initStubData();
    }

    @Override
    public List<ClusterVO> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public Optional<ClusterVO> findById(String id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public void updateConfig(String clusterId, ClusterConfigVO config) {
        ClusterVO cluster = store.get(clusterId);
        if (cluster != null) {
            cluster.setConfig(config);
            cluster.setUpdatedAt(LocalDateTime.now());
            log.info("Config updated for cluster: {}", clusterId);
        }
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
