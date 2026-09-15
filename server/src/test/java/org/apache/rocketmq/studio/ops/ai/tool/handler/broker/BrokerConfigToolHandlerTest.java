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

import org.apache.rocketmq.studio.cluster.broker.BrokerConfigDiffService;
import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.BrokerClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class BrokerConfigToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @Mock
    private BrokerConfigDiffService brokerConfigDiffService;

    @InjectMocks
    private BrokerConfigToolHandler handler;

    @Test
    void comparesUsingPhysicalClusterAndResolvedInstanceTest() {
        assertThat(handler.name()).isEqualTo("rmq.broker.config");
        when(clusterResolver.resolveInstanceId("rmq-a")).thenReturn("instance-a");
        BrokerConfigDiffVO diff = BrokerConfigDiffVO.builder()
                .cluster("rmq-a")
                .complete(true)
                .driftDetected(false)
                .brokerCount(2)
                .reachableBrokerCount(2)
                .comparedFields(List.of("maxMessageSize"))
                .brokers(List.of())
                .differences(List.of())
                .build();
        when(brokerConfigDiffService.compare("rmq-a", "instance-a")).thenReturn(diff);

        BrokerConfigOutput output = handler.execute(
                new BrokerClusterInput("rmq-a"), context("instance-a"));

        // §15.5.5 regression guard: the physical cluster name (not the instance id) keys the
        // cluster-details lookup, with the resolved owning instance supplying endpoint+credential.
        verify(brokerConfigDiffService).compare("rmq-a", "instance-a");
        assertThat(output.cluster()).isEqualTo("rmq-a");
        assertThat(output.complete()).isTrue();
        assertThat(output.brokerCount()).isEqualTo(2);
        assertThat(output.comparedFields()).containsExactly("maxMessageSize");
    }
}
