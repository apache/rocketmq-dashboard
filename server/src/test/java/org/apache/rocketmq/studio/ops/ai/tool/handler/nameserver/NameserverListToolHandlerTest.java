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

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.nameserver.NameserverListInput;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
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
class NameserverListToolHandlerTest {

    @Mock
    private PlatformClusterResolver clusterResolver;

    @InjectMocks
    private NameserverListToolHandler handler;

    @Test
    void listsNameserversOfResolvedClusterTest() {
        assertThat(handler.name()).isEqualTo("rmq.nameserver.list");
        when(clusterResolver.require("rmq-a")).thenReturn(new ManagedCluster(
                "rmq-a", "instance-a", List.of("ns-b:9876", "ns-a:9876"), List.of()));

        ListOutput<NameserverListToolHandler.Item> result = handler.execute(
                new NameserverListInput("rmq-a"), context("instance-a"));

        assertThat(result.items())
                .extracting(NameserverListToolHandler.Item::namesrvAddr)
                .containsExactly("ns-a:9876", "ns-b:9876");
        NameserverListToolHandler.Item item = result.items().getFirst();
        assertThat(item.id()).isEqualTo("ns-a:9876");
        assertThat(item.name()).isEqualTo("ns-a:9876");
        assertThat(item.status()).isEqualTo("UNKNOWN");
    }

    @Test
    void aggregatesAllInstanceEndpointsDeduplicatedTest() {
        when(clusterResolver.manageableInstances()).thenReturn(List.of(
                InstanceVO.builder().name("instance-a").vendor(InstanceVendor.APACHE)
                        .endpoint("ns-a:9876;ns-shared:9876").build(),
                InstanceVO.builder().name("instance-b").vendor(InstanceVendor.APACHE)
                        .endpoint("ns-shared:9876,ns-b:9876").build()));

        ListOutput<NameserverListToolHandler.Item> result = handler.execute(
                new NameserverListInput(null), context("instance-a"));

        assertThat(result.items())
                .extracting(NameserverListToolHandler.Item::namesrvAddr)
                .containsExactly("ns-a:9876", "ns-b:9876", "ns-shared:9876");
    }
}
