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
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceResolver;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.apache.rocketmq.studio.ops.ai.McpToolController;
import org.apache.rocketmq.studio.ops.ai.ToolController;
import org.apache.rocketmq.studio.ops.ai.mcp.McpConfiguration;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;

import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;

import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolMutationFilter;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolAuditFilter;
import org.apache.rocketmq.studio.ops.ai.tool.handler.MutationToolHandler;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.mcp.server.common.autoconfigure.properties.McpServerStreamableHttpProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.DefaultResourceLoader;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ToolTokenConfigurationTest {

    private static final String TEST_SECRET =
            "cm9ja2V0bXEtc3R1ZGlvLXRlc3QtY29uZmlybS10b2tlbi1rZXk=";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean("mcpServerJsonMapper", JsonMapper.class, JsonMapper::new)
            .withBean(McpServerStreamableHttpProperties.class, McpServerStreamableHttpProperties::new)
            .withBean(RuntimeAdminClientResolver.class, () -> mock(RuntimeAdminClientResolver.class))
            .withBean(CloudCredentialRepository.class, () -> mock(CloudCredentialRepository.class))
            .withInitializer(context -> {
                ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
                context.getBeanFactory().registerSingleton("toolCatalog", catalog);
                for (ToolDefinition definition : catalog.list()) {
                    context.getBeanFactory().registerSingleton(definition.name(), previewHandler(definition.name()));
                }
            })
            .withBean(ToolDiscoveryService.class, () -> mock(ToolDiscoveryService.class))
            .withBean(AuditService.class, () -> mock(AuditService.class))
            .withBean(InstanceRepository.class, () -> mock(InstanceRepository.class))
            .withBean(InstanceResolver.class, () -> mock(InstanceResolver.class))
            .withBean(RocketMQDefaultClusterResolver.class, () -> mock(RocketMQDefaultClusterResolver.class))
            .withUserConfiguration(
                    ToolTokenService.class, ToolMutationFilter.class, ToolAuditFilter.class,
                    ToolFilterChain.class, ToolExecutionService.class, ToolController.class,
                    McpToolController.class, McpConfiguration.class);

    @Test
    void missingSecretAllowsReadOnlyToolsButRejectsTokenOperations() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            ToolCatalog catalog = context.getBean(ToolCatalog.class);
            ToolExecutionContext request = executionContext(catalog, "rmq.topic.create", Map.of("topic", "orders"));
            ToolTokenService tokens = context.getBean(ToolTokenService.class);
            assertThatThrownBy(() -> tokens.issue(request)).isInstanceOfSatisfying(ToolExecutionException.class,
                    failure -> assertThat(failure.getErrorCode()).isEqualTo("UNAVAILABLE"));
            assertThatThrownBy(() -> tokens.verify(request)).isInstanceOfSatisfying(ToolExecutionException.class,
                    failure -> assertThat(failure.getErrorCode()).isEqualTo("UNAVAILABLE"));
            ToolExecutionContext read = executionContext(catalog, "rmq.topic.list", Map.of("cluster", "dev"));
            assertThat(context.getBean(ToolFilterChain.class).execute(
                    new ToolInvocation(read, previewHandler("rmq.topic.list")))).isEqualTo(read.input());
        });
    }

    @Test
    void configuredSecretAllowsPreviewAndConfirmedExecution() {
        runner.withPropertyValues("studio.ai.token-secret=" + TEST_SECRET)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    ToolFilterChain chain = context.getBean(ToolFilterChain.class);
                    ToolCatalog catalog = context.getBean(ToolCatalog.class);
                    MutationToolHandler<Map<String, Object>, Object> handler = previewHandler("rmq.topic.create");
                    ToolExecutionContext preview = executionContext(catalog, "rmq.topic.create", Map.of(
                            "cluster", "dev", "topic", "orders", "dry_run", true));
                    MutationOutput<?> plan = (MutationOutput<?>) chain.execute(new ToolInvocation(preview, handler));
                    Map<?, ?> output = new ObjectMapper().convertValue(plan, Map.class);
                    assertThat(output.get("confirm_token")).isInstanceOf(String.class);
                    ToolExecutionContext apply = executionContext(catalog, "rmq.topic.create", Map.of(
                            "cluster", "dev", "topic", "orders", "confirm_token", output.get("confirm_token")));
                    AtomicBoolean executed = new AtomicBoolean();
                    MutationOutput<?> result = (MutationOutput<?>) chain.execute(new ToolInvocation(apply, executeHandler(executed)));
                    assertThat(executed).isTrue();
                    assertThat(result.status()).isEqualTo(MutationOutput.Status.EXECUTED);
                });
    }

    private static ToolExecutionContext executionContext(
            ToolCatalog catalog, String tool, Map<String, Object> input) {
        return ToolExecutionContext.of("instance-a", catalog.getDefinition(tool), input, "alice");
    }

    @SuppressWarnings("unchecked")
    private static MutationToolHandler<Map<String, Object>, Object> previewHandler(String name) {
        return new MutationToolHandler<>((Class<Map<String, Object>>) (Class<?>) Map.class) {
            @Override
            public String name() {
                return name;
            }

            @Override
            public ToolPlan preview(Map<String, Object> input, ToolExecutionContext context) {
                return ToolPlan.builder("Create orders").build();
            }

            @Override
            public Object execute(Map<String, Object> input, ToolExecutionContext context) {
                return new LinkedHashMap<>(input);
            }
        };
    }

    @SuppressWarnings("unchecked")
    private static MutationToolHandler<Map<String, Object>, Object> executeHandler(AtomicBoolean executed) {
        return new MutationToolHandler<>((Class<Map<String, Object>>) (Class<?>) Map.class) {
            @Override
            public String name() {
                return "rmq.topic.create";
            }

            @Override
            public ToolPlan preview(Map<String, Object> input, ToolExecutionContext context) {
                return ToolPlan.builder("Create orders").build();
            }

            @Override
            public Object execute(Map<String, Object> input, ToolExecutionContext context) {
                executed.set(true);
                return Map.of("topic", "orders");
            }
        };
    }
}
