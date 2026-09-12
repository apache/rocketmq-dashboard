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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.domain.enums.ClientType;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.common.domain.enums.Protocol;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDateTime;
import java.util.List;

class ToolOutputSchemaContractTest {

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolSchemaValidator validator = new ToolSchemaValidator(
            catalog,
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules(),
            new JsonMapper());

    @Test
    void validatesTypedListOutputsAgainstCatalogSchemas() {
        ClientConnectionVO client = ClientConnectionVO.builder()
                .clientId("client-1")
                .type(ClientType.Producer)
                .groupOrTopic("orders")
                .producerGroup("orders-producer")
                .protocol(Protocol.Remoting)
                .address("127.0.0.1:12000")
                .language(ClientLanguage.Java)
                .version("5.0.0")
                .connectedAt(LocalDateTime.of(2026, 9, 7, 1, 0))
                .partial(false)
                .clusterName("cluster-a")
                .build();
        BrokerVO broker = BrokerVO.builder()
                .name("broker-a")
                .addr("127.0.0.1:10911")
                .version("5.0.0")
                .status(BrokerStatus.running)
                .diskUsage(0.25)
                .tpsIn(10)
                .tpsOut(9)
                .runtimeStatsAvailable(true)
                .build();
        ProxyVO proxy = ProxyVO.builder()
                .addr("127.0.0.1:8081")
                .status(ClusterStatus.healthy)
                .connections(3)
                .grpcPort(8081)
                .remotingPort(8080)
                .build();

        validator.validateOutput(
                catalog.getDefinition("rmq.client.list"),
                new ClusterListOutput<>("cluster-a", List.of(client)));
        validator.validateOutput(
                catalog.getDefinition("rmq.broker.list"),
                new ClusterListOutput<>("cluster-a", List.of(broker)));
        validator.validateOutput(
                catalog.getDefinition("rmq.proxy.list"),
                new ClusterListOutput<>("cluster-a", List.of(proxy)));
    }

}
