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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerDescribeInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerDescribeOutput;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedBroker;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class BrokerDescribeToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @Mock
    private ClusterProvider clusterProvider;

    @InjectMocks
    private BrokerDescribeToolHandler handler;

    @Test
    void mergesDetailWithRuntimeStatsTest() {
        assertThat(handler.name()).isEqualTo("rmq.broker.describe");
        stubCluster("broker-a");
        BrokerVO enriched = BrokerVO.builder()
                .name("broker-a")
                .addr("10.0.0.1:10911")
                .version("V5_5_0")
                .status(BrokerStatus.running)
                .diskUsage(42.5)
                .tpsIn(100L)
                .tpsOut(200L)
                .putMessagesToday(11L)
                .putMessagesYesterday(12L)
                .getMessagesToday(13L)
                .getMessagesYesterday(14L)
                .runtimeStatsAvailable(true)
                .build();
        when(clusterProvider.discoverBrokers("instance-a", "broker-a")).thenReturn(List.of(enriched));

        BrokerDescribeOutput output = handler.execute(
                new BrokerDescribeInput("rmq-a", "broker-a"), context("instance-a"));

        assertThat(output.brokerName()).isEqualTo("broker-a");
        assertThat(output.addr()).isEqualTo("10.0.0.1:10911");
        assertThat(output.version()).isEqualTo("V5_5_0");
        assertThat(output.status()).isEqualTo(BrokerStatus.running);
        assertThat(output.diskUsage()).isEqualTo(42.5);
        assertThat(output.tpsIn()).isEqualTo(100L);
        assertThat(output.tpsOut()).isEqualTo(200L);
        assertThat(output.putMessagesToday()).isEqualTo(11L);
        assertThat(output.putMessagesYesterday()).isEqualTo(12L);
        assertThat(output.getMessagesToday()).isEqualTo(13L);
        assertThat(output.getMessagesYesterday()).isEqualTo(14L);
        assertThat(output.runtimeStatsAvailable()).isTrue();
    }

    @Test
    void rejectsBrokerOutsideResolvedClusterTest() {
        stubCluster("broker-a");

        assertThatThrownBy(() -> handler.execute(
                new BrokerDescribeInput("rmq-a", "broker-z"), context("instance-a")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Broker not found in cluster rmq-a: broker-z")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode())
                        .isEqualTo(404));
        verifyNoInteractions(clusterProvider);
    }

    private void stubCluster(String brokerName) {
        when(clusterResolver.require("rmq-a")).thenReturn(new ManagedCluster(
                "rmq-a", "instance-a", List.of(),
                List.of(new ManagedBroker(brokerName, 0L, "10.0.0.1:10911", true, null))));
    }
}
