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
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
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
import org.apache.rocketmq.studio.ops.ai.tool.handler.topic.TopicListToolHandler;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.provider.apache.MetadataProvider;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolInstanceRoutingTest {

    @ParameterizedTest
    @CsvSource({"APACHE,false", "APACHE,true", "ALIYUN,false", "TENCENT,false"})
    void bodyClusterRoutesCapabilitiesMetadataAndAuditToTheSameTarget(InstanceVendor vendor, boolean configured) {
        String cluster = configured ? "DefaultCluster" : "prod-orders";
        InstanceRepository instances = mock(InstanceRepository.class);
        RocketMQDefaultClusterResolver configuredClusters = mock(RocketMQDefaultClusterResolver.class);
        InstanceResolver targets = new InstanceResolver(instances, configuredClusters);
        InstanceVO instance = InstanceVO.builder().name(cluster).vendor(vendor)
                .type(vendor == InstanceVendor.APACHE ? InstanceType.PROXY_CLUSTER : InstanceType.CLOUD)
                .endpoint("selected-ns:9876").cloudInstanceId("vendor-resource-id").build();
        if (configured) {
            when(configuredClusters.find(cluster)).thenReturn(Optional.of(instance));
        } else {
            instance.setId(7L);
            when(instances.findByName(cluster)).thenReturn(Optional.of(instance));
            when(instances.findByIdentifier(cluster)).thenReturn(Optional.of(instance));
        }
        InstanceProvider selected = mock(InstanceProvider.class);
        when(selected.vendor()).thenReturn(vendor);
        when(selected.capabilities()).thenReturn(Set.of(InstanceCapability.TOPIC_MANAGEMENT));
        when(selected.listTopics(cluster, null, null)).thenReturn(List.of());
        InstanceProviderRegistry registry = new InstanceProviderRegistry(List.of(selected),
                List.of(),
                targets);
        MetadataProvider globalMetadata = mock(MetadataProvider.class);
        AdminClient globalAdmin = mock(AdminClient.class);
        MetadataService metadata = new MetadataService(globalMetadata, globalAdmin, registry, targets,
                mock(OperationAuditService.class), mock(MessageService.class));
        TopicListToolHandler handler = new TopicListToolHandler(metadata);
        ToolDefinition definition = new ToolCatalog(new DefaultResourceLoader()).getDefinition(handler.name());
        ToolCatalog catalog = mock(ToolCatalog.class);
        when(catalog.find(handler.name())).thenReturn(Optional.of(definition));
        when(catalog.getDefinition(handler.name())).thenReturn(definition);
        when(catalog.list()).thenReturn(List.of(definition));
        AuditService audit = mock(AuditService.class);
        ToolFilterChain filters = new ToolFilterChain(List.of(
                new ToolCapabilityFilter(new CapabilityResolver(registry,
                    targets)), new ToolAuditFilter(audit)));
        ToolExecutionService executor = new ToolExecutionService(catalog,
                List.of(handler),
                filters,
                targets);

        executor.execute(handler.name(), Map.of("cluster", cluster));

        verify(selected).capabilities();
        verify(selected).listTopics(cluster, null, null);
        verify(audit).record(anyString(), anyString(), eq(handler.name()), eq(cluster), isNull(), eq("SUCCESS"));
        verifyNoInteractions(globalMetadata, globalAdmin);
    }
}
