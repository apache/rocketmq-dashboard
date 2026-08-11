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
package org.apache.rocketmq.studio.provider.alibaba;

import com.aliyun.sdk.service.rocketmq20220801.AsyncClient;
import com.aliyun.sdk.service.rocketmq20220801.models.CreateConsumerGroupRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.CreateConsumerGroupResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.CreateConsumerGroupResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.DataTopicLagMapValue;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ResetConsumeOffsetRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ResetConsumeOffsetResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ResetConsumeOffsetResponseBody;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AliyunInstanceProviderTest {

    private static final String STUDIO_INSTANCE_ID = "inst-1";
    private static final String CLOUD_INSTANCE_ID = "rmq-cn-001";
    private static final String REGION = "cn-hangzhou";
    private static final String CREDENTIAL_ID = "cred-1";

    @Mock
    private AliyunClientFactory clientFactory;

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private AsyncClient asyncClient;

    private AliyunInstanceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new AliyunInstanceProvider(clientFactory, instanceRepository);
    }

    @Test
    void listTopicsShouldMapMessageTypeAndFilterTest() {
        stubInstance();
        stubCallThrough();
        ListTopicsResponse response = topicsResponse(
                topicRow("topic-normal", "NORMAL"),
                topicRow("topic-fifo", "FIFO"),
                topicRow("topic-mystery", "MYSTERY"));
        when(asyncClient.listTopics(any(ListTopicsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        List<TopicVO> all = provider.listTopics(STUDIO_INSTANCE_ID, null, null);

        assertThat(all).hasSize(3);
        assertThat(all.get(0).getName()).isEqualTo("topic-normal");
        assertThat(all.get(0).getType()).isEqualTo(TopicType.NORMAL);
        assertThat(all.get(0).getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
        assertThat(all.get(0).getWriteQueues()).isZero();
        assertThat(all.get(0).getReadQueues()).isZero();
        assertThat(all.get(0).getRemark()).isEqualTo("remark-topic-normal");
        assertThat(all.get(2).getType()).isNull();

        List<TopicVO> fifos = provider.listTopics(STUDIO_INSTANCE_ID, "FIFO", null);

        assertThat(fifos).hasSize(1);
        assertThat(fifos.get(0).getType()).isEqualTo(TopicType.FIFO);
    }

    @Test
    void listConsumerGroupsShouldMapGroupIdTest() {
        stubInstance();
        stubCallThrough();
        ListConsumerGroupsResponse response = ListConsumerGroupsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListConsumerGroupsResponseBody.builder()
                        .data(ListConsumerGroupsResponseBody.Data.builder()
                                .list(List.of(ListConsumerGroupsResponseBody.List.builder()
                                        .consumerGroupId("GID_test")
                                        .messageModel("Clustering")
                                        .status("RUNNING")
                                        .remark("test group")
                                        .build()))
                                .pageNumber(1L)
                                .pageSize(100L)
                                .totalCount(1L)
                                .build())
                        .build())
                .build();
        when(asyncClient.listConsumerGroups(any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        List<ConsumerGroupVO> groups = provider.listConsumerGroups(STUDIO_INSTANCE_ID, null);

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getName()).isEqualTo("GID_test");
        assertThat(groups.get(0).getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
    }

    @Test
    void getGroupProgressShouldMapLagRowsTest() {
        stubInstance();
        stubCallThrough();
        GetConsumerGroupLagResponse response = GetConsumerGroupLagResponse.create().toBuilder()
                .statusCode(200)
                .body(GetConsumerGroupLagResponseBody.builder()
                        .data(GetConsumerGroupLagResponseBody.Data.builder()
                                .consumerGroupId("GID_test")
                                .topicLagMap(Map.of("topic-a",
                                        DataTopicLagMapValue.builder().readyCount(42L).build()))
                                .totalLag(GetConsumerGroupLagResponseBody.TotalLag.builder()
                                        .readyCount(100L)
                                        .build())
                                .build())
                        .build())
                .build();
        when(asyncClient.getConsumerGroupLag(any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        List<QueueProgressVO> rows = provider.getGroupProgress(STUDIO_INSTANCE_ID, "GID_test");

        assertThat(rows).hasSize(2);
        QueueProgressVO topicRow = rows.stream()
                .filter(row -> "topic:topic-a".equals(row.getBroker()))
                .findFirst()
                .orElseThrow();
        assertThat(topicRow.getDiffTotal()).isEqualTo(42L);
        QueueProgressVO totalRow = rows.stream()
                .filter(row -> "total".equals(row.getBroker()))
                .findFirst()
                .orElseThrow();
        assertThat(totalRow.getDiffTotal()).isEqualTo(100L);
    }

    @Test
    void queryMessagesShouldMapFieldsAndDecodeBase64BodyTest() {
        stubInstance();
        stubCallThrough();
        String encodedBody = Base64.getEncoder().encodeToString("hello".getBytes(StandardCharsets.UTF_8));
        ListMessagesResponse response = ListMessagesResponse.create().toBuilder()
                .statusCode(200)
                .body(ListMessagesResponseBody.builder()
                        .data(ListMessagesResponseBody.Data.builder()
                                .list(List.of(
                                        ListMessagesResponseBody.List.builder()
                                                .messageId("msg-1")
                                                .topicName("topic-a")
                                                .messageTag("tagA")
                                                .messageKeys(List.of("k1", "k2"))
                                                .body(encodedBody)
                                                .bodySize(5)
                                                .bornHost("10.0.0.1")
                                                .storeHost("10.0.0.2")
                                                .storeTime("2023-03-22 12:17:08")
                                                .userProperties(Map.of("a", "b"))
                                                .build(),
                                        ListMessagesResponseBody.List.builder()
                                                .messageId("msg-2")
                                                .topicName("topic-a")
                                                .messageTag("tagB")
                                                .body("{}")
                                                .bodySize(2)
                                                .build()))
                                .pageNumber(1L)
                                .pageSize(20L)
                                .totalCount(2L)
                                .build())
                        .build())
                .build();
        when(asyncClient.listMessages(any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        List<MessageRecordVO> records = provider.queryMessages(STUDIO_INSTANCE_ID, "topic-a", null,
                null, null, null, null);

        assertThat(records).hasSize(2);
        MessageRecordVO first = records.get(0);
        assertThat(first.getMsgId()).isEqualTo("msg-1");
        assertThat(first.getTag()).isEqualTo("tagA");
        assertThat(first.getKey()).isEqualTo("k1 k2");
        assertThat(first.getBody()).isEqualTo("hello");
        assertThat(first.getBodyEncoding()).isEqualTo("UTF-8");
        assertThat(first.getStoreTime())
                .isEqualTo(AliyunConverters.parseTimeMillis("2023-03-22 12:17:08"));
        assertThat(first.getBornHost()).isEqualTo("10.0.0.1");
        assertThat(first.getProperties()).containsEntry("a", "b");
        MessageRecordVO second = records.get(1);
        assertThat(second.getBody()).isEqualTo("{}");
        assertThat(second.getBodyEncoding()).isEqualTo("TEXT");

        List<MessageRecordVO> filtered = provider.queryMessages(STUDIO_INSTANCE_ID, "topic-a", null,
                "tagB", null, null, null);

        assertThat(filtered).hasSize(1);
        assertThat(filtered.get(0).getMsgId()).isEqualTo("msg-2");
    }

    @Test
    void createConsumerGroupShouldApplyDefaultsTest() {
        stubInstance();
        stubCallThrough();
        when(asyncClient.createConsumerGroup(any()))
                .thenReturn(CompletableFuture.completedFuture(CreateConsumerGroupResponse.create()
                        .toBuilder()
                        .statusCode(200)
                        .body(CreateConsumerGroupResponseBody.builder().data(true).build())
                        .build()));
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("GID_new");

        ConsumerGroupVO created = provider.createConsumerGroup(STUDIO_INSTANCE_ID, group);

        ArgumentCaptor<CreateConsumerGroupRequest> captor =
                ArgumentCaptor.forClass(CreateConsumerGroupRequest.class);
        verify(asyncClient).createConsumerGroup(captor.capture());
        CreateConsumerGroupRequest request = captor.getValue();
        assertThat(request.getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(request.getConsumerGroupId()).isEqualTo("GID_new");
        assertThat(request.getDeliveryOrderType()).isEqualTo("Concurrently");
        assertThat(request.getConsumeRetryPolicy().getRetryPolicy()).isEqualTo("DefaultRetryPolicy");
        assertThat(request.getConsumeRetryPolicy().getMaxRetryTimes()).isEqualTo(16);
        assertThat(created.getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
        assertThat(created.getDeliveryOrderType()).isEqualTo("Concurrently");
        assertThat(created.getRetryMaxTimes()).isEqualTo(16);
    }

    @Test
    void resetOffsetShouldUseSpecifiedTimeTest() {
        stubInstance();
        stubCallThrough();
        when(asyncClient.resetConsumeOffset(any()))
                .thenReturn(CompletableFuture.completedFuture(ResetConsumeOffsetResponse.create()
                        .toBuilder()
                        .statusCode(200)
                        .body(ResetConsumeOffsetResponseBody.builder().success(true).build())
                        .build()));
        long timestamp = 1679458628000L;

        provider.resetOffset(STUDIO_INSTANCE_ID, "GID_test", timestamp, "topic-a");

        ArgumentCaptor<ResetConsumeOffsetRequest> captor =
                ArgumentCaptor.forClass(ResetConsumeOffsetRequest.class);
        verify(asyncClient).resetConsumeOffset(captor.capture());
        ResetConsumeOffsetRequest request = captor.getValue();
        assertThat(request.getConsumerGroupId()).isEqualTo("GID_test");
        assertThat(request.getTopicName()).isEqualTo("topic-a");
        assertThat(request.getResetType()).isEqualTo("SPECIFIED_TIME");
        assertThat(request.getResetTime()).isEqualTo(AliyunConverters.formatTimeMillis(timestamp));
    }

    @Test
    void getMessageTraceShouldMapNodesTest() {
        stubInstance();
        stubCallThrough();
        GetTraceResponse response = GetTraceResponse.create().toBuilder()
                .statusCode(200)
                .body(GetTraceResponseBody.builder()
                        .data(GetTraceResponseBody.Data.builder()
                                .producerInfo(GetTraceResponseBody.ProducerInfo.builder()
                                        .records(List.of(GetTraceResponseBody.ProducerInfoRecords.builder()
                                                .produceTime("2023-03-22 12:17:08")
                                                .produceStatus("SEND_OK")
                                                .produceDuration(12L)
                                                .clientHost("10.0.0.1")
                                                .messageSource("SDK")
                                                .build()))
                                        .build())
                                .brokerInfo(GetTraceResponseBody.BrokerInfo.builder()
                                        .operations(List.of(GetTraceResponseBody.Operations.builder()
                                                .operateType("store")
                                                .operateTime("2023-03-22 12:17:09")
                                                .build()))
                                        .build())
                                .consumerInfos(List.of(GetTraceResponseBody.ConsumerInfos.builder()
                                        .consumerGroupId("GID_test")
                                        .records(List.of(GetTraceResponseBody.Records.builder()
                                                .consumeStatus("CONSUME_OK")
                                                .clientHost("10.0.0.3")
                                                .operations(List.of(
                                                        GetTraceResponseBody.RecordsOperations.builder()
                                                                .operateType("pull")
                                                                .operateTime("2023-03-22 12:17:10")
                                                                .build()))
                                                .build()))
                                        .build()))
                                .build())
                        .build())
                .build();
        when(asyncClient.getTrace(any())).thenReturn(CompletableFuture.completedFuture(response));

        TraceRecordVO trace = provider.getMessageTrace(STUDIO_INSTANCE_ID, "msg-1");

        assertThat(trace.getNodes()).hasSize(3);
        TraceNodeVO producer = trace.getNodes().get(0);
        assertThat(producer.getTitle()).isEqualTo("Producer");
        assertThat(producer.getStatus()).isEqualTo("SEND_OK");
        assertThat(producer.getCostTime()).isEqualTo(12L);
        assertThat(producer.getTimestamp())
                .isEqualTo(AliyunConverters.parseTimeMillis("2023-03-22 12:17:08"));
        assertThat(trace.getNodes().get(1).getTitle()).isEqualTo("Broker store");
        TraceNodeVO consumer = trace.getNodes().get(2);
        assertThat(consumer.getTitle()).isEqualTo("Consumer GID_test");
        assertThat(consumer.getStatus()).isEqualTo("CONSUME_OK");
    }

    @Test
    void mappedBusinessExceptionShouldPropagateTest() {
        stubInstance();
        when(clientFactory.call(anyString(), anyString(), any()))
                .thenThrow(new BusinessException(404, "Aliyun resource not found"));

        assertThatThrownBy(() -> provider.listTopics(STUDIO_INSTANCE_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(404);
    }

    @Test
    void resolveShouldRejectMissingCloudBindingTest() {
        InstanceVO instance = InstanceVO.builder()
                .name("incomplete")
                .vendor(InstanceVendor.ALIYUN)
                .cloudInstanceId(CLOUD_INSTANCE_ID)
                .regionId(REGION)
                .build();
        when(instanceRepository.findById(STUDIO_INSTANCE_ID)).thenReturn(Optional.of(instance));

        assertThatThrownBy(() -> provider.listTopics(STUDIO_INSTANCE_ID, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(400);
    }

    private void stubInstance() {
        InstanceVO instance = InstanceVO.builder()
                .name("aliyun-prod")
                .vendor(InstanceVendor.ALIYUN)
                .cloudInstanceId(CLOUD_INSTANCE_ID)
                .regionId(REGION)
                .credentialId(CREDENTIAL_ID)
                .build();
        when(instanceRepository.findById(STUDIO_INSTANCE_ID)).thenReturn(Optional.of(instance));
    }

    private void stubCallThrough() {
        when(clientFactory.call(anyString(), anyString(), any())).thenAnswer(invocation -> {
            Function<AsyncClient, CompletableFuture<Object>> action = invocation.getArgument(2);
            return action.apply(asyncClient).join();
        });
    }

    private static ListTopicsResponse topicsResponse(ListTopicsResponseBody.List... rows) {
        return ListTopicsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListTopicsResponseBody.builder()
                        .data(ListTopicsResponseBody.Data.builder()
                                .list(List.of(rows))
                                .pageNumber(1L)
                                .pageSize(100L)
                                .totalCount((long) rows.length)
                                .build())
                        .build())
                .build();
    }

    private static ListTopicsResponseBody.List topicRow(String name, String messageType) {
        return ListTopicsResponseBody.List.builder()
                .topicName(name)
                .messageType(messageType)
                .remark("remark-" + name)
                .build();
    }

    @Test
    void countTopicsShouldUseTotalCountWithoutFetchingEveryTopicTest() {
        stubInstance();
        stubCallThrough();
        ListTopicsResponse response = ListTopicsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListTopicsResponseBody.builder()
                        .data(ListTopicsResponseBody.Data.builder()
                                .list(List.of(topicRow("topic-a", "NORMAL")))
                                .pageNumber(1L)
                                .pageSize(1L)
                                .totalCount(321L)
                                .build())
                        .build())
                .build();
        when(asyncClient.listTopics(any(ListTopicsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(response));

        assertThat(provider.countTopics(STUDIO_INSTANCE_ID)).isEqualTo(321);
        ArgumentCaptor<ListTopicsRequest> captor = ArgumentCaptor.forClass(ListTopicsRequest.class);
        verify(asyncClient).listTopics(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1L);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1L);
    }

    @Test
    void countGroupsShouldUseTotalCountWithoutFetchingEveryGroupTest() {
        stubInstance();
        stubCallThrough();
        ListConsumerGroupsResponse response = ListConsumerGroupsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListConsumerGroupsResponseBody.builder()
                        .data(ListConsumerGroupsResponseBody.Data.builder()
                                .list(List.of(ListConsumerGroupsResponseBody.List.builder()
                                        .consumerGroupId("GID_one")
                                        .build()))
                                .pageNumber(1L)
                                .pageSize(1L)
                                .totalCount(654L)
                                .build())
                        .build())
                .build();
        when(asyncClient.listConsumerGroups(any()))
                .thenReturn(CompletableFuture.completedFuture(response));

        assertThat(provider.countGroups(STUDIO_INSTANCE_ID)).isEqualTo(654);
        ArgumentCaptor<ListConsumerGroupsRequest> captor =
                ArgumentCaptor.forClass(ListConsumerGroupsRequest.class);
        verify(asyncClient).listConsumerGroups(captor.capture());
        assertThat(captor.getValue().getPageNumber()).isEqualTo(1L);
        assertThat(captor.getValue().getPageSize()).isEqualTo(1L);
    }

    @Test
    void countTopicsShouldFallBackToFullListingWhenTotalCountIsMissingTest() {
        stubInstance();
        stubCallThrough();
        ListTopicsResponse missingTotal = ListTopicsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListTopicsResponseBody.builder()
                        .data(ListTopicsResponseBody.Data.builder()
                                .list(List.of(topicRow("topic-a", "NORMAL")))
                                .pageNumber(1L)
                                .pageSize(1L)
                                .build())
                        .build())
                .build();
        when(asyncClient.listTopics(any(ListTopicsRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(missingTotal))
                .thenReturn(CompletableFuture.completedFuture(topicsResponse(
                        topicRow("topic-a", "NORMAL"),
                        topicRow("topic-b", "FIFO"))));

        assertThat(provider.countTopics(STUDIO_INSTANCE_ID)).isEqualTo(2);
        ArgumentCaptor<ListTopicsRequest> captor = ArgumentCaptor.forClass(ListTopicsRequest.class);
        verify(asyncClient, times(2)).listTopics(captor.capture());
        assertThat(captor.getAllValues()).extracting(ListTopicsRequest::getPageSize)
                .containsExactly(1, AliyunConverters.PAGE_SIZE);
    }

    @Test
    void countGroupsShouldFallBackToFullListingWhenTotalCountIsMissingTest() {
        stubInstance();
        stubCallThrough();
        ListConsumerGroupsResponse missingTotal = groupsResponse(null, "GID_one");
        ListConsumerGroupsResponse completeListing = groupsResponse(2L, "GID_one", "GID_two");
        when(asyncClient.listConsumerGroups(any()))
                .thenReturn(CompletableFuture.completedFuture(missingTotal))
                .thenReturn(CompletableFuture.completedFuture(completeListing));

        assertThat(provider.countGroups(STUDIO_INSTANCE_ID)).isEqualTo(2);
        ArgumentCaptor<ListConsumerGroupsRequest> captor =
                ArgumentCaptor.forClass(ListConsumerGroupsRequest.class);
        verify(asyncClient, times(2)).listConsumerGroups(captor.capture());
        assertThat(captor.getAllValues()).extracting(ListConsumerGroupsRequest::getPageSize)
                .containsExactly(1, AliyunConverters.PAGE_SIZE);
    }

    private static ListConsumerGroupsResponse groupsResponse(Long totalCount, String... groupIds) {
        return ListConsumerGroupsResponse.create().toBuilder()
                .statusCode(200)
                .body(ListConsumerGroupsResponseBody.builder()
                        .data(ListConsumerGroupsResponseBody.Data.builder()
                                .list(java.util.Arrays.stream(groupIds)
                                        .map(groupId -> ListConsumerGroupsResponseBody.List.builder()
                                                .consumerGroupId(groupId)
                                                .build())
                                        .toList())
                                .pageNumber(1L)
                                .pageSize((long) AliyunConverters.PAGE_SIZE)
                                .totalCount(totalCount)
                                .build())
                        .build())
                .build();
    }

    @Test
    void normalizeDeliveryOrderTypeShouldMapFifoToOrderlyTest() {
        org.junit.jupiter.api.Assertions.assertEquals("Orderly",
                AliyunInstanceProvider.normalizeDeliveryOrderType("FIFO"));
        org.junit.jupiter.api.Assertions.assertEquals("Orderly",
                AliyunInstanceProvider.normalizeDeliveryOrderType("orderly"));
        org.junit.jupiter.api.Assertions.assertEquals("Concurrently",
                AliyunInstanceProvider.normalizeDeliveryOrderType(null));
        org.junit.jupiter.api.Assertions.assertEquals("Concurrently",
                AliyunInstanceProvider.normalizeDeliveryOrderType("Concurrently"));
    }
}
