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

import org.apache.rocketmq.studio.audit.OperationAuditService;
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
import static org.mockito.Mockito.doThrow;
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
    private InstanceProvider cloudProvider;

    @Mock
    private org.apache.rocketmq.studio.instance.InstanceRepository instanceRepository;

    @Mock
    private OperationAuditService operationAuditService;

    @InjectMocks
    private MetadataService metadataService;

    @BeforeEach
    void routeBlankInstanceIdsToApacheProvider() {
        lenient().when(providerRegistry.forVendor(InstanceVendor.APACHE)).thenReturn(apacheProvider);
        lenient().when(apacheProvider.vendor()).thenReturn(InstanceVendor.APACHE);
        lenient().when(cloudProvider.vendor()).thenReturn(InstanceVendor.TENCENT);
        lenient().when(providerRegistry.byInstanceId("instance-a")).thenReturn(java.util.Optional.of(apacheProvider));
        lenient().when(providerRegistry.byInstanceId("cloud-instance")).thenReturn(java.util.Optional.of(cloudProvider));
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
        verifyNoInteractions(operationAuditService);
    }

    @Test
    void apacheTopicWriteOperationsShouldNotDuplicateProviderAudit() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setInstanceId("instance-a");
        topic.setWriteQueues(4);
        topic.setReadQueues(4);
        when(apacheProvider.updateTopic("instance-a", topic)).thenReturn(topic);
        SendMessageDTO message = SendMessageDTO.builder()
                .instanceId("instance-a")
                .topic("orders")
                .tag("TagA")
                .key("order-1")
                .body("hello")
                .build();
        when(adminClient.sendMessage(message)).thenReturn(SendMessageVO.builder().msgId("msg-1").build());

        metadataService.updateTopic(topic);
        metadataService.deleteTopic("instance-a", " orders ");
        metadataService.sendMessage(message);

        verifyNoInteractions(operationAuditService);
    }

    @Test
    void cloudTopicWriteOperationsShouldRecordServiceBoundaryAudit() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setInstanceId("cloud-instance");
        topic.setWriteQueues(4);
        topic.setReadQueues(4);
        when(cloudProvider.createTopic("cloud-instance", topic)).thenReturn(topic);
        when(cloudProvider.updateTopic("cloud-instance", topic)).thenReturn(topic);

        metadataService.createTopic(topic);
        metadataService.updateTopic(topic);
        metadataService.deleteTopic("cloud-instance", " orders ");

        verify(operationAuditService).record("CREATE_TOPIC", "TOPIC", "orders", "cloud-instance",
                "type=-, writeQueues=4, readQueues=4, perm=-", "SUCCESS", null);
        verify(operationAuditService).record("UPDATE_TOPIC", "TOPIC", "orders", "cloud-instance",
                "type=-, writeQueues=4, readQueues=4, perm=-", "SUCCESS", null);
        verify(operationAuditService).record("DELETE_TOPIC", "TOPIC", "orders", "cloud-instance",
                null, "SUCCESS", null);
    }

    @Test
    void auditFailureShouldNotAbortCloudMetadataOperation() {
        doThrow(new RuntimeException("audit unavailable")).when(operationAuditService)
                .record("DELETE_TOPIC", "TOPIC", "orders", "cloud-instance", null, "SUCCESS", null);

        metadataService.deleteTopic("cloud-instance", "orders");

        verify(cloudProvider).deleteTopic("cloud-instance", "orders");
    }

    @Test
    void failedCloudMetadataOperationShouldRecordFailedAuditTest() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setInstanceId("cloud-instance");
        topic.setWriteQueues(4);
        topic.setReadQueues(4);
        when(cloudProvider.createTopic("cloud-instance", topic))
                .thenThrow(new BusinessException(502, "open api unavailable"));

        assertThatThrownBy(() -> metadataService.createTopic(topic))
                .isInstanceOf(BusinessException.class);

        verify(operationAuditService).record("CREATE_TOPIC", "TOPIC", "orders", "cloud-instance",
                "type=-, writeQueues=4, readQueues=4, perm=-", "FAILED", "open api unavailable");
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
        verifyNoInteractions(operationAuditService);
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
        ConsumerGroupVO third = new ConsumerGroupVO();
        third.setName("cg-c");
        when(apacheProvider.listConsumerGroupsPage("instance-a", "order", 2, 2))
                .thenReturn(PageResult.of(List.of(third), 3, 2, 2));

        PageResult<ConsumerGroupVO> result =
                metadataService.listConsumerGroupsPage("instance-a", null, "order", 2, 2);

        assertThat(result.getItems()).containsExactly(third);
        assertThat(result.getTotal()).isEqualTo(3);
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(2);
        verify(apacheProvider).listConsumerGroupsPage("instance-a", "order", 2, 2);
        verify(apacheProvider, org.mockito.Mockito.never()).listConsumerGroups("instance-a", "order");
    }

    @Test
    void listConsumerGroupsPageShouldReturnEmptyItemsWhenPageStartsPastFilteredTotal() {
        when(metadataProvider.listConsumerGroupsPage("cluster-1", "order", 2, 1))
                .thenReturn(PageResult.empty(2, 1));

        PageResult<ConsumerGroupVO> result =
                metadataService.listConsumerGroupsPage(null, "cluster-1", "order", 2, 1);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isZero();
        assertThat(result.getPage()).isEqualTo(2);
        assertThat(result.getSize()).isEqualTo(1);
        verify(metadataProvider).listConsumerGroupsPage("cluster-1", "order", 2, 1);
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

    @Test
    void refreshConsumerGroupShouldExactMatchWithinProviderSearchResults() {
        ConsumerGroupVO similar = new ConsumerGroupVO();
        similar.setName("cg-order-archive");
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-order");
        when(apacheProvider.listConsumerGroups("instance-a", "cg-order"))
                .thenReturn(List.of(similar, group));

        ConsumerGroupVO result = metadataService.refreshConsumerGroup("instance-a", " cg-order ");

        assertThat(result).isSameAs(group);
        verify(apacheProvider).listConsumerGroups("instance-a", "cg-order");
    }

    @Test
    void refreshConsumerGroupShouldReturnNullWhenGroupMissingInsteadOfError() {
        when(apacheProvider.listConsumerGroups("instance-a", "cg-gone")).thenReturn(List.of());

        assertThat(metadataService.refreshConsumerGroup("instance-a", "cg-gone")).isNull();
    }

    @Test
    void cloudConsumerGroupWriteOperationsShouldRecordServiceBoundaryAudit() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setInstanceId("cloud-instance");
        group.setRetryMaxTimes(16);
        when(cloudProvider.createConsumerGroup("cloud-instance", group)).thenReturn(group);

        metadataService.createConsumerGroup(group);
        metadataService.deleteConsumerGroup("cloud-instance", " cg-orders ");
        metadataService.resetOffset("cloud-instance", " cg-orders ", 1784246400000L, "orders");

        verify(operationAuditService).record("CREATE_GROUP", "GROUP", "cg-orders",
                "cloud-instance", "consumeType=-, subscriptionMode=-, retryMaxTimes=16", "SUCCESS", null);
        verify(operationAuditService).record("DELETE_GROUP", "GROUP", "cg-orders",
                "cloud-instance", null, "SUCCESS", null);
        verify(operationAuditService).record("RESET_OFFSET", "GROUP", "cg-orders",
                "cloud-instance", "topic=orders, timestamp=1784246400000", "SUCCESS", null);
    }

}
