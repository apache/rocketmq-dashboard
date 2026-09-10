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
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageDTO;
import org.apache.rocketmq.studio.instance.message.MessageQueryResult;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
    public void getTopicConsumersPageShouldSliceTheMiddleWindowTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getTopicConsumers("instance-a", "orders")).thenReturn(Arrays.asList(
                TopicConsumerVO.builder().group("group-a").build(),
                TopicConsumerVO.builder().group("group-b").build(),
                TopicConsumerVO.builder().group("group-c").build()));
        when(provider.getTopicConsumersPage("instance-a", "orders", 2, 1))
                .thenCallRealMethod();

        TopicConsumerPageVO result = provider.getTopicConsumersPage("instance-a", "orders", 2, 1);

        assertThat(result.getItems()).extracting("group").containsExactly("group-b");
        assertThat(result.getTotal()).isEqualTo(3);
    }

    @Test
    public void listTopicsPageShouldSliceAcrossWindowsTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.listTopics("instance-a", "NORMAL", "order"))
                .thenReturn(Arrays.asList(topic("t-1"), topic("t-2"), topic("t-3")));
        when(provider.listTopicsPage("instance-a", "NORMAL", "order", 1, 2))
                .thenCallRealMethod();
        when(provider.listTopicsPage("instance-a", "NORMAL", "order", 2, 2))
                .thenCallRealMethod();

        PageResult<TopicVO> first = provider.listTopicsPage("instance-a", "NORMAL", "order", 1, 2);
        PageResult<TopicVO> second = provider.listTopicsPage("instance-a", "NORMAL", "order", 2, 2);

        assertThat(first.getItems()).hasSize(2);
        assertThat(first.getTotal()).isEqualTo(3);
        assertThat(first.getPage()).isEqualTo(1);
        assertThat(second.getItems()).hasSize(1);
        assertThat(second.getPage()).isEqualTo(2);
    }

    @Test
    public void listConsumerGroupsPageShouldSliceTheRequestedWindowTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.listConsumerGroups("instance-a", "cg"))
                .thenReturn(Arrays.asList(group("cg-1"), group("cg-2"), group("cg-3"), group("cg-4")));
        when(provider.listConsumerGroupsPage("instance-a", "cg", 2, 2))
                .thenCallRealMethod();

        PageResult<ConsumerGroupVO> result =
                provider.listConsumerGroupsPage("instance-a", "cg", 2, 2);

        assertThat(result.getItems()).hasSize(2);
        assertThat(result.getTotal()).isEqualTo(4);
        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    public void previewResetOffsetShouldFilterQueuesByTopicTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getGroupProgress("instance-a", "cg-order"))
                .thenReturn(Arrays.asList(
                        progress("orders", "broker-0", 0, 100L, 80L, 20L),
                        progress("orders", "broker-1", 0, 200L, 150L, 50L),
                        progress("payments", "broker-2", 0, 90L, 90L, 0L)));
        when(provider.previewResetOffset("instance-a", "cg-order", 1234L, "orders"))
                .thenCallRealMethod();

        ResetConsumerOffsetPreviewVO preview =
                provider.previewResetOffset("instance-a", "cg-order", 1234L, "orders");

        assertThat(preview.getQueueCount()).isEqualTo(2);
        assertThat(preview.isAllowReset()).isTrue();
        assertThat(preview.getCurrentTotalLag()).isEqualTo(70L);
        assertThat(preview.getQueues()).extracting("broker")
                .containsExactly("broker-0", "broker-1");
        assertThat(preview.getWarningCount()).isEqualTo(1);
    }

    @Test
    public void previewResetOffsetShouldBlockResetWithoutMatchingQueuesTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getGroupProgress("instance-a", "cg-order"))
                .thenReturn(Arrays.asList(progress("payments", "broker-0", 0, 5L, 5L, 0L)));
        when(provider.previewResetOffset("instance-a", "cg-order", 1234L, "orders"))
                .thenCallRealMethod();

        ResetConsumerOffsetPreviewVO preview =
                provider.previewResetOffset("instance-a", "cg-order", 1234L, "orders");

        assertThat(preview.getQueueCount()).isZero();
        assertThat(preview.isAllowReset()).isFalse();
        assertThat(preview.isComplete()).isFalse();
        assertThat(preview.getWarningCount()).isEqualTo(1);
    }

    @Test
    public void queryMessagesDetailedShouldMarkTheResultCompleteTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.queryMessages("instance-a", "orders", "msg-1", null, null, 1L, 2L))
                .thenReturn(Arrays.asList(mock(MessageRecordVO.class)));
        when(provider.queryMessagesDetailed("instance-a", "orders", "msg-1", null, null, 1L, 2L))
                .thenCallRealMethod();

        MessageQueryResult result =
                provider.queryMessagesDetailed("instance-a", "orders", "msg-1", null, null, 1L, 2L);

        assertThat(result.messages()).hasSize(1);
        assertThat(result.mayBeTruncated()).isFalse();
    }

    @Test
    public void capabilitiesShouldDefaultToAnEmptySetTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.capabilities()).thenCallRealMethod();

        assertThat(provider.capabilities()).isEmpty();
    }

    @Test
    public void consumeMessageDirectlyShouldDefaultToUnsupportedTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        DirectConsumeMessageDTO request = dto("instance-a", "orders", "msg-1");
        when(provider.consumeMessageDirectly(request)).thenCallRealMethod();

        assertThatThrownBy(() -> provider.consumeMessageDirectly(request))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    public void traceLookupDefaultsShouldFailWith501Test() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getMessageTrace("instance-a", "msg-1", "orders", "CUSTOM_TRACE"))
                .thenCallRealMethod();
        when(provider.getMessageTraceByKey("instance-a", "key-1", "orders", "CUSTOM_TRACE"))
                .thenCallRealMethod();

        assertThatThrownBy(() -> provider.getMessageTrace("instance-a", "msg-1", "orders",
                "CUSTOM_TRACE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(501));
        assertThatThrownBy(() -> provider.getMessageTraceByKey("instance-a", "key-1", "orders",
                "CUSTOM_TRACE"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(501));
    }

    private static DirectConsumeMessageDTO dto(String instanceId, String topic, String msgId) {
        DirectConsumeMessageDTO dto = new DirectConsumeMessageDTO();
        dto.setInstanceId(instanceId);
        dto.setTopic(topic);
        dto.setMsgId(msgId);
        return dto;
    }

    private static TopicVO topic(String name) {
        TopicVO topic = new TopicVO();
        topic.setName(name);
        return topic;
    }

    private static ConsumerGroupVO group(String name) {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName(name);
        return group;
    }

    private static QueueProgressVO progress(String topic, String broker, int queueId,
            long brokerOffset, long consumerOffset, long diffTotal) {
        return QueueProgressVO.builder()
                .topic(topic)
                .broker(broker)
                .queueId(queueId)
                .brokerOffset(brokerOffset)
                .consumerOffset(consumerOffset)
                .diffTotal(diffTotal)
                .build();
    }
}
