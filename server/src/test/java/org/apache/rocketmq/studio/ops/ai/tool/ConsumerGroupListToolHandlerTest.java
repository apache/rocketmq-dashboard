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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerGroupListToolHandlerTest {

    @Test
    void executeShouldDelegateAndProjectGroups() {
        MetadataService service = mock(MetadataService.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setNamespace("prod");
        group.setClusterId("cluster-a");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setOnlineInstances(3);
        group.setTotalLag(42);
        group.setSubscribedTopics(new ArrayList<>(List.of("orders")));
        group.setRetryMaxTimes(16);
        PageResult<ConsumerGroupVO> page = PageResult.of(List.of(group), 1, 1, 20);
        when(service.listConsumerGroupsPage("cluster-a", null, null, 1, 20)).thenReturn(page);

        Object output = new ConsumerGroupListToolHandler(service).execute(Map.of("cluster", "cluster-a"));

        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) output;
        Map<?, ?> row = (Map<?, ?>) ((List<?>) result.get("items")).get(0);
        assertThat(row.get("name")).isEqualTo("cg-orders");
        assertThat(row.get("subscriptionMode")).isEqualTo("Push");
        assertThat(row.get("consumeType")).isEqualTo("CLUSTERING");
        assertThat(row.get("onlineInstances")).isEqualTo(3);
        assertThat(row.get("totalLag")).isEqualTo(42L);
        assertThat(row.get("subscribedTopics")).isEqualTo(List.of("orders"));
        assertThat(row.get("retryMaxTimes")).isEqualTo(16);
        verify(service).listConsumerGroupsPage("cluster-a", null, null, 1, 20);
    }

    @Test
    void executeShouldForwardSearchAndPaging() {
        MetadataService service = mock(MetadataService.class);
        PageResult<ConsumerGroupVO> page = PageResult.of(List.of(), 0, 3, 25);
        when(service.listConsumerGroupsPage("cluster-a", null, "orders", 3, 25)).thenReturn(page);

        new ConsumerGroupListToolHandler(service).execute(Map.of(
                "cluster", "cluster-a", "search", "orders", "page", 3, "pageSize", 25));

        verify(service).listConsumerGroupsPage("cluster-a", null, "orders", 3, 25);
    }

    @Test
    void handlerNameShouldBeRmqGroupList() {
        assertThat(new ConsumerGroupListToolHandler(mock(MetadataService.class)).name())
                .isEqualTo("rmq.group.list");
    }

    @Test
    void missingGroupNameIsRejected() {
        MetadataService service = mock(MetadataService.class);
        PageResult<ConsumerGroupVO> page = PageResult.of(List.of(new ConsumerGroupVO()), 1, 1, 20);
        when(service.listConsumerGroupsPage("cluster-a", null, null, 1, 20)).thenReturn(page);

        assertThatThrownBy(() -> new ConsumerGroupListToolHandler(service)
                .execute(Map.of("cluster", "cluster-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Consumer group name is unavailable");
    }

    @Test
    void nullModeAndConsumeTypeAreRejected() {
        MetadataService service = mock(MetadataService.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        PageResult<ConsumerGroupVO> page = PageResult.of(List.of(group), 1, 1, 20);
        when(service.listConsumerGroupsPage("cluster-a", null, null, 1, 20)).thenReturn(page);

        assertThatThrownBy(() -> new ConsumerGroupListToolHandler(service)
                .execute(Map.of("cluster", "cluster-a")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("subscriptionMode is unavailable: cg-orders");
    }

    @Test
    void absentOptionalFieldsProjectAsBlanksAndEmptyTopics() {
        MetadataService service = mock(MetadataService.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.BROADCASTING);
        PageResult<ConsumerGroupVO> page = PageResult.of(List.of(group), 1, 1, 20);
        when(service.listConsumerGroupsPage("cluster-a", null, null, 1, 20)).thenReturn(page);

        Object output = new ConsumerGroupListToolHandler(service).execute(Map.of("cluster", "cluster-a"));

        Map<?, ?> row = (Map<?, ?>) ((List<?>) ((Map<?, ?>) output).get("items")).get(0);
        assertThat(row.get("namespace")).isEqualTo("");
        assertThat(row.get("clusterId")).isEqualTo("");
        assertThat(row.get("subscribedTopics")).isEqualTo(List.of());
        assertThat(row.get("totalLag")).isEqualTo(0L);
    }
}
