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

import com.rocketmq.studio.cluster.nameserver.NameServerVO;
import com.rocketmq.studio.cluster.proxy.ProxyVO;

import com.rocketmq.studio.common.domain.enums.BrokerStatus;
import com.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ClusterProviderStub implements ClusterProvider {

    @Override
    public List<ClusterVO> discoverClusters() {
        return List.of(buildSampleCluster());
    }

    @Override
    public ClusterVO refreshClusterDetail(String clusterId) {
        return buildSampleCluster();
    }

    private ClusterVO buildSampleCluster() {
        return ClusterVO.builder()
                .name("rmq-cluster-01")
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
                .build();
    }
}
