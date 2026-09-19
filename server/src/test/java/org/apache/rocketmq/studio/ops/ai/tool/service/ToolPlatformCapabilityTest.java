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
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.message.MessageService;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolAuditFilter;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolCapabilityFilter;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.apache.rocketmq.studio.ops.ai.tool.handler.cluster.ClusterListToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.support.PlatformClusterResolver;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.provider.apache.MetadataProvider;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Platform-level tools carry no instanceId: they are addressed by a physical clusterName and
 * every one of them declares requiredCapabilities. The capability filter must not demand an
 * instance for them — discovery advertises these tools and both transports dispatch them with
 * a null context instanceId, so a capability lookup there breaks every platform tool call.
 */
class ToolPlatformCapabilityTest {

    @Test
    void platformToolWithoutInstanceExecutesThroughTheCapabilityFilterTest() {
        InstanceRepository instances = mock(InstanceRepository.class);
        RocketMQDefaultClusterResolver configuredClusters = mock(RocketMQDefaultClusterResolver.class);
        InstanceResolver targets = new InstanceResolver(instances, configuredClusters);
        InstanceVO instance = InstanceVO.builder().name("prod-apache")
                .endpoint("selected-ns:9876").build();
        instance.setId(7L);
        when(instances.findByName("prod-apache")).thenReturn(Optional.of(instance));

        PlatformClusterResolver platformClusters = mock(PlatformClusterResolver.class);
        when(platformClusters.scanWithBrokerVersions()).thenReturn(List.of());
        ClusterListToolHandler handler = new ClusterListToolHandler(platformClusters);

        ToolDefinition definition = new ToolCatalog(new DefaultResourceLoader())
                .getDefinition(handler.name());
        assertThat(definition.requiredCapabilities()).isNotEmpty();

        // An instance context exists for discovery, but the platform-tool call itself
        // dispatches with a null instanceId — exactly what resolveTargetInstance produces.
        InstanceProviderRegistry registry = mock(InstanceProviderRegistry.class);
        CapabilityResolver capabilityResolver = new CapabilityResolver(registry, targets);
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(catalog.find(handler.name())).thenReturn(Optional.of(definition));
        when(catalog.getDefinition(handler.name())).thenReturn(definition);
        when(catalog.list()).thenReturn(List.of(definition));
        AuditService audit = mock(AuditService.class);
        ToolFilterChain filters = new ToolFilterChain(List.of(
                new ToolCapabilityFilter(capabilityResolver), new ToolAuditFilter(audit)));
        MetadataProvider globalMetadata = mock(MetadataProvider.class);
        AdminClient globalAdmin = mock(AdminClient.class);
        MetadataService metadata = new MetadataService(globalMetadata, globalAdmin, registry,
                targets, mock(OperationAuditService.class), mock(MessageService.class),
                mock(RuntimeAdminClientResolver.class));
        ToolExecutionService executor = new ToolExecutionService(catalog,
                List.of(handler),
                filters,
                targets);

        Object output = executor.execute(handler.name(), Map.of());

        assertThat(output).isNotNull();
        verify(audit).record(anyString(), anyString(), eq(handler.name()), isNull(), isNull(),
                eq("SUCCESS"));
    }
}
