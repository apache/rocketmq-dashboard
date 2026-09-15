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

import org.apache.rocketmq.studio.cluster.broker.ClusterService;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;
import org.apache.rocketmq.studio.cluster.config.UpdateConfigDTO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigUpdateInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.broker.BrokerConfigUpdateOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class BrokerConfigUpdateToolHandlerTest {

    @Mock
    private ClusterService clusterService;

    @InjectMocks
    private BrokerConfigUpdateToolHandler handler;

    @Test
    void applyShouldUpdateConfig() {
        assertThat(handler.name()).isEqualTo("rmq.broker.config_update");
        ToolExecutionContext execution = context("instance-1", Map.of(
                "cluster", "instance-1"));
        ClusterConfigUpdateResultVO resultVO = ClusterConfigUpdateResultVO.builder()
                .status(ClusterConfigUpdateResultVO.Status.SUCCESS)
                .successfulBrokers(List.of("broker-a"))
                .failedBrokers(List.of())
                .build();
        when(clusterService.updateClusterConfig(any(UpdateConfigDTO.class), same(execution.cluster())))
                .thenReturn(resultVO);

        BrokerConfigUpdateOutput output = handler.execute(new BrokerConfigUpdateInput(
                "instance-1", null, null, null, 4194304, 72,
                null, null, null), execution);

        assertThat(output.status()).isEqualTo("SUCCESS");
        assertThat(output.successfulBrokers()).containsExactly("broker-a");

        ArgumentCaptor<UpdateConfigDTO> captor = ArgumentCaptor.forClass(UpdateConfigDTO.class);
        verify(clusterService).updateClusterConfig(captor.capture(), same(execution.cluster()));
        assertThat(captor.getValue().getId()).isNull();
        assertThat(captor.getValue().getInstanceId()).isEqualTo("instance-1");
        assertThat(captor.getValue().getMaxMessageSize()).isEqualTo(4194304);
        assertThat(captor.getValue().getFileReservedTime()).isEqualTo(72);
    }
}
