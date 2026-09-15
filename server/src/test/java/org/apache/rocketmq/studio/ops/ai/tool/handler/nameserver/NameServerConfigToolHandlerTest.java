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
package org.apache.rocketmq.studio.ops.ai.tool.handler.nameserver;

import org.apache.rocketmq.studio.cluster.nameserver.NameServerConfigDiffService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverConfigInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverConfigItem;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class NameServerConfigToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @Mock
    private NameServerConfigDiffService configDiffService;

    @InjectMocks
    private NameServerConfigToolHandler handler;

    @Test
    void readsConfigThroughResolvedInstanceTest() {
        assertThat(handler.name()).isEqualTo("rmq.nameserver.config");
        when(clusterResolver.resolveInstanceId("rmq-a")).thenReturn("instance-a");
        when(configDiffService.read("rmq-a", "instance-a")).thenReturn(List.of(
                new NameServerConfigDiffService.NodeConfig(
                        "ns-a:9876", Map.of("listenPort", "9876"))));

        ListOutput<NameserverConfigItem> result = handler.execute(
                new NameserverConfigInput("rmq-a"), context("instance-a"));

        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.addr()).isEqualTo("ns-a:9876");
            assertThat(item.config()).containsEntry("listenPort", "9876");
        });
        verify(configDiffService).read("rmq-a", "instance-a");
    }
}
