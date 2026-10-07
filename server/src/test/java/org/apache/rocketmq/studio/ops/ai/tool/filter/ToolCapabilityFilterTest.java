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
package org.apache.rocketmq.studio.ops.ai.tool.filter;

import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins {@link ToolCapabilityFilter}: the gate that refuses a tool unless the bound Instance
 * supports every capability the tool declares. The ungated pass-through for capability-less tools
 * and the containsAll check (not a spot check) are the two halves of the contract.
 */
class ToolCapabilityFilterTest {

    private final CapabilityResolver capabilityResolver = mock(CapabilityResolver.class);
    private final ToolCapabilityFilter filter = new ToolCapabilityFilter(capabilityResolver);

    /** A chain that records whether it ran and returns a sentinel. */
    private static final class RecordingChain implements ToolExecutionFilter.Chain {

        private boolean proceeded;
        private final Object sentinel = new Object();

        @Override
        public Object proceed(ToolInvocation invocation) {
            this.proceeded = true;
            return sentinel;
        }
    }

    private static ToolDefinition definition(List<String> requiredCapabilities) {
        return new ToolDefinition(
                "rmq.topic.list",
                new ToolDefinition.Cli("topic", "list"),
                "lists topics",
                ToolRiskLevel.L1,
                "topic:read",
                requiredCapabilities,
                Map.of(),
                Map.of(),
                null,
                false,
                null);
    }

    private static ToolInvocation invocation(ToolDefinition definition) {
        ToolExecutionContext context =
                ToolExecutionContext.of("instance-a", definition, Map.of("instanceId", "instance-a"));
        ToolHandler<Map<String, Object>, Object> handler = new ToolHandler<>() {
            @Override
            public String name() {
                return definition.name();
            }

            @Override
            public Class<Map<String, Object>> inputType() {
                return cast();
            }

            @Override
            public Object execute(Map<String, Object> input, ToolExecutionContext executionContext) {
                return "executed";
            }

            @SuppressWarnings("unchecked")
            private Class<Map<String, Object>> cast() {
                return (Class<Map<String, Object>>) (Class<?>) Map.class;
            }
        };
        return new ToolInvocation(context, handler);
    }

    @Test
    void aToolThatDeclaresNoCapabilitiesPassesWithoutALookup() {
        RecordingChain chain = new RecordingChain();
        ToolInvocation call = invocation(definition(List.of()));

        Object output = filter.filter(call, chain);

        assertThat(output).isSameAs(chain.sentinel);
        assertThat(chain.proceeded).isTrue();
        verifyNoInteractions(capabilityResolver);
    }

    @Test
    void aToolWhoseInstanceSupportsEveryCapabilityProceeds() {
        when(capabilityResolver.resolve("instance-a"))
                .thenReturn(Set.of("CLUSTER_TOPOLOGY", "TOPIC_READ"));
        RecordingChain chain = new RecordingChain();
        ToolInvocation call = invocation(definition(List.of("CLUSTER_TOPOLOGY")));

        Object output = filter.filter(call, chain);

        assertThat(output).isSameAs(chain.sentinel);
        assertThat(chain.proceeded).isTrue();
    }

    /**
     * containsAll, not a spot check: an Instance holding one of two required capabilities must
     * still be refused, or a tool would run half-supported against it.
     */
    @Test
    void aToolMissingEvenOneCapabilityIsRefused() {
        when(capabilityResolver.resolve("instance-a")).thenReturn(Set.of("CLUSTER_TOPOLOGY"));
        RecordingChain chain = new RecordingChain();
        ToolInvocation call = invocation(definition(List.of("CLUSTER_TOPOLOGY", "TOPIC_READ")));

        assertThatThrownBy(() -> filter.filter(call, chain))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Instance target does not support tool: rmq.topic.list");
        assertThat(chain.proceeded).isFalse();
    }

    @Test
    void anInstanceWithUnrelatedCapabilitiesDoesNotPassTheGate() {
        when(capabilityResolver.resolve("instance-a"))
                .thenReturn(Set.of("PROXY_MANAGEMENT", "ALERT_READ"));
        RecordingChain chain = new RecordingChain();
        ToolInvocation call = invocation(definition(List.of("CLUSTER_TOPOLOGY")));

        assertThatThrownBy(() -> filter.filter(call, chain))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("Instance target does not support tool: rmq.topic.list");
        assertThat(chain.proceeded).isFalse();
    }

    @Test
    void theFilterDeclaresTheCapabilityType() {
        assertThat(filter.type()).isEqualTo(ToolExecutionFilter.Type.CAPABILITY);
    }
}
