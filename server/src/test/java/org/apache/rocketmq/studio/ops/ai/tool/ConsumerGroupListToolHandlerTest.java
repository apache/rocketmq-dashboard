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

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link ConsumerGroupListToolHandler}: the list-style tool delegates to the
 * metadata service with its filter/paging inputs and projects each consumer group row.
 */
@ExtendWith(MockitoExtension.class)
class ConsumerGroupListToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    @InjectMocks
    private ConsumerGroupListToolHandler handler;

    private static ConsumerGroupVO group(String name) {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName(name);
        group.setClusterId("c1");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setOnlineInstances(2);
        group.setTotalLag(500L);
        group.setRetryMaxTimes(16);
        return group;
    }

    @Test
    void reportsItsToolName() {
        assertThat(handler.name()).isEqualTo("rmq.group.list");
    }

    @Test
    @SuppressWarnings("unchecked")
    void delegatesAndProjectsTheGroupRows() {
        ConsumerGroupVO group = group("orders");
        List<String> topics = new ArrayList<>(List.of("orders-topic"));
        group.setSubscribedTopics(topics);
        when(metadataService.listConsumerGroupsPage("c1", null, "orders", 1, 20))
                .thenReturn(PageResult.of(List.of(group), 1L, 1, 20));

        Map<String, Object> result = (Map<String, Object>) handler.execute(
                Map.of("cluster", "c1", "search", "orders"));

        Map<String, Object> row = (Map<String, Object>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("name")).isEqualTo("orders");
        assertThat(row.get("clusterId")).isEqualTo("c1");
        assertThat(row.get("subscriptionMode")).isEqualTo("Push");
        assertThat(row.get("consumeType")).isEqualTo("CLUSTERING");
        assertThat(row.get("onlineInstances")).isEqualTo(2);
        assertThat(row.get("totalLag")).isEqualTo(500L);
        assertThat(row.get("retryMaxTimes")).isEqualTo(16);
        assertThat(row.get("subscribedTopics")).isEqualTo(List.of("orders-topic"));
        assertThat(result.get("total")).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void defaultsThePageWindowAndCopiesTopicListsDefensively() {
        ConsumerGroupVO group = group("orders");
        List<String> topics = new ArrayList<>(List.of("orders-topic"));
        group.setSubscribedTopics(topics);
        when(metadataService.listConsumerGroupsPage("c1", null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(group), 0L, 1, 20));

        Map<String, Object> result = (Map<String, Object>) handler.execute(Map.of("cluster", "c1"));

        topics.add("mutated");
        Map<String, Object> row = (Map<String, Object>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("subscribedTopics")).isEqualTo(List.of("orders-topic"));
        verify(metadataService).listConsumerGroupsPage(eq("c1"), isNull(), isNull(), eq(1), eq(20));
    }

    @Test
    void rejectsGroupsMissingRequiredProjectionFields() {
        ConsumerGroupVO group = group("no-mode");
        group.setSubscriptionMode(null);
        when(metadataService.listConsumerGroupsPage("c1", null, null, 1, 20))
                .thenReturn(PageResult.of(List.of(group), 1L, 1, 20));

        assertThatThrownBy(() -> handler.execute(Map.of("cluster", "c1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subscriptionMode");
    }
}
