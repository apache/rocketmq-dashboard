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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.ops.ai.auth.McpAuthentication;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.cluster.ClusterListItem;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.ListOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolCapabilityFilter;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolValidationFilter;
import org.apache.rocketmq.studio.ops.ai.tool.handler.cluster.ClusterListToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedBroker;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver.ManagedCluster;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Instance binding of the platform-level tools listed in
 * {@code ToolCatalog.INSTANCE_ID_EXEMPT_TOOLS}: they carry no {@code instanceId} argument, yet
 * they are capability-gated, so the authenticated Instance has to reach the execution context.
 * The chain here is the real validation + capability chain over the real catalog schemas.
 */
class ToolInstanceBindingTest {

    private static final String INSTANCE = "open-source-local";
    private static final String CLOUD_INSTANCE = "aliyun-prod";
    private static final String CLUSTER_LIST = "rmq.cluster.list";
    private static final String TOPIC_LIST = "rmq.topic.list";
    private static final String AUDIT_LIST = "rmq.audit.list";

    private static final ToolCatalog CATALOG = new ToolCatalog(new DefaultResourceLoader());
    private static final ToolSchemaValidator SCHEMA_VALIDATOR = new ToolSchemaValidator(
            CATALOG, new ObjectMapper(), JsonMapper.builder().build());

    private InstanceRepository instances;
    private InstanceResolver instanceResolver;
    private InstanceProvider apacheProvider;
    private InstanceProvider cloudProvider;
    private PlatformClusterResolver platformClusters;
    private ToolFilterChain filterChain;

    @BeforeEach
    void setUp() {
        instances = mock(InstanceRepository.class);
        instanceResolver = new InstanceResolver(instances, mock(RocketMQDefaultClusterResolver.class));
        apacheProvider = mock(InstanceProvider.class);
        when(apacheProvider.vendor()).thenReturn(InstanceVendor.APACHE);
        when(apacheProvider.capabilities()).thenReturn(Set.of(InstanceCapability.TOPIC_MANAGEMENT));
        cloudProvider = mock(InstanceProvider.class);
        when(cloudProvider.vendor()).thenReturn(InstanceVendor.ALIYUN);
        when(cloudProvider.capabilities()).thenReturn(Set.of(InstanceCapability.TOPIC_MANAGEMENT));
        CapabilityResolver capabilityResolver = new CapabilityResolver(
                new InstanceProviderRegistry(List.of(apacheProvider, cloudProvider), List.of(), instanceResolver),
                instanceResolver);
        filterChain = new ToolFilterChain(List.of(
                new ToolValidationFilter(SCHEMA_VALIDATOR),
                new ToolCapabilityFilter(capabilityResolver)));
        platformClusters = mock(PlatformClusterResolver.class);
        registerInstance(INSTANCE, InstanceVendor.APACHE, InstanceType.PROXY_CLUSTER);
        registerInstance(CLOUD_INSTANCE, InstanceVendor.ALIYUN, InstanceType.CLOUD);
    }

    @AfterEach
    void clearUserContext() {
        AuthenticatedUserContext.clear();
    }

    /** The reported bug: an exempt tool over MCP died in capability lookup despite a signed Instance. */
    @Test
    void authenticatedExemptToolResolvesCapabilitiesAndExecutesTest() {
        when(platformClusters.scanWithBrokerVersions()).thenReturn(List.of(new ManagedCluster(
                "DefaultCluster", INSTANCE, List.of("ns:9876"),
                List.of(new ManagedBroker("broker-a", 0L, "10.0.0.1:10911", true, "V5_5_0")))));
        ToolExecutionService executor = executorFor(new ClusterListToolHandler(platformClusters));

        Object result = executor.execute(CLUSTER_LIST, Map.of(), new McpAuthentication(INSTANCE, "access-key"));

        assertThat(result).isInstanceOfSatisfying(ListOutput.class, output -> assertThat(output.items())
                .extracting(item -> ((ClusterListItem) item).cluster())
                .containsExactly("DefaultCluster"));
        verify(apacheProvider).capabilities();
        verify(platformClusters).scanWithBrokerVersions();
    }

    /** The binding goes into the context only: the argument map stays schema-clean. */
    @Test
    void authenticatedExemptToolBindsContextWithoutInjectingAnArgumentTest() {
        RecordingHandler handler = new RecordingHandler(CLUSTER_LIST);
        ToolExecutionService executor = executorFor(handler);

        executor.execute(CLUSTER_LIST, Map.of("status", "healthy"),
                new McpAuthentication(INSTANCE, "access-key"));

        assertThat(handler.context.instanceId()).isEqualTo(INSTANCE);
        assertThat(handler.context.input()).containsExactlyEntriesOf(Map.of("status", "healthy"));
        assertThat(handler.convertedInput).containsExactlyEntriesOf(Map.of("status", "healthy"));
        assertThat(handler.context.principal()).isEqualTo("access-key");
    }

    /** No authenticated Instance must stay a loud failure, never a silently unscoped execution. */
    @Test
    void exemptToolWithoutABoundInstanceFailsCapabilityLookupTest() {
        RecordingHandler handler = new RecordingHandler(CLUSTER_LIST);
        ToolExecutionService executor = executorFor(handler);

        assertThatThrownBy(() -> executor.execute(CLUSTER_LIST, Map.of()))
                .isInstanceOfSatisfying(ToolExecutionException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(400);
                    assertThat(error.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
                    assertThat(error.getMessage()).isEqualTo("Capability lookup requires a bound Instance");
                    assertThat(error.getHint()).isEqualTo("Select an Instance and retry the tool call.");
                });
        assertThat(handler.context).isNull();
    }

    /** The fix must not paper over the schema: instanceId stays a rejected argument for exempt tools. */
    @Test
    void exemptToolStillRejectsAnInstanceIdArgumentTest() {
        RecordingHandler handler = new RecordingHandler(CLUSTER_LIST);
        ToolExecutionService executor = executorFor(handler);

        assertThatThrownBy(() -> executor.execute(CLUSTER_LIST, Map.of("instanceId", INSTANCE),
                new McpAuthentication(INSTANCE, "access-key")))
                .isInstanceOfSatisfying(ToolExecutionException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(400);
                    assertThat(error.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
                    assertThat(error.getMessage()).contains(CLUSTER_LIST).contains("instanceId");
                });
        assertThat(handler.context).isNull();
    }

    /** Capability gating keeps its teeth: a bound Instance without the capability is still refused. */
    @Test
    void exemptToolBoundToAnUnsupportedInstanceStaysCapabilityGatedTest() {
        RecordingHandler handler = new RecordingHandler(CLUSTER_LIST);
        ToolExecutionService executor = executorFor(handler);

        assertThatThrownBy(() -> executor.execute(CLUSTER_LIST, Map.of(),
                new McpAuthentication(CLOUD_INSTANCE, "access-key")))
                .isInstanceOfSatisfying(ToolExecutionException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(400);
                    assertThat(error.getErrorCode()).isEqualTo("TOOL_CAPABILITY_UNSUPPORTED");
                });
        assertThat(handler.context).isNull();
        verifyNoInteractions(platformClusters);
    }

    /** Control: instance-addressed tools keep reading their target from the argument. */
    @Test
    void instanceAddressedToolIsUnaffectedTest() {
        RecordingHandler handler = new RecordingHandler(TOPIC_LIST);
        ToolExecutionService executor = executorFor(handler);

        executor.execute(TOPIC_LIST, Map.of("instanceId", INSTANCE),
                new McpAuthentication(INSTANCE, "access-key"));

        assertThat(handler.context.instanceId()).isEqualTo(INSTANCE);
        assertThat(handler.convertedInput).containsEntry("instanceId", INSTANCE);
    }

    /**
     * The console has no signed instance header, so the Instance selected in the Tool Playground is
     * the binding; the operator's session identity stays the principal.
     */
    @Test
    void consoleSelectedInstanceBindsAnExemptToolTest() {
        AuthenticatedUserContext.setUsername("console-user");
        RecordingHandler handler = new RecordingHandler(CLUSTER_LIST);
        ToolExecutionService executor = executorFor(handler);

        executor.executeWithTarget(CLUSTER_LIST, Map.of(), INSTANCE);

        assertThat(handler.context.instanceId()).isEqualTo(INSTANCE);
        assertThat(handler.context.input()).isEmpty();
        assertThat(handler.context.principal()).isEqualTo("console-user");
        verify(apacheProvider).capabilities();
    }

    /** Global scope in the playground selects no Instance, which must stay a loud failure. */
    @Test
    void consoleGlobalScopeLeavesAnExemptToolUnboundTest() {
        RecordingHandler handler = new RecordingHandler(CLUSTER_LIST);
        ToolExecutionService executor = executorFor(handler);

        assertThatThrownBy(() -> executor.executeWithTarget(CLUSTER_LIST, Map.of(), " "))
                .isInstanceOfSatisfying(ToolExecutionException.class, error -> {
                    assertThat(error.getCode()).isEqualTo(400);
                    assertThat(error.getMessage()).isEqualTo("Capability lookup requires a bound Instance");
                });
        assertThat(handler.context).isNull();
    }

    /** On the console path the argument target is still resolved against the registry. */
    @Test
    void consoleInstanceAddressedToolStillVerifiesItsArgumentTargetTest() {
        RecordingHandler handler = new RecordingHandler(TOPIC_LIST);
        ToolExecutionService executor = executorFor(handler);

        executor.executeWithTarget(TOPIC_LIST, Map.of("instanceId", INSTANCE), "");

        assertThat(handler.context.instanceId()).isEqualTo(INSTANCE);
        verify(instances, atLeastOnce()).findByName(INSTANCE);

        assertThatThrownBy(() -> executor.executeWithTarget(TOPIC_LIST, Map.of("instanceId", "unknown"), ""))
                .isInstanceOfSatisfying(ToolExecutionException.class,
                        error -> assertThat(error.getCode()).isEqualTo(404));
    }

    /**
     * Documented decision, pinned: an exempt tool that declares no capability needs no binding,
     * because its data is deployment-wide. Gated exempt tools are covered by the tests above.
     */
    @Test
    void exemptToolWithoutRequiredCapabilitiesRunsUnboundTest() {
        RecordingHandler handler = new RecordingHandler(AUDIT_LIST);
        ToolExecutionService executor = executorFor(handler);

        executor.execute(AUDIT_LIST, Map.of());

        assertThat(handler.context.instanceId()).isNull();
        verifyNoInteractions(instances);
    }

    private void registerInstance(String name, InstanceVendor vendor, InstanceType type) {
        InstanceVO instance = InstanceVO.builder()
                .name(name).vendor(vendor).type(type).endpoint("ns:9876").build();
        instance.setId(11L);
        when(instances.findByName(name)).thenReturn(Optional.of(instance));
    }

    private ToolExecutionService executorFor(ToolHandler<?, ?> handler) {
        ToolDefinition definition = CATALOG.getDefinition(handler.name());
        ToolCatalog scoped = mock(ToolCatalog.class);
        when(scoped.find(handler.name())).thenReturn(Optional.of(definition));
        when(scoped.getDefinition(handler.name())).thenReturn(definition);
        when(scoped.list()).thenReturn(List.of(definition));
        return new ToolExecutionService(scoped, List.of(handler), filterChain, instanceResolver);
    }

    /** Records the execution context and answers with the minimal valid {@code items} envelope. */
    private static final class RecordingHandler implements ToolHandler<Map<String, Object>, Map<String, Object>> {

        private final String toolName;
        private ToolExecutionContext context;
        private Map<String, Object> convertedInput;

        private RecordingHandler(String toolName) {
            this.toolName = toolName;
        }

        @Override
        public String name() {
            return toolName;
        }

        @Override
        @SuppressWarnings("unchecked")
        public Class<Map<String, Object>> inputType() {
            return (Class<Map<String, Object>>) (Class<?>) Map.class;
        }

        @Override
        public Map<String, Object> execute(Map<String, Object> input, ToolExecutionContext context) {
            this.convertedInput = input;
            this.context = context;
            return Map.of("items", List.of());
        }
    }
}
