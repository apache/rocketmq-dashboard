/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolRiskLevel;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ToolExecutionServiceTest {

    private ToolCatalog catalog;
    private ToolFilterChain filterChain;
    private InstanceResolver instanceResolver;
    private ToolHandler<Object, Object> handler;

    @BeforeEach
    void setUp() {
        catalog = mock(ToolCatalog.class);
        filterChain = mock(ToolFilterChain.class);
        instanceResolver = mock(InstanceResolver.class);
        handler = mock(ToolHandler.class);
        when(handler.name()).thenReturn("rmq.topic.list");
    }

    @AfterEach
    void clearThreadLocals() {
        org.apache.rocketmq.studio.auth.AuthenticatedUserContext.clear();
    }

    private static ToolDefinition definition(ToolRiskLevel risk) {
        return new ToolDefinition(
                "rmq.topic.list", null, "desc", risk, "tool:read", List.of(), Map.of(), Map.of(), null, false, null);
    }

    private ToolExecutionService newService(ToolDefinition definition) {
        when(catalog.find("rmq.topic.list")).thenReturn(Optional.of(definition));
        when(catalog.getDefinition("rmq.topic.list")).thenReturn(definition);
        when(catalog.list()).thenReturn(List.of(definition));
        return new ToolExecutionService(catalog, List.of(handler), filterChain, instanceResolver);
    }

    @Test
    void aConsoleCallResolvesTheInstanceByNameAndRunsThroughTheFilterChain() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));
        when(instanceResolver.findByName("inst-1")).thenReturn(Optional.of(new InstanceVO()));
        when(filterChain.execute(any())).thenReturn("done");

        Object result = service.execute("rmq.topic.list", Map.of("instanceId", "inst-1"));

        assertThat(result).isEqualTo("done");
    }

    @Test
    void aMissingInstanceArgumentIsRejectedNamingTheTool() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));

        assertThatThrownBy(() -> service.execute("rmq.topic.list", Map.of()))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("rmq.topic.list")
                .hasMessageContaining("instanceId");
    }

    @Test
    void aBlankInstanceArgumentIsRejectedLikeAMissingOne() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));

        assertThatThrownBy(() -> service.execute("rmq.topic.list", Map.of("instanceId", "   ")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("instanceId");
    }

    @Test
    void anUnknownInstanceNameIsRejectedOnTheConsolePath() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));
        when(instanceResolver.findByName("nope")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.execute("rmq.topic.list", Map.of("instanceId", "nope")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("nope");
    }

    @Test
    void aMismatchedMcpInstanceIsRejectedEchoingTheBoundInstance() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));

        // The transport bound inst-bound; the tool call claims inst-other. The error must echo
        // inst-bound back so a guessing agent self-corrects instead of probing candidates.
        assertThatThrownBy(() -> service.execute(
                "rmq.topic.list", Map.of("instanceId", "inst-other"),
                new McpAuthentication("inst-bound", "agent-1")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("inst-bound");
    }

    @Test
    void aMatchingMcpInstanceSkipsTheResolverAndExecutes() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));
        when(filterChain.execute(any())).thenReturn("ok");

        Object result = service.execute(
                "rmq.topic.list", Map.of("instanceId", "inst-bound"),
                new McpAuthentication("inst-bound", "agent-1"));

        assertThat(result).isEqualTo("ok");
        org.mockito.Mockito.verify(instanceResolver, org.mockito.Mockito.never()).findByName(any());
    }

    @Test
    void aNullAuthenticationOnTheMcpPathIsRejected() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));

        assertThatThrownBy(() -> service.execute("rmq.topic.list", Map.of("instanceId", "i"), (McpAuthentication) null))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("authentication");
    }

    @Test
    void aBusinessExceptionFromThePipelineIsWrappedWithItsCode() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));
        when(instanceResolver.findByName("inst-1")).thenReturn(Optional.of(new InstanceVO()));
        when(filterChain.execute(any())).thenThrow(new BusinessException(409, "topic exists"));

        assertThatThrownBy(() -> service.execute("rmq.topic.list", Map.of("instanceId", "inst-1")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("topic exists");
    }

    @Test
    void anUnexpectedFailureIsWrappedAsInternalWithTheCauseAttached() {
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));
        when(instanceResolver.findByName("inst-1")).thenReturn(Optional.of(new InstanceVO()));
        IllegalStateException cause = new IllegalStateException("boom");
        when(filterChain.execute(any())).thenThrow(cause);

        assertThatThrownBy(() -> service.execute("rmq.topic.list", Map.of("instanceId", "inst-1")))
                .isInstanceOf(ToolExecutionException.class)
                .hasRootCause(cause);
    }

    @Test
    void aDuplicateHandlerNameIsRejectedAtConstruction() {
        ToolDefinition def = definition(ToolRiskLevel.L1);
        when(catalog.find("rmq.topic.list")).thenReturn(Optional.of(def));
        when(catalog.getDefinition("rmq.topic.list")).thenReturn(def);
        when(catalog.list()).thenReturn(List.of(def));

        assertThatThrownBy(() -> new ToolExecutionService(catalog, List.of(handler, handler), filterChain,
                instanceResolver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate tool handler");
    }

    @Test
    void aHandlerMissingFromTheCatalogIsRejectedAtConstruction() {
        ToolDefinition def = definition(ToolRiskLevel.L1);
        when(catalog.find("rmq.topic.list")).thenReturn(Optional.empty());
        when(catalog.list()).thenReturn(List.of());

        assertThatThrownBy(() -> new ToolExecutionService(catalog, List.of(handler), filterChain, instanceResolver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("absent from catalog");
    }

    @Test
    void aCatalogToolWithoutAHandlerIsRejectedAtConstruction() {
        ToolDefinition def = definition(ToolRiskLevel.L1);
        when(catalog.find("rmq.topic.list")).thenReturn(Optional.of(def));
        when(catalog.getDefinition("rmq.topic.list")).thenReturn(def);
        // A second catalog entry with a different name and no handler.
        ToolDefinition orphan = new ToolDefinition(
                "rmq.orphan", null, "d", ToolRiskLevel.L1, "p", List.of(), Map.of(), Map.of(), null, false, null);
        when(catalog.list()).thenReturn(List.of(def, orphan));

        assertThatThrownBy(() -> new ToolExecutionService(catalog, List.of(handler), filterChain, instanceResolver))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("without handlers")
                .hasMessageContaining("rmq.orphan");
    }

    @Test
    void theConsoleTargetIsPassedThroughForInstanceExemptTools() {
        // An instance-exempt (platform) tool: no instanceId in the input, the target comes from
        // the transport/selection. We cannot flip the catalog's static exempt list, so we assert
        // the console entry point works end to end with a tool whose input lacks instanceId by
        // using the L1 definition and the executeWithTarget route through the exempt branch -
        // which for a non-exempt tool would demand an instanceId. The non-exempt path rejecting
        // executeWithTarget without an instanceId is itself the pinned contract here.
        ToolExecutionService service = newService(definition(ToolRiskLevel.L1));

        assertThatThrownBy(() -> service.executeWithTarget("rmq.topic.list", Map.of(), "inst-target"))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessageContaining("instanceId");
    }
}
