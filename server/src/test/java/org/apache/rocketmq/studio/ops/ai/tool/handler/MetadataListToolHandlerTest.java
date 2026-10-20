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
package org.apache.rocketmq.studio.ops.ai.tool.handler;

import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupListInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicListInput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolDefinition;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.handler.group.ConsumerGroupListToolHandler;
import org.apache.rocketmq.studio.ops.ai.tool.handler.topic.TopicListToolHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MetadataListToolHandlerTest {

    @Test
    @SuppressWarnings("unchecked")
    void metadataListCatalogsShouldAdvertisePhysicalClusterFilters() {
        ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());

        Map<String, Object> topicProperties = (Map<String, Object>) catalog
                .getDefinition("rmq.topic.list").inputSchema().get("properties");
        Map<String, Object> groupProperties = (Map<String, Object>) catalog
                .getDefinition("rmq.group.list").inputSchema().get("properties");

        assertThat(topicProperties).containsKey("clusterId");
        assertThat(groupProperties).containsKey("clusterId");
    }

    @Test
    void topicListShouldForwardPhysicalClusterFilter() {
        MetadataService metadata = mock(MetadataService.class);
        TopicListToolHandler handler = new TopicListToolHandler(metadata);
        ToolDefinition definition = new ToolCatalog(new DefaultResourceLoader())
                .getDefinition(handler.name());
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("cluster-a");
        when(metadata.listTopics("instance-a", "cluster-a", "NORMAL", "orders"))
                .thenReturn(List.of(topic));

        var result = handler.execute(new TopicListInput("instance-a", "cluster-a", "orders", "NORMAL"),
                ToolExecutionContext.of("instance-a", definition, Map.of()));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().clusterId()).isEqualTo("cluster-a");
        verify(metadata).listTopics("instance-a", "cluster-a", "NORMAL", "orders");
    }

    @Test
    void groupListShouldForwardPhysicalClusterFilter() {
        MetadataService metadata = mock(MetadataService.class);
        ConsumerGroupListToolHandler handler = new ConsumerGroupListToolHandler(metadata);
        ToolDefinition definition = new ToolCatalog(new DefaultResourceLoader())
                .getDefinition(handler.name());
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("group");
        group.setClusterId("cluster-a");
        when(metadata.listConsumerGroups("instance-a", "cluster-a", "group"))
                .thenReturn(List.of(group));

        var result = handler.execute(new GroupListInput("instance-a", "cluster-a", "group"),
                ToolExecutionContext.of("instance-a", definition, Map.of()));

        assertThat(result.items()).hasSize(1);
        assertThat(result.items().getFirst().clusterId()).isEqualTo("cluster-a");
        verify(metadata).listConsumerGroups("instance-a", "cluster-a", "group");
    }
}
