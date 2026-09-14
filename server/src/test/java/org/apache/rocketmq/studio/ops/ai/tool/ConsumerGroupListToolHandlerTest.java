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

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerGroupListToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    @InjectMocks
    private ConsumerGroupListToolHandler handler;

    @Test
    void executeShouldRouteClusterToClusterScopedRead() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setClusterId("DefaultCluster");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setOnlineInstances(3);
        group.setTotalLag(42L);
        group.setSubscribedTopics(List.of("orders"));
        group.setRetryMaxTimes(16);
        when(metadataService.listConsumerGroupsPage(isNull(), eq("DefaultCluster"), eq("order"), eq(1), eq(20)))
                .thenReturn(PageResult.of(List.of(group), 1L, 1, 20));

        Object result = handler.execute(Map.of("cluster", "DefaultCluster", "search", "order"));

        Map<?, ?> page = (Map<?, ?>) result;
        assertThat(page.get("total")).isEqualTo(1L);
        List<?> items = (List<?>) page.get("items");
        assertThat(items).hasSize(1);
        Map<?, ?> row = (Map<?, ?>) items.get(0);
        assertThat(row.get("name")).isEqualTo("cg-orders");
        assertThat(row.get("subscriptionMode")).isEqualTo("Push");
        assertThat(row.get("consumeType")).isEqualTo("CLUSTERING");
        assertThat(row.get("totalLag")).isEqualTo(42L);
        verify(metadataService).listConsumerGroupsPage(isNull(), eq("DefaultCluster"), eq("order"), eq(1), eq(20));
    }
}
