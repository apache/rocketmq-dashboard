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
package org.apache.rocketmq.studio.ops.ai.tool.handler.proxy;

import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.cluster.proxy.ProxyVO;
import org.apache.rocketmq.studio.common.domain.enums.ClusterStatus;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class ProxyListToolHandlerTest {

    @Mock
    private ClusterProvider clusterProvider;

    @InjectMocks
    private ProxyListToolHandler handler;

    @Test
    void executeShouldListProxies() {
        assertThat(handler.name()).isEqualTo("rmq.proxy.list");
        ProxyVO proxy = ProxyVO.builder()
                .addr("127.0.0.1:8081")
                .status(ClusterStatus.healthy)
                .connections(10)
                .grpcPort(8081)
                .remotingPort(8080)
                .build();
        when(clusterProvider.discoverProxies("cluster-1")).thenReturn(List.of(proxy));

        ClusterListOutput<ProxyVO> result = handler.execute(
                new ClusterInput("cluster-1"), context("cluster-1"));

        assertThat(result.cluster()).isEqualTo("cluster-1");
        assertThat(result.items()).containsExactly(proxy);
        assertThat(result.items().getFirst().getAddr()).isEqualTo("127.0.0.1:8081");
        assertThat(result.items().getFirst().getStatus()).isEqualTo(ClusterStatus.healthy);
        assertThat(result.items().getFirst().getGrpcPort()).isEqualTo(8081);
    }
}
