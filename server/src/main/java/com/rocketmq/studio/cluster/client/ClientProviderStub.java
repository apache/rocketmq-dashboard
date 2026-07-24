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
package com.rocketmq.studio.cluster.client;

import com.rocketmq.studio.common.domain.enums.ClientLanguage;
import com.rocketmq.studio.common.domain.enums.ClientType;
import com.rocketmq.studio.common.domain.enums.Protocol;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class ClientProviderStub implements ClientProvider {

    private final List<ClientConnectionVO> stubData = Arrays.asList(
            ClientConnectionVO.builder()
                    .clientId("producer-001")
                    .type(ClientType.Producer)
                    .groupOrTopic("order-topic")
                    .producerGroup("pg-order")
                    .protocol(Protocol.gRPC)
                    .address("192.168.1.10:56789")
                    .language(ClientLanguage.Java)
                    .version("5.1.0")
                    .connectedAt(LocalDateTime.now().minusHours(2))
                    .clusterName("production-cluster")
                    .build(),
            ClientConnectionVO.builder()
                    .clientId("consumer-001")
                    .type(ClientType.Consumer)
                    .groupOrTopic("order-consumer-group")
                    .protocol(Protocol.gRPC)
                    .address("192.168.1.11:56790")
                    .language(ClientLanguage.Java)
                    .version("5.1.0")
                    .connectedAt(LocalDateTime.now().minusHours(1))
                    .clusterName("production-cluster")
                    .build(),
            ClientConnectionVO.builder()
                    .clientId("consumer-002")
                    .type(ClientType.Consumer)
                    .groupOrTopic("payment-consumer-group")
                    .protocol(Protocol.Remoting)
                    .address("192.168.1.12:56791")
                    .language(ClientLanguage.Go)
                    .version("5.0.0")
                    .connectedAt(LocalDateTime.now().minusMinutes(30))
                    .clusterName("staging-cluster")
                    .build()
    );

    @Override
    public List<ClientConnectionVO> findConnections(String clusterId, String type) {
        log.debug("Stub: finding connections, clusterId={}, type={}", clusterId, type);
        return stubData.stream()
                .filter(c -> clusterId == null || clusterId.equals(c.getClusterName()))
                .filter(c -> type == null || type.equalsIgnoreCase(c.getType().name()))
                .collect(Collectors.toList());
    }
}
