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
package org.apache.rocketmq.studio.provider;

import java.util.Arrays;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstanceProviderTest {

    @Test
    public void getTopicConsumersPageShouldHandleLargePageNumberTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getTopicConsumers("instance-a", "orders")).thenReturn(Arrays.asList(
                TopicConsumerVO.builder().group("group-a").build(),
                TopicConsumerVO.builder().group("group-b").build()));
        when(provider.getTopicConsumersPage("instance-a", "orders", Integer.MAX_VALUE, 100))
                .thenCallRealMethod();

        TopicConsumerPageVO result = provider.getTopicConsumersPage(
                "instance-a", "orders", Integer.MAX_VALUE, 100);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    public void listTopicsPageShouldReturnEmptyPageForNegativePageSizeTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        when(provider.listTopics("instance-a", null, null)).thenReturn(Arrays.asList(topic));
        when(provider.listTopicsPage("instance-a", null, null, 1, -1))
                .thenCallRealMethod();

        PageResult<TopicVO> result = provider.listTopicsPage("instance-a", null, null, 1, -1);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    public void getTopicConsumersPageShouldReturnEmptyPageForNegativePageSizeTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getTopicConsumers("instance-a", "orders")).thenReturn(Arrays.asList(
                TopicConsumerVO.builder().group("group-a").build()));
        when(provider.getTopicConsumersPage("instance-a", "orders", 1, -1))
                .thenCallRealMethod();

        TopicConsumerPageVO result = provider.getTopicConsumersPage("instance-a", "orders", 1, -1);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    public void listConsumerGroupsPageShouldReturnEmptyPageForNegativePageSizeTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("group-a");
        when(provider.listConsumerGroups("instance-a", null)).thenReturn(Arrays.asList(group));
        when(provider.listConsumerGroupsPage("instance-a", null, 1, -1))
                .thenCallRealMethod();

        PageResult<ConsumerGroupVO> result = provider.listConsumerGroupsPage("instance-a", null, 1, -1);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(1);
    }
}
