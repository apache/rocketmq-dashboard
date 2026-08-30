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
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.CreateConsumerGroupDTO;
import org.apache.rocketmq.studio.instance.group.ImportConsumerGroupsResultVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;

import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.provider.apache.MetadataProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
    void createTopicShouldRejectSystemTopicNamesTest() {
        TopicVO systemTopic = new TopicVO();
        systemTopic.setName("TBW102");
        systemTopic.setWriteQueues(8);
        systemTopic.setReadQueues(8);

        assertThatThrownBy(() -> metadataService.createTopic(systemTopic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("System topics cannot be created");

        verify(apacheProvider, org.mockito.Mockito.never()).createTopic(any(), any(TopicVO.class));
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
    void exportTopicsShouldApplyFiltersSelectedNamesSortingAndCsvEscaping() {
        TopicVO low = topic("orders-low", "\t=orders", TopicType.NORMAL);
        TopicVO high = topic("orders-high", "critical", TopicType.FIFO);
        TopicVO hidden = topic("users-topic", "=formula", TopicType.NORMAL);
        when(apacheProvider.listTopics("instance-a", "NORMAL", "orders")).thenReturn(List.of(low, hidden, high));

        String csv = metadataService.exportTopics("instance-a", " NORMAL ", " orders ",
                List.of("orders-high", "orders-low"));

        assertThat(csv).contains("\"Name\",\"Namespace\",\"Type\"");
        assertThat(csv).contains("\"orders-high\",\"critical\",\"FIFO\"");
        assertThat(csv).contains("\"orders-low\",\"'\t=orders\",\"NORMAL\"");
        assertThat(csv).doesNotContain("users-topic");
        assertThat(csv.indexOf("\"orders-high\"")).isLessThan(csv.indexOf("\"orders-low\""));
        verify(apacheProvider).listTopics("instance-a", "NORMAL", "orders");
    }

    @Test
    void importTopicsShouldContinueAfterRowFailure() {
        when(apacheProvider.createTopic(eq("instance-a"), any(TopicVO.class))).thenAnswer(invocation -> {
            TopicVO topic = invocation.getArgument(1);
            if ("topic-fail".equals(topic.getName())) {
                throw new BusinessException(500, "broker rejected topic");
            }
            topic.setClusterId("cluster-a");
            return topic;
        });

        ImportTopicsResultVO result = metadataService.importTopics("instance-a",
                List.of(topicImportRequest("topic-ok", "other-instance"), topicImportRequest("topic-fail", "other-instance")));

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getTopics()).extracting(TopicVO::getName).containsExactly("topic-ok");
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getIndex()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getName()).isEqualTo("topic-fail");
        assertThat(result.getFailures().get(0).getMessage()).isEqualTo("broker rejected topic");

        ArgumentCaptor<TopicVO> captor = ArgumentCaptor.forClass(TopicVO.class);
        verify(apacheProvider, org.mockito.Mockito.times(2)).createTopic(eq("instance-a"), captor.capture());
        assertThat(captor.getAllValues()).extracting(TopicVO::getInstanceId)
                .containsExactly("instance-a", "instance-a");
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

    @Test
    void exportConsumerGroupsShouldApplyFiltersSortingAndCsvEscaping() {
        ConsumerGroupVO stale = consumerGroup("users-cg", "=formula", 2, SubscriptionMode.Push);
        ConsumerGroupVO unknownLag = consumerGroup("orders-unknown", "orders", -1, SubscriptionMode.Pop);
        ConsumerGroupVO highLag = consumerGroup("orders-high", "orders", 100, SubscriptionMode.Pop);
        ConsumerGroupVO lowLag = consumerGroup("orders-low", "orders", 5, SubscriptionMode.Pop);
        when(apacheProvider.listConsumerGroups("instance-a", "orders"))
                .thenReturn(List.of(stale, unknownLag, lowLag, highLag));

        String csv = metadataService.exportConsumerGroups("instance-a", " orders ", "Pop",
                List.of("orders-low", "orders-high", "orders-unknown"));

        assertThat(csv).contains("\"Name\",\"Namespace\",\"Cluster ID\"");
        assertThat(csv).contains("\"orders-high\",\"orders\"");
        assertThat(csv).contains("\"orders-low\",\"orders\"");
        assertThat(csv).contains("\"orders-unknown\",\"orders\"");
        assertThat(csv).doesNotContain("users-cg");
        assertThat(csv.indexOf("\"orders-high\"")).isLessThan(csv.indexOf("\"orders-low\""));
        assertThat(csv.indexOf("\"orders-low\"")).isLessThan(csv.indexOf("\"orders-unknown\""));
        assertThat(csv).contains("\"orders-topic;payments,topic\"");
        verify(apacheProvider).listConsumerGroups("instance-a", "orders");
    }

    @Test
    void exportConsumerGroupsShouldEscapeFormulaCells() {
        ConsumerGroupVO group = consumerGroup("orders-cg", "=formula", 10, SubscriptionMode.Push);
        when(apacheProvider.listConsumerGroups("instance-a", null)).thenReturn(List.of(group));

        String csv = metadataService.exportConsumerGroups("instance-a", null, null, List.of());

        assertThat(csv).contains("\"'=formula\"");
    }

    @Test
    void importConsumerGroupsShouldContinueAfterRowFailure() {
        when(apacheProvider.createConsumerGroup(eq("instance-a"), any(ConsumerGroupVO.class)))
                .thenAnswer(invocation -> {
                    ConsumerGroupVO group = invocation.getArgument(1);
                    if ("cg-fail".equals(group.getName())) {
                        throw new BusinessException(500, "broker rejected group");
                    }
                    group.setClusterId("cluster-a");
                    return group;
                });

        ImportConsumerGroupsResultVO result = metadataService.importConsumerGroups("instance-a",
                List.of(importRequest("cg-ok", "other-instance"), importRequest("cg-fail", "other-instance")));

        assertThat(result.getImported()).isEqualTo(1);
        assertThat(result.getFailed()).isEqualTo(1);
        assertThat(result.getGroups()).extracting(ConsumerGroupVO::getName).containsExactly("cg-ok");
        assertThat(result.getFailures()).hasSize(1);
        assertThat(result.getFailures().get(0).getIndex()).isEqualTo(1);
        assertThat(result.getFailures().get(0).getName()).isEqualTo("cg-fail");
        assertThat(result.getFailures().get(0).getMessage()).isEqualTo("broker rejected group");

        ArgumentCaptor<ConsumerGroupVO> captor = ArgumentCaptor.forClass(ConsumerGroupVO.class);
        verify(apacheProvider, org.mockito.Mockito.times(2))
                .createConsumerGroup(eq("instance-a"), captor.capture());
        assertThat(captor.getAllValues()).extracting(ConsumerGroupVO::getInstanceId)
                .containsExactly("instance-a", "instance-a");
    }


    @Test
    void importConsumerGroupsShouldRejectEmptyAndOversizedBatchesTest() {
        assertThatThrownBy(() -> metadataService.importConsumerGroups("instance-a", List.of()))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("groups is required");

        List<CreateConsumerGroupDTO> oversized = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            oversized.add(importRequest("cg-" + i, null));
        }
        assertThatThrownBy(() -> metadataService.importConsumerGroups("instance-a", oversized))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("At most 100");

        verifyNoInteractions(apacheProvider);
    }

    @Test
    void previewResetOffsetShouldNormalizeAndDelegateToProvider() {
        ResetConsumerOffsetPreviewVO preview = ResetConsumerOffsetPreviewVO.builder()
                .instanceId("instance-a")
                .groupName("cg-orders")
                .topic("orders")
                .timestamp(1784246400000L)
                .complete(true)
                .allowReset(true)
                .queueCount(0)
                .warnings(List.of())
                .queues(List.of())
                .build();
        when(apacheProvider.previewResetOffset("instance-a", "cg-orders", 1784246400000L, "orders"))
                .thenReturn(preview);

        ResetConsumerOffsetPreviewVO result = metadataService.previewResetOffset(
                "instance-a", " cg-orders ", 1784246400000L, " orders ");

        assertThat(result).isSameAs(preview);
        verify(apacheProvider).previewResetOffset("instance-a", "cg-orders", 1784246400000L, "orders");
    }

    @Test
    void previewResetOffsetShouldRejectBlankTopicBeforeProviderResolution() {
        assertThatThrownBy(() -> metadataService.previewResetOffset("instance-a", "cg-orders",
                1784246400000L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic name is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));


        verifyNoInteractions(apacheProvider);
    }

    private ConsumerGroupVO consumerGroup(String name, String namespace, long lag, SubscriptionMode mode) {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName(name);
        group.setNamespace(namespace);
        group.setClusterId("cluster-a");
        group.setSubscriptionMode(mode);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setOnlineInstances(1);
        group.setTotalLag(lag);
        group.setDelaySeconds(3);
        group.setSubscriptionDataType("NORMAL");
        group.setRetryMaxTimes(16);
        group.setSubscribedTopics(List.of("orders-topic", "payments,topic"));
        group.setGmtCreate(LocalDateTime.of(2026, 8, 27, 10, 0));
        group.setGmtModified(LocalDateTime.of(2026, 8, 27, 11, 0));
        return group;
    }

    private CreateConsumerGroupDTO importRequest(String name, String instanceId) {
        CreateConsumerGroupDTO request = new CreateConsumerGroupDTO();
        request.setName(name);
        request.setInstanceId(instanceId);
        request.setSubscriptionMode(SubscriptionMode.Push);
        request.setConsumeType(ConsumeType.CLUSTERING);
        request.setRetryMaxTimes(16);
        request.setSubscriptionDataType("NORMAL");
        return request;

    }

    private TopicVO topic(String name, String namespace, TopicType type) {
        TopicVO topic = new TopicVO();
        topic.setName(name);
        topic.setNamespace(namespace);
        topic.setType(type);
        topic.setClusterId("cluster-a");
        topic.setWriteQueues(8);
        topic.setReadQueues(8);
        topic.setPerm(TopicPerm.RW);
        topic.setMessageCount(100);
        topic.setTps(2.5);
        topic.setConsumerGroupCount(3);
        topic.setRemark("remark");
        topic.setGmtCreate(LocalDateTime.of(2026, 8, 27, 10, 0));
        topic.setGmtModified(LocalDateTime.of(2026, 8, 27, 11, 0));
        return topic;
    }

    private CreateTopicDTO topicImportRequest(String name, String instanceId) {
        CreateTopicDTO request = new CreateTopicDTO();
        request.setName(name);
        request.setInstanceId(instanceId);
        request.setType(TopicType.NORMAL);
        request.setWriteQueues(8);
        request.setReadQueues(8);
        request.setPerm(TopicPerm.RW);
        return request;

    }


}
