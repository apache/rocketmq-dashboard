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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;

import static org.mockito.Mockito.never;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ToolExecutorInvocationTest {

    private final ToolHandler<?, ?> handler = mock(ToolHandler.class);
    private final ToolCatalog catalog = mock(ToolCatalog.class);
    private final ToolFilterChain chain = mock(ToolFilterChain.class);
    private final ToolDefinition definition = mock(ToolDefinition.class);
    private ToolExecutionService executor;
    private final InstanceRepository instances = mock(InstanceRepository.class);

    @BeforeEach
    void setUp() {
        when(handler.name()).thenReturn("rmq.topic.list");
        when(definition.name()).thenReturn("rmq.topic.list");
        when(definition.isReadOnly()).thenReturn(true);
        when(catalog.find(handler.name())).thenReturn(Optional.of(definition));
        when(catalog.getDefinition(handler.name())).thenReturn(definition);
        when(catalog.list()).thenReturn(List.of(definition));
        InstanceVO instance = InstanceVO.builder().name("instance-a").build();
        instance.setId(1L);
        when(instances.findByName("instance-a")).thenReturn(Optional.of(instance));
        executor = new ToolExecutionService(catalog,
                List.of(handler),
                chain,
                new InstanceResolver(instances, mock(RocketMQDefaultClusterResolver.class)));
    }

    @AfterEach
    void clearAuthentication() {
        AuthenticatedUserContext.clear();
    }

    @Test
    void capturesConsoleUserBeforeEnteringChain() {
        AuthenticatedUserContext.setUsername("alice");
        when(chain.execute(any(ToolInvocation.class))).thenAnswer(invocation -> {
            AuthenticatedUserContext.setUsername("bob");
            return null;
        });
        executor.execute("rmq.topic.list", Map.of("cluster", "instance-a"));
        ArgumentCaptor<ToolInvocation> invocation = ArgumentCaptor.forClass(ToolInvocation.class);
        verify(chain).execute(invocation.capture());
        assertThat(invocation.getValue().context().principal()).isEqualTo("alice");
    }

    @Test
    void passesBoundInstancePrincipalAndHandlerToChain() {
        AuthenticatedUserContext.setUsername("console-user");
        Map<String, Object> input = Map.of("cluster", "instance-a");
        Map<String, Object> output = Map.of("items", List.of());
        when(chain.execute(any(ToolInvocation.class))).thenReturn(output);

        Object result = executor.execute("rmq.topic.list", input, new McpAuthentication("instance-a", "mcp-access-key"));

        ArgumentCaptor<ToolInvocation> invocation = ArgumentCaptor.forClass(ToolInvocation.class);
        verify(chain).execute(invocation.capture());
        assertThat(invocation.getValue().handler()).isSameAs(handler);
        assertThat(invocation.getValue().context().definition()).isSameAs(definition);
        assertThat(invocation.getValue().context().input()).containsExactlyEntriesOf(input);
        assertThat(invocation.getValue().context().cluster()).isEqualTo("instance-a");
        assertThat(invocation.getValue().context().principal()).isEqualTo("mcp-access-key");
        assertThat(result).isSameAs(output);
        verifyNoInteractions(instances);
    }

    @Test
    void rejectsMissingBlankAndNonStringClusterBeforeFilters() {
        for (Map<String, Object> input : List.<Map<String, Object>>of(
                Map.of(), Map.of("cluster", " "), Map.of("cluster", 1), Map.of("cluster", true))) {
            assertThatThrownBy(() -> executor.execute("rmq.topic.list", input))
                    .isInstanceOfSatisfying(ToolExecutionException.class,
                            error -> assertThat(error.getCode()).isEqualTo(400));
        }
        assertThatThrownBy(() -> executor.execute("rmq.topic.list", null))
                .isInstanceOfSatisfying(ToolExecutionException.class,
                        error -> assertThat(error.getCode()).isEqualTo(400));
        verifyNoInteractions(chain);
    }

    @Test
    void bodyClusterRequiresARegisteredNameAndNeverFallsBackToNumericId() {
        assertThatThrownBy(() -> executor.execute("rmq.topic.list", Map.of("cluster", "1")))
                .isInstanceOfSatisfying(ToolExecutionException.class,
                        error -> assertThat(error.getCode()).isEqualTo(404));
        verify(instances).findByName("1");
        verify(instances, never()).findByIdentifier("1");
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsDifferentAuthenticatedInstanceBeforeFilters() {
        InstanceVO other = InstanceVO.builder().name("instance-b").build();
        other.setId(2L);
        when(instances.findByName("instance-b")).thenReturn(Optional.of(other));
        assertThatThrownBy(() -> executor.execute("rmq.topic.list",
                Map.of("cluster", "instance-a"), new McpAuthentication("instance-b", "shared-access-key")))
                .isInstanceOfSatisfying(ToolExecutionException.class,
                        error -> assertThat(error.getCode()).isEqualTo(403));
        verifyNoInteractions(chain);
    }

    @Test
    void rejectsDifferentAuthenticatedClusterWithoutResolvingEitherName() {
        assertThatThrownBy(() -> executor.execute("rmq.topic.list",
                Map.of("cluster", "instance-a"), new McpAuthentication("1", "access-key")))
                .isInstanceOfSatisfying(ToolExecutionException.class,
                        error -> assertThat(error.getCode()).isEqualTo(403));
        verifyNoInteractions(instances, chain);
    }

    @Test
    void acceptsAuthenticatedConfiguredClusterWithoutDatabaseIdentity() {
        executor.execute("rmq.topic.list", Map.of("cluster", "DefaultCluster"),
                new McpAuthentication("DefaultCluster", "access-key"));
        ArgumentCaptor<ToolInvocation> invocation = ArgumentCaptor.forClass(ToolInvocation.class);
        verify(chain).execute(invocation.capture());
        assertThat(invocation.getValue().context().cluster()).isEqualTo("DefaultCluster");
        verifyNoInteractions(instances);
    }

    @Test
    void missingMcpAuthenticationDoesNotBecomeAConsoleCall() {
        assertThatThrownBy(() -> executor.execute("rmq.topic.list", Map.of("cluster", "instance-a"), null))
                .isInstanceOf(ToolExecutionException.class);
        verifyNoInteractions(instances, chain);
    }
}
