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

package org.apache.rocketmq.studio.instance.topic;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.provider.apache.MetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetadataServiceTest {

    @Mock
    private MetadataProvider metadataProvider;

    @Mock
    private AdminClient adminClient;

    @Mock
    private InstanceProviderRegistry providerRegistry;

    @Mock
    private InstanceProvider apacheProvider;

    @Mock
    private org.apache.rocketmq.studio.instance.InstanceRepository instanceRepository;

    @InjectMocks
    private MetadataService metadataService;

    @BeforeEach
    void routeBlankInstanceIdsToApacheProvider() {
        lenient().when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(apacheProvider);
        lenient().when(apacheProvider.vendor()).thenReturn(InstanceVendor.APACHE);
        lenient().when(providerRegistry.byInstanceId("instance-a")).thenReturn(java.util.Optional.of(apacheProvider));
        lenient().when(instanceRepository.findByIdentifier(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(java.util.Optional.empty());
    }

    @Test
    void listTopicsShouldReturnTopicsFromProvider() {
        TopicVO topic = new TopicVO();
        topic.setName("test-topic");
        topic.setWriteQueues(8);
        topic.setReadQueues(8);

        when(metadataProvider.listTopics("cluster-1", "NORMAL", null)).thenReturn(List.of(topic));

        List<TopicVO> result = metadataService.listTopics("cluster-1", "NORMAL", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-topic");
        verify(metadataProvider).listTopics("cluster-1", "NORMAL", null);
    }

    @Test
    void listTopicsShouldReturnEmptyWhenNone() {
        when(apacheProvider.listTopics(null, null, "nonexistent")).thenReturn(List.of());

        List<TopicVO> result = metadataService.listTopics(null, null, "nonexistent");

        assertThat(result).isEmpty();
    }

    @Test
    void listTopicsShouldNormalizeFiltersBeforeQueryingProvider() {
        TopicVO topic = new TopicVO();
        topic.setName("order-topic");
        when(metadataProvider.listTopics("cluster-1", "FIFO", "order")).thenReturn(List.of(topic));

        List<TopicVO> result = metadataService.listTopics(" cluster-1 ", " FIFO ", " order ");

        assertThat(result).containsExactly(topic);
        verify(metadataProvider).listTopics("cluster-1", "FIFO", "order");
    }

    @Test
    void listTopicsShouldTreatBlankFiltersAsUnspecified() {
        when(apacheProvider.listTopics(null, null, null)).thenReturn(List.of());

        List<TopicVO> result = metadataService.listTopics(" ", "\t", "");

        assertThat(result).isEmpty();
        verify(apacheProvider).listTopics(null, null, null);
    }

    @Test
    void topicWriteOperationsShouldRejectNullRequest() {
        assertThatThrownBy(() -> metadataService.createTopic(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Topic request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> metadataService.updateTopic(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Topic request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));
        assertThatThrownBy(() -> metadataService.sendMessage(null))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Topic send message request is required")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(400));

        verifyNoInteractions(adminClient);
    }

    @Test
    void createTopicShouldDelegateToApacheProvider() {
        TopicVO input = new TopicVO();
        input.setName("new-topic");
        input.setWriteQueues(8);
        input.setReadQueues(8);

        TopicVO created = new TopicVO();
        created.setName("new-topic");
        created.setWriteQueues(8);
        created.setReadQueues(8);

        when(apacheProvider.createTopic(any(), any(TopicVO.class))).thenReturn(created);

        TopicVO result = metadataService.createTopic(input);

        assertThat(result.getName()).isEqualTo("new-topic");
        verify(apacheProvider).createTopic(null, input);
    }

    @Test
    void deleteTopicShouldDelegateToApacheProvider() {
        metadataService.deleteTopic("topic-to-delete");

        verify(apacheProvider).deleteTopic(null, "topic-to-delete");
    }

    @Test
    void deleteTopicShouldTrimTopicNameBeforeProviderResolution() {
        metadataService.deleteTopic("instance-a", "  topic-to-delete  ");

        verify(apacheProvider).deleteTopic("instance-a", "topic-to-delete");
    }

    @Test
    void deleteTopicShouldRejectBlankNameBeforeProviderResolution() {
        assertThatThrownBy(() -> metadataService.deleteTopic("instance-a", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic name is required");

        verifyNoInteractions(apacheProvider);
    }

    @Test
    void topicRuntimeDiagnosticsShouldDelegateWithSelectedInstance() {
        BrokerRouteVO route = BrokerRouteVO.builder().brokerName("broker-a").build();
        TopicConsumerVO consumer = TopicConsumerVO.builder().group("cg-orders").build();
        when(metadataProvider.getTopicRoutes("instance-a", "orders")).thenReturn(List.of(route));
        when(apacheProvider.getTopicConsumers("instance-a", "orders")).thenReturn(List.of(consumer));

        assertThat(metadataService.getTopicRoutes("instance-a", "orders")).containsExactly(route);
        assertThat(metadataService.getTopicConsumers("instance-a", "orders")).containsExactly(consumer);

        verify(metadataProvider).getTopicRoutes("instance-a", "orders");
        verify(apacheProvider).getTopicConsumers("instance-a", "orders");
    }

    @Test
    void topicConsumerPageShouldDelegateWithSelectedInstance() {
        TopicConsumerPageVO page = TopicConsumerPageVO.builder()
                .items(List.of()).total(3).page(1).pageSize(20).build();
        when(apacheProvider.getTopicConsumersPage("instance-a", "orders", 1, 20)).thenReturn(page);

        assertThat(metadataService.getTopicConsumersPage("instance-a", "orders", 1, 20)).isSameAs(page);

        verify(apacheProvider).getTopicConsumersPage("instance-a", "orders", 1, 20);
    }

    @Test
    void runtimeDiagnosticsShouldRejectBlankTopicAndGroupNamesBeforeProviderResolution() {
        assertThatThrownBy(() -> metadataService.getTopicRoutes("instance-a", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic name is required");
        assertThatThrownBy(() -> metadataService.getTopicConsumers("instance-a", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic name is required");
        assertThatThrownBy(() -> metadataService.getConsumerGroup("instance-a", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("consumer group name is required");
        assertThatThrownBy(() -> metadataService.getGroupProgress("instance-a", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("consumer group name is required");
        assertThatThrownBy(() -> metadataService.getGroupSubscriptions("instance-a", " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("consumer group name is required");

        verifyNoInteractions(metadataProvider, adminClient, providerRegistry, apacheProvider);
    }

    @Test
    void sendMessageShouldReturnResult() {
        SendMessageDTO request = SendMessageDTO.builder()
                .topic("test-topic")
                .tag("TagA")
                .body("hello")
                .build();

        SendMessageVO expectedResult = SendMessageVO.builder()
                .msgId("msg-001")
                .sendTime(System.currentTimeMillis())
                .offsetMsgId("offset-001")
                .build();

        when(adminClient.sendMessage(request)).thenReturn(expectedResult);

        SendMessageVO result = metadataService.sendMessage(request);

        assertThat(result.getMsgId()).isEqualTo("msg-001");
        assertThat(result.getOffsetMsgId()).isEqualTo("offset-001");
        verify(adminClient).sendMessage(request);
    }

    @Test
    void listConsumerGroupsShouldReturnGroupsFromProvider() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("test-group");

        when(metadataProvider.listConsumerGroups("cluster-1", null)).thenReturn(List.of(group));

        List<ConsumerGroupVO> result = metadataService.listConsumerGroups("cluster-1", null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("test-group");
        verify(metadataProvider).listConsumerGroups("cluster-1", null);
    }

    @Test
    void listConsumerGroupsShouldPassSearchFilter() {
        when(apacheProvider.listConsumerGroups(null, "order")).thenReturn(List.of());

        List<ConsumerGroupVO> result = metadataService.listConsumerGroups(null, "order");

        assertThat(result).isEmpty();
        verify(apacheProvider).listConsumerGroups(null, "order");
    }

    @Test
    void listConsumerGroupsShouldNormalizeFiltersBeforeQueryingProvider() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-order");
        when(metadataProvider.listConsumerGroups("cluster-1", "order")).thenReturn(List.of(group));

        List<ConsumerGroupVO> result = metadataService.listConsumerGroups(" cluster-1 ", " order ");

        assertThat(result).containsExactly(group);
        verify(metadataProvider).listConsumerGroups("cluster-1", "order");
    }

    @Test
    void listConsumerGroupsPageShouldPaginateFromOneBasedIndexes() {
        ConsumerGroupVO first = new ConsumerGroupVO();
        first.setName("cg-a");
        ConsumerGroupVO second = new ConsumerGroupVO();
        second.setName("cg-b");
        ConsumerGroupVO third = new ConsumerGroupVO();
        third.setName("cg-c");
        when(apacheProvider.listConsumerGroups("instance-a", "order"))
                .thenReturn(List.of(first, second, third));

        PageResult<ConsumerGroupVO> result =
                metadataService.listConsumerGroupsPage("instance-a", null, "order", 2, 2);

        assertThat(result.getItems()).containsExactly(third);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(2);
        verify(apacheProvider).listConsumerGroups("instance-a", "order");
    }

    @Test
    void listConsumerGroupsPageShouldReturnEmptyItemsWhenPageStartsPastFilteredTotal() {
        ConsumerGroupVO first = new ConsumerGroupVO();
        first.setName("cg-a");
        when(metadataProvider.listConsumerGroups("cluster-1", "order")).thenReturn(List.of(first));

        PageResult<ConsumerGroupVO> result =
                metadataService.listConsumerGroupsPage(null, "cluster-1", "order", 2, 1);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(1);
        verify(metadataProvider).listConsumerGroups("cluster-1", "order");
    }

    @Test
    void listConsumerGroupsPageShouldRejectInvalidPaginationBounds() {
        assertThatThrownBy(() -> metadataService.listConsumerGroupsPage(null, "cluster-1", null, 0, 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("page must be greater than zero");
        assertThatThrownBy(() -> metadataService.listConsumerGroupsPage(null, "cluster-1", null, 1, 0))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be between 1 and 100");
        assertThatThrownBy(() -> metadataService.listConsumerGroupsPage(null, "cluster-1", null, 1, 101))
                .isInstanceOf(BusinessException.class)
                .hasMessage("pageSize must be between 1 and 100");
    }

}
