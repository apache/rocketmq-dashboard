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

import org.apache.rocketmq.studio.ops.ai.tool.handler.nameserver.NameserverListToolHandler;

import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterListOutput;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class NameserverListToolHandlerTest {

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @InjectMocks
    private NameserverListToolHandler handler;

    @Test
    void executeShouldListNameservers() {
        assertThat(handler.name()).isEqualTo("rmq.nameserver.list");
        when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("127.0.0.1:9876");

        ClusterListOutput<NameserverListToolHandler.Item> result = handler.execute(
                new ClusterInput("instance-a"), context("instance-a"));

        assertThat(result.items()).hasSize(1);
        NameserverListToolHandler.Item item = result.items().getFirst();
        assertThat(item.id()).isEqualTo("127.0.0.1:9876");
        assertThat(item.name()).isEqualTo("127.0.0.1:9876");
        assertThat(item.namesrvAddr()).isEqualTo("127.0.0.1:9876");
        verify(runtimeAdminClientResolver).resolveEndpoint("instance-a");
    }
}
