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
package org.apache.rocketmq.studio.ops.ai.tool.handler.broker;

import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.cluster.broker.ClusterProvider;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;
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
class BrokerListToolHandlerTest {

    @Mock
    private ClusterProvider clusterProvider;

    @InjectMocks
    private BrokerListToolHandler handler;

    @Test
    void executeShouldListBrokers() {
        assertThat(handler.name()).isEqualTo("rmq.broker.list");
        BrokerVO broker = BrokerVO.builder()
                .name("broker-a")
                .addr("127.0.0.1:10911")
                .version("5.0.0")
                .status(BrokerStatus.running)
                .diskUsage(0.5)
                .tpsIn(100L)
                .tpsOut(200L)
                .runtimeStatsAvailable(true)
                .build();
        when(clusterProvider.discoverBrokers("cluster-1", null)).thenReturn(List.of(broker));

        ClusterListOutput<BrokerVO> result = handler.execute(
                new ClusterInput("cluster-1"), context("cluster-1"));

        assertThat(result.cluster()).isEqualTo("cluster-1");
        assertThat(result.items()).containsExactly(broker);
        assertThat(result.items().getFirst().getName()).isEqualTo("broker-a");
        assertThat(result.items().getFirst().getAddr()).isEqualTo("127.0.0.1:10911");
        assertThat(result.items().getFirst().getStatus()).isEqualTo(BrokerStatus.running);
    }

}
