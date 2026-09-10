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
import java.util.List;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
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
    public void listTopicsPageShouldReturnSlicedResults() {
        InstanceProvider provider = mock(InstanceProvider.class);
        TopicVO topic1 = new TopicVO();
        topic1.setName("t-1");
        TopicVO topic2 = new TopicVO();
        topic2.setName("t-2");
        TopicVO topic3 = new TopicVO();
        topic3.setName("t-3");
        TopicVO topic4 = new TopicVO();
        topic4.setName("t-4");
        TopicVO topic5 = new TopicVO();
        topic5.setName("t-5");
        when(provider.listTopics("instance-a", null, null))
                .thenReturn(List.of(topic1, topic2, topic3, topic4, topic5));
        when(provider.listTopicsPage("instance-a", null, null, 2, 2)).thenCallRealMethod();

        PageResult<TopicVO> result = provider.listTopicsPage("instance-a", null, null, 2, 2);

        assertThat(result.getItems()).extracting(TopicVO::getName).containsExactly("t-3", "t-4");
        assertThat(result.getTotal()).isEqualTo(5);
        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    public void listTopicsPageShouldClampPageBeyondTotal() {
        InstanceProvider provider = mock(InstanceProvider.class);
        TopicVO topic1 = new TopicVO();
        topic1.setName("t-1");
        TopicVO topic2 = new TopicVO();
        topic2.setName("t-2");
        TopicVO topic3 = new TopicVO();
        topic3.setName("t-3");
        when(provider.listTopics("instance-a", null, null)).thenReturn(List.of(topic1, topic2, topic3));
        when(provider.listTopicsPage("instance-a", null, null, 99, 10)).thenCallRealMethod();

        PageResult<TopicVO> result = provider.listTopicsPage("instance-a", null, null, 99, 10);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    public void listConsumerGroupsPageShouldSliceAndReportTotal() {
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO group1 = new ConsumerGroupVO();
        group1.setName("g-1");
        ConsumerGroupVO group2 = new ConsumerGroupVO();
        group2.setName("g-2");
        ConsumerGroupVO group3 = new ConsumerGroupVO();
        group3.setName("g-3");
        when(provider.listConsumerGroups("instance-a", null)).thenReturn(List.of(group1, group2, group3));
        when(provider.listConsumerGroupsPage("instance-a", null, 1, 2)).thenCallRealMethod();

        PageResult<ConsumerGroupVO> result = provider.listConsumerGroupsPage("instance-a", null, 1, 2);

        assertThat(result.getItems()).extracting(ConsumerGroupVO::getName).containsExactly("g-1", "g-2");
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    public void previewResetOffsetShouldCollectOnlyMatchingTopicQueues() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getGroupProgress("instance-a", "group-a")).thenReturn(Arrays.asList(
                null,
                QueueProgressVO.builder().topic("orders").broker("broker-1").queueId(0)
                        .brokerOffset(100).consumerOffset(80).diffTotal(20).build(),
                QueueProgressVO.builder().topic("payments").broker("broker-1").queueId(0)
                        .brokerOffset(50).consumerOffset(50).diffTotal(0).build(),
                QueueProgressVO.builder().topic("orders").broker("broker-1").queueId(1)
                        .brokerOffset(200).consumerOffset(150).diffTotal(50).build()));
        when(provider.previewResetOffset("instance-a", "group-a", 1234L, "orders")).thenCallRealMethod();

        ResetConsumerOffsetPreviewVO preview =
                provider.previewResetOffset("instance-a", "group-a", 1234L, "orders");

        assertThat(preview.getQueueCount()).isEqualTo(2);
        assertThat(preview.isAllowReset()).isTrue();
        assertThat(preview.getCurrentTotalLag()).isEqualTo(70);
        assertThat(preview.isComplete()).isFalse();
        assertThat(preview.getWarningCount()).isEqualTo(1);
    }

    @Test
    public void previewResetOffsetShouldDisallowResetWithoutMatchingQueues() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getGroupProgress("instance-a", "group-a")).thenReturn(List.of(
                QueueProgressVO.builder().topic("payments").broker("broker-1").queueId(0)
                        .brokerOffset(1).consumerOffset(1).diffTotal(0).build()));
        when(provider.previewResetOffset("instance-a", "group-a", 1L, "orders")).thenCallRealMethod();

        ResetConsumerOffsetPreviewVO preview =
                provider.previewResetOffset("instance-a", "group-a", 1L, "orders");

        assertThat(preview.getQueueCount()).isEqualTo(0);
        assertThat(preview.isAllowReset()).isFalse();
        assertThat(preview.getCurrentTotalLag()).isEqualTo(0);
        assertThat(preview.getWarnings()).isNotEmpty();
    }
}
