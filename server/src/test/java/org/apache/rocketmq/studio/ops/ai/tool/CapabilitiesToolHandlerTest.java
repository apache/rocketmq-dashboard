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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.ops.ai.tool.handler.CapabilitiesToolHandler;

import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.CapabilitiesOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ClusterInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class CapabilitiesToolHandlerTest {

    @Test
    void projectsSortedCapabilitiesFromTheBoundInstance() {
        CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);
        when(capabilityResolver.resolve("instance-a"))
                .thenReturn(new LinkedHashSet<>(List.of("TOPIC_MANAGEMENT", "REMOTING")));

        CapabilitiesOutput result = new CapabilitiesToolHandler(capabilityResolver)
                .execute(
                        new ClusterInput("DefaultCluster"),
                        ToolExecutionContext.of(
                                "instance-a", null, Map.of("cluster", "DefaultCluster")));

        assertThat(result.cluster()).isEqualTo("instance-a");
        assertThat(result.capabilities())
                .containsExactly("REMOTING", "TOPIC_MANAGEMENT");
        verify(capabilityResolver).resolve("instance-a");
        verifyNoMoreInteractions(capabilityResolver);
    }
}
