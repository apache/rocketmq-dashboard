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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageDTO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageResultVO;
import org.apache.rocketmq.studio.instance.message.MessageProvider;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApacheInstanceProviderTest {

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private AdminClient adminClient;

    @Mock
    private MessageProvider messageProvider;

    @Mock
    private InstanceRepository instanceRepository;

    @InjectMocks
    private ApacheInstanceProvider provider;

    @Test
    void capabilitiesShouldIncludeApacheOnlyOperationsTest() {
        assertThat(provider.capabilities()).contains(
                InstanceCapability.TOPIC_MANAGEMENT,
                InstanceCapability.CONSUMER_GROUP_MANAGEMENT,
                InstanceCapability.MESSAGE_QUERY,
                InstanceCapability.MESSAGE_TRACE,
                InstanceCapability.ACL_MANAGEMENT,
                InstanceCapability.DLQ_MANAGEMENT);
    }

    @Test
    void vendorShouldBeApacheTest() {
        assertThat(provider.vendor()).isEqualTo(InstanceVendor.APACHE);
    }

    @Test
    void countTopicsShouldDelegateToRepositoryTest() {
        InstanceVO instance = InstanceVO.builder().name("inst-1").build();
        instance.setId(1L);
        when(instanceRepository.findByIdentifier("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.countTopicsByInstance("inst-1")).thenReturn(3L);

        assertThat(provider.countTopics("inst-1")).isEqualTo(3);
    }

    @Test
    void countGroupsShouldDelegateToRepositoryTest() {
        InstanceVO instance = InstanceVO.builder().name("inst-1").build();
        instance.setId(1L);
        when(instanceRepository.findByIdentifier("inst-1")).thenReturn(Optional.of(instance));
        when(instanceRepository.countGroupsByInstance("inst-1")).thenReturn(2L);

        assertThat(provider.countGroups("inst-1")).isEqualTo(2);
    }

    @Test
    void listTopicsShouldPassTheSelectedInstanceToMetadataProvider() {
        when(metadataProvider.listTopics("inst-1", null, "FIFO", "orders")).thenReturn(java.util.List.of());

        assertThat(provider.listTopics("inst-1", "FIFO", "orders")).isEmpty();

        verify(metadataProvider).listTopics("inst-1", null, "FIFO", "orders");
    }

    @Test
    void listConsumerGroupsShouldPassTheSelectedInstanceToMetadataProvider() {
        when(metadataProvider.listConsumerGroups("inst-1", null, "orders")).thenReturn(java.util.List.of());

        assertThat(provider.listConsumerGroups("inst-1", "orders")).isEmpty();

        verify(metadataProvider).listConsumerGroups("inst-1", null, "orders");
    }

    @Test
    void listConsumerGroupsPageShouldRouteThroughDatabasePaginationTest() {
        PageResult<ConsumerGroupVO> page = PageResult.of(java.util.List.of(), 0, 1, 20);
        when(metadataProvider.listConsumerGroupsPage("inst-1", null, "orders", 1, 20)).thenReturn(page);

        assertThat(provider.listConsumerGroupsPage("inst-1", "orders", 1, 20)).isSameAs(page);

        verify(metadataProvider).listConsumerGroupsPage("inst-1", null, "orders", 1, 20);
    }

    @Test
    void listTopicsPageShouldDelegateToMetadataProviderTest() {
        PageResult<TopicVO> page = PageResult.of(java.util.List.of(), 0, 1, 20);
        when(metadataProvider.listTopicsPage("inst-1", null, "FIFO", "orders", 1, 20)).thenReturn(page);

        assertThat(provider.listTopicsPage("inst-1", "FIFO", "orders", 1, 20)).isSameAs(page);

        verify(metadataProvider).listTopicsPage("inst-1", null, "FIFO", "orders", 1, 20);
    }

    @Test
    void topicWritesShouldDelegateToAdminClientTest() {
        TopicVO created = new TopicVO();
        created.setName("orders");
        TopicVO updated = new TopicVO();
        updated.setName("orders-updated");
        when(adminClient.createTopic(created)).thenReturn(created);
        when(adminClient.updateTopic(updated)).thenReturn(updated);

        assertThat(provider.createTopic("inst-1", created)).isSameAs(created);
        assertThat(provider.updateTopic("inst-1", updated)).isSameAs(updated);

        verify(adminClient).createTopic(created);
        verify(adminClient).updateTopic(updated);

        provider.deleteTopic("inst-1", "orders");
        verify(adminClient).deleteTopic("inst-1", "orders");
    }

    @Test
    void topicConsumersShouldDelegateToMetadataProviderTest() {
        TopicConsumerVO consumer = TopicConsumerVO.builder().group("orders-group").build();
        TopicConsumerPageVO page = TopicConsumerPageVO.builder()
                .items(java.util.List.of(consumer))
                .build();
        when(metadataProvider.getTopicConsumers("inst-1", "orders")).thenReturn(java.util.List.of(consumer));
        when(metadataProvider.getTopicConsumersPage("inst-1", "orders", 1, 20)).thenReturn(page);

        assertThat(provider.getTopicConsumers("inst-1", "orders")).containsExactly(consumer);
        assertThat(provider.getTopicConsumersPage("inst-1", "orders", 1, 20)).isSameAs(page);
    }

    @Test
    void consumerGroupWritesShouldDelegateToAdminClientTest() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders-group");
        when(adminClient.createConsumerGroup(group)).thenReturn(group);

        assertThat(provider.createConsumerGroup("inst-1", group)).isSameAs(group);
        verify(adminClient).createConsumerGroup(group);

        provider.deleteConsumerGroup("inst-1", "orders-group");
        verify(adminClient).deleteConsumerGroup("inst-1", "orders-group");
    }

    @Test
    void groupProgressAndSubscriptionsShouldDelegateToMetadataProviderTest() {
        QueueProgressVO progress = QueueProgressVO.builder().broker("broker-1").build();
        SubscriptionEntryVO subscription = SubscriptionEntryVO.builder()
                .topic("orders").build();
        when(metadataProvider.getGroupProgress("inst-1", "orders-group"))
                .thenReturn(java.util.List.of(progress));
        when(metadataProvider.getGroupSubscriptions("inst-1", "orders-group"))
                .thenReturn(java.util.List.of(subscription));

        assertThat(provider.getGroupProgress("inst-1", "orders-group")).containsExactly(progress);
        assertThat(provider.getGroupSubscriptions("inst-1", "orders-group")).containsExactly(subscription);
    }

    @Test
    void offsetResetShouldDelegateToAdminClientTest() {
        ResetConsumerOffsetPreviewVO preview = ResetConsumerOffsetPreviewVO.builder()
                .timestamp(1700000000000L)
                .build();
        when(adminClient.previewResetOffset("inst-1", "orders-group", 1700000000000L, "orders"))
                .thenReturn(preview);

        assertThat(provider.previewResetOffset("inst-1", "orders-group", 1700000000000L, "orders"))
                .isSameAs(preview);
        verify(adminClient).previewResetOffset("inst-1", "orders-group", 1700000000000L, "orders");

        provider.resetOffset("inst-1", "orders-group", 1700000000000L, "orders");
        verify(adminClient).resetOffset("inst-1", "orders-group", 1700000000000L, "orders");
    }

    @Test
    void messageQueriesShouldDelegateToMessageProviderTest() {
        MessageRecordVO message = MessageRecordVO.builder().msgId("msg-1").build();
        TraceRecordVO trace = TraceRecordVO.builder().build();
        DirectConsumeMessageDTO request = new DirectConsumeMessageDTO();
        request.setInstanceId("inst-1");
        DirectConsumeMessageResultVO direct = DirectConsumeMessageResultVO.builder().build();
        when(messageProvider.queryMessages("inst-1", "orders", "msg-1", "tag", "key", 1L, 2L))
                .thenReturn(java.util.List.of(message));
        when(messageProvider.getMessageTrace("inst-1", "msg-1", "orders")).thenReturn(trace);
        when(messageProvider.getMessageTrace("inst-1", "msg-1", "orders", "rmq_sys_TRACE_DATA"))
                .thenReturn(trace);
        when(messageProvider.getMessageTraceByKey("inst-1", "key", "orders", "rmq_sys_TRACE_DATA"))
                .thenReturn(trace);
        when(messageProvider.consumeMessageDirectly(request)).thenReturn(direct);

        assertThat(provider.queryMessages("inst-1", "orders", "msg-1", "tag", "key", 1L, 2L))
                .containsExactly(message);
        assertThat(provider.getMessageTrace("inst-1", "msg-1", "orders")).isSameAs(trace);
        assertThat(provider.getMessageTrace("inst-1", "msg-1", "orders", "rmq_sys_TRACE_DATA"))
                .isSameAs(trace);
        assertThat(provider.getMessageTraceByKey("inst-1", "key", "orders", "rmq_sys_TRACE_DATA"))
                .isSameAs(trace);
        assertThat(provider.consumeMessageDirectly(request)).isSameAs(direct);
    }
}
