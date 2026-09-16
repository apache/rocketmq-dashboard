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
package org.apache.rocketmq.studio.ops.ai.tool.handler.topic;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicQueueStatsVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.CapabilityResolver;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicDetailInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicDetailOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicQueueStatsItem;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TopicDetailToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    @Mock
    private CapabilityResolver capabilityResolver;

    @InjectMocks
    private TopicDetailToolHandler handler;

    @Test
    void detailShouldIncludeQueueStatsForRemotingCapableInstanceTest() {
        assertThat(handler.name()).isEqualTo("rmq.topic.detail");
        when(metadataService.getTopic("instance-a", null, "orders")).thenReturn(topic());
        when(metadataService.getTopicConsumers("instance-a", "orders")).thenReturn(List.of());
        when(metadataService.getTopicRoutes("instance-a", "orders")).thenReturn(List.of());
        when(capabilityResolver.resolve("instance-a")).thenReturn(Set.of("REMOTING", "TOPIC_MANAGEMENT"));
        when(metadataService.getTopicStats("instance-a", "orders")).thenReturn(List.of(
                TopicQueueStatsVO.builder()
                        .brokerName("broker-a").queueId(0)
                        .minOffset(1).maxOffset(9).lastUpdateTimestamp(1700000000000L)
                        .build()));

        TopicDetailOutput output = handler.execute(input(), context());

        assertThat(output.name()).isEqualTo("orders");
        assertThat(output.instanceId()).isEqualTo("instance-a");
        assertThat(output.queueStats()).containsExactly(
                new TopicQueueStatsItem("broker-a", 0, 1L, 9L, 1700000000000L));
    }

    @Test
    void detailShouldOmitQueueStatsWithoutRemotingCapabilityTest() {
        when(metadataService.getTopic("instance-a", null, "orders")).thenReturn(topic());
        when(metadataService.getTopicConsumers("instance-a", "orders")).thenReturn(List.of());
        when(metadataService.getTopicRoutes("instance-a", "orders")).thenReturn(List.of());
        when(capabilityResolver.resolve("instance-a")).thenReturn(Set.of("TOPIC_MANAGEMENT", "CLOUD_API"));

        TopicDetailOutput output = handler.execute(input(), context());

        assertThat(output.queueStats()).isNull();
        assertThat(output.name()).isEqualTo("orders");
        verify(metadataService, never()).getTopicStats("instance-a", "orders");
    }

    @Test
    void detailShouldOmitQueueStatsWhenStatsCollectionFailsTest() {
        when(metadataService.getTopic("instance-a", null, "orders")).thenReturn(topic());
        when(metadataService.getTopicConsumers("instance-a", "orders")).thenReturn(List.of());
        when(metadataService.getTopicRoutes("instance-a", "orders")).thenReturn(List.of());
        when(capabilityResolver.resolve("instance-a")).thenReturn(Set.of("REMOTING"));
        when(metadataService.getTopicStats("instance-a", "orders"))
                .thenThrow(new BusinessException(502, "Failed to get stats for topic orders: broker down"));

        TopicDetailOutput output = handler.execute(input(), context());

        assertThat(output.queueStats()).isNull();
        assertThat(output.writeQueues()).isEqualTo(8);
    }

    @Test
    void detailShouldOmitQueueStatsWhenRouteMissingReturnsEmptyStatsTest() {
        when(metadataService.getTopic("instance-a", null, "orders")).thenReturn(topic());
        when(metadataService.getTopicConsumers("instance-a", "orders")).thenReturn(List.of());
        when(metadataService.getTopicRoutes("instance-a", "orders")).thenReturn(List.of());
        when(capabilityResolver.resolve("instance-a")).thenReturn(Set.of("REMOTING"));
        when(metadataService.getTopicStats("instance-a", "orders")).thenReturn(List.of());

        TopicDetailOutput output = handler.execute(input(), context());

        assertThat(output.queueStats()).isNull();
    }

    private static TopicDetailInput input() {
        return new TopicDetailInput("untrusted-instance", "orders");
    }

    private static ToolExecutionContext context() {
        return ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "instance-a", "topicName", "orders"));
    }

    private static TopicVO topic() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("cluster-a");
        topic.setInstanceId("instance-a");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(8);
        topic.setReadQueues(8);
        topic.setPerm(TopicPerm.RW);
        return topic;
    }
}
