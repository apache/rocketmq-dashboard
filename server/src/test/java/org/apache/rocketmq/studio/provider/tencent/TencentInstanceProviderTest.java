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
package org.apache.rocketmq.studio.provider.tencent;

import com.tencentcloudapi.trocket.v20230308.models.ConsumeGroupItem;
import com.tencentcloudapi.trocket.v20230308.models.CreateConsumerGroupRequest;
import com.tencentcloudapi.trocket.v20230308.models.CreateTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DeleteConsumerGroupRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeMessageListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeMessageListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeMessageRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeMessageResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeMessageTraceRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeMessageTraceResponse;
import com.tencentcloudapi.trocket.v20230308.models.MessageItem;
import com.tencentcloudapi.trocket.v20230308.models.MessageTraceItem;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListByGroupResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicResponse;
import com.tencentcloudapi.trocket.v20230308.models.ModifyTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.ResetConsumerGroupOffsetRequest;
import com.tencentcloudapi.trocket.v20230308.models.SubscriptionData;
import com.tencentcloudapi.trocket.v20230308.models.TopicItem;
import com.tencentcloudapi.trocket.v20230308.TrocketClient;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TencentInstanceProviderTest {

    private static final String STUDIO_INSTANCE_ID = "inst-1";
    private static final String CLOUD_INSTANCE_ID = "rmq-abc";
    private static final String REGION = "ap-chengdu";
    private static final String CREDENTIAL_ID = "cred-1";

    @Mock
    private TencentClientFactory clientFactory;

    @Mock
    private InstanceRepository instanceRepository;

    @Mock
    private TrocketClient client;

    private TencentInstanceProvider provider;

    @BeforeEach
    void setUp() {
        provider = new TencentInstanceProvider(clientFactory, instanceRepository);
        when(instanceRepository.findById(STUDIO_INSTANCE_ID)).thenReturn(Optional.of(InstanceVO.builder()
                .name("tencent-prod")
                .vendor(InstanceVendor.TENCENT)
                .cloudInstanceId(CLOUD_INSTANCE_ID)
                .regionId(REGION)
                .credentialId(CREDENTIAL_ID)
                .build()));
        lenient().when(clientFactory.call(anyString(), anyString(), any())).thenAnswer(invocation -> {
            TencentClientFactory.TencentCall<Object> action = invocation.getArgument(2);
            return action.execute(client);
        });
    }

    @Test
    void listTopicsShouldMapAndFilterAndEnrichTimesTest() throws Exception {
        TopicItem normal = topicItem("orders", "NORMAL", 8L);
        TopicItem fifo = topicItem("orders-fifo", "FIFO", 4L);
        DescribeTopicListResponse response = new DescribeTopicListResponse();
        response.setData(new TopicItem[]{normal, fifo});
        when(client.DescribeTopicList(any())).thenReturn(response);
        DescribeTopicResponse detail = new DescribeTopicResponse();
        detail.setCreatedTime(1600000000000L);
        detail.setLastUpdateTime(1600000100000L);
        when(client.DescribeTopic(any())).thenReturn(detail);

        List<TopicVO> topics = provider.listTopics(STUDIO_INSTANCE_ID, "FIFO", "fifo");

        assertThat(topics).hasSize(1);
        assertThat(topics.get(0).getName()).isEqualTo("orders-fifo");
        assertThat(topics.get(0).getType()).isEqualTo(TopicType.FIFO);
        assertThat(topics.get(0).getWriteQueues()).isEqualTo(4);
        assertThat(topics.get(0).getReadQueues()).isEqualTo(4);
        assertThat(topics.get(0).getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
        assertThat(topics.get(0).getCreatedAt())
                .isEqualTo(java.time.Instant.ofEpochMilli(1600000000000L)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        assertThat(topics.get(0).getUpdatedAt())
                .isEqualTo(java.time.Instant.ofEpochMilli(1600000100000L)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
    }

    @Test
    void createTopicShouldCallTencentOpenApiTest() throws Exception {
        when(client.CreateTopic(any())).thenReturn(null);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(12);
        topic.setRemark("business orders");

        TopicVO created = provider.createTopic(STUDIO_INSTANCE_ID, topic);

        ArgumentCaptor<CreateTopicRequest> captor = ArgumentCaptor.forClass(CreateTopicRequest.class);
        verify(client).CreateTopic(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getTopic()).isEqualTo("orders");
        assertThat(captor.getValue().getTopicType()).isEqualTo("NORMAL");
        assertThat(captor.getValue().getQueueNum()).isEqualTo(12L);
        assertThat(created.getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
    }

    @Test
    void createTopicShouldAcceptTencentQueueDefaultsAndBoundariesTest() throws Exception {
        when(client.CreateTopic(any())).thenReturn(null);
        for (int queueNum : new int[]{0, 3, 16}) {
            TopicVO topic = new TopicVO();
            topic.setName("orders-" + queueNum);
            topic.setType(TopicType.NORMAL);
            topic.setWriteQueues(queueNum);
            provider.createTopic(STUDIO_INSTANCE_ID, topic);
        }

        ArgumentCaptor<CreateTopicRequest> captor = ArgumentCaptor.forClass(CreateTopicRequest.class);
        verify(client, times(3)).CreateTopic(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(CreateTopicRequest::getQueueNum)
                .containsExactly(8L, 3L, 16L);
    }

    @Test
    void createTopicShouldRejectQueueCountsOutsideTencentRangeTest() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(2);

        assertThatThrownBy(() -> provider.createTopic(STUDIO_INSTANCE_ID, topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("between 3 and 16");

        topic.setWriteQueues(17);
        assertThatThrownBy(() -> provider.createTopic(STUDIO_INSTANCE_ID, topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("between 3 and 16");
        verifyNoInteractions(client);
    }

    @Test
    void topicWritesShouldRejectMismatchedQueueCountsTest() {
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(8);
        topic.setReadQueues(4);

        assertThatThrownBy(() -> provider.createTopic(STUDIO_INSTANCE_ID, topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must match for Tencent Cloud");
        assertThatThrownBy(() -> provider.updateTopic(STUDIO_INSTANCE_ID, topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("must match for Tencent Cloud");
        verifyNoInteractions(client);
    }

    @Test
    void updateAndDeleteTopicShouldCallTencentOpenApiTest() throws Exception {
        when(client.ModifyTopic(any())).thenReturn(null);
        when(client.DeleteTopic(any())).thenReturn(null);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.NORMAL);
        topic.setRemark("updated");

        provider.updateTopic(STUDIO_INSTANCE_ID, topic);
        provider.deleteTopic(STUDIO_INSTANCE_ID, "orders");

        ArgumentCaptor<ModifyTopicRequest> updateCaptor = ArgumentCaptor.forClass(ModifyTopicRequest.class);
        verify(client).ModifyTopic(updateCaptor.capture());
        assertThat(updateCaptor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(updateCaptor.getValue().getTopic()).isEqualTo("orders");
        assertThat(updateCaptor.getValue().getRemark()).isEqualTo("updated");
        assertThat(updateCaptor.getValue().getQueueNum()).isNull();
    }

    @Test
    void updateTopicShouldNormalizeTheSingleTencentQueueCountTest() throws Exception {
        when(client.ModifyTopic(any())).thenReturn(null);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.NORMAL);
        topic.setWriteQueues(12);

        TopicVO updated = provider.updateTopic(STUDIO_INSTANCE_ID, topic);

        ArgumentCaptor<ModifyTopicRequest> captor = ArgumentCaptor.forClass(ModifyTopicRequest.class);
        verify(client).ModifyTopic(captor.capture());
        assertThat(captor.getValue().getQueueNum()).isEqualTo(12L);
        assertThat(updated.getWriteQueues()).isEqualTo(12);
        assertThat(updated.getReadQueues()).isEqualTo(12);
    }

    @Test
    void getTopicConsumersShouldMapSubscriptionsTest() throws Exception {
        SubscriptionData subscription = new SubscriptionData();
        subscription.setConsumerGroup("GID_orders");
        subscription.setConsumeType("CLUSTERING");
        subscription.setMessageModel("CLUSTERING");
        subscription.setConsumerLag(42L);
        DescribeTopicResponse response = new DescribeTopicResponse();
        response.setSubscriptionData(new SubscriptionData[]{subscription});
        when(client.DescribeTopic(any())).thenReturn(response);

        List<TopicConsumerVO> consumers = provider.getTopicConsumers(STUDIO_INSTANCE_ID, "orders");

        assertThat(consumers).hasSize(1);
        assertThat(consumers.get(0).getGroup()).isEqualTo("GID_orders");
        assertThat(consumers.get(0).getDiffTotal()).isEqualTo(42L);
    }

    @Test
    void getTopicConsumersShouldReadEverySubscriptionPageTest() throws Exception {
        DescribeTopicResponse firstPage = new DescribeTopicResponse();
        firstPage.setSubscriptionCount(101L);
        firstPage.setSubscriptionData(IntStream.range(0, 100)
                .mapToObj(index -> subscription("GID_" + index))
                .toArray(SubscriptionData[]::new));
        DescribeTopicResponse secondPage = new DescribeTopicResponse();
        secondPage.setSubscriptionCount(101L);
        secondPage.setSubscriptionData(new SubscriptionData[]{subscription("GID_100")});
        when(client.DescribeTopic(any())).thenReturn(firstPage, secondPage);

        List<TopicConsumerVO> consumers = provider.getTopicConsumers(STUDIO_INSTANCE_ID, "orders");

        assertThat(consumers).hasSize(101);
        assertThat(consumers.get(0).getGroup()).isEqualTo("GID_0");
        assertThat(consumers.get(100).getGroup()).isEqualTo("GID_100");
        ArgumentCaptor<DescribeTopicRequest> captor = ArgumentCaptor.forClass(DescribeTopicRequest.class);
        verify(client, org.mockito.Mockito.times(2)).DescribeTopic(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DescribeTopicRequest::getOffset)
                .containsExactly(0L, 100L);
        assertThat(captor.getAllValues())
                .extracting(DescribeTopicRequest::getLimit)
                .containsOnly(100L);
    }

    @Test
    void listConsumerGroupsShouldMapAndFilterTest() throws Exception {
        ConsumeGroupItem one = new ConsumeGroupItem();
        one.setConsumerGroup("GID_test");
        one.setMaxRetryTimes(10L);
        one.setConsumeMessageOrderly(false);
        ConsumeGroupItem two = new ConsumeGroupItem();
        two.setConsumerGroup("GID_orders");
        two.setMaxRetryTimes(16L);
        DescribeConsumerGroupListResponse response = new DescribeConsumerGroupListResponse();
        response.setData(new ConsumeGroupItem[]{one, two});
        when(client.DescribeConsumerGroupList(any())).thenReturn(response);
        DescribeConsumerGroupResponse detail = new DescribeConsumerGroupResponse();
        detail.setCreatedTime(1600000000000L);
        detail.setConsumeModel("CLUSTERING");
        when(client.DescribeConsumerGroup(any())).thenReturn(detail);

        List<ConsumerGroupVO> groups = provider.listConsumerGroups(STUDIO_INSTANCE_ID, "orders");

        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getName()).isEqualTo("GID_orders");
        assertThat(groups.get(0).getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
        assertThat(groups.get(0).getRetryMaxTimes()).isEqualTo(16);
        assertThat(groups.get(0).getCreatedAt()).isNotNull();
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(groups.get(0).getInstances()).isNotNull().isEmpty();
    }

    @Test
    void createConsumerGroupShouldCallTencentOpenApiTest() throws Exception {
        when(client.CreateConsumerGroup(any())).thenReturn(null);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("GID_new");
        group.setRetryMaxTimes(20);

        ConsumerGroupVO created = provider.createConsumerGroup(STUDIO_INSTANCE_ID, group);

        ArgumentCaptor<CreateConsumerGroupRequest> captor = ArgumentCaptor.forClass(CreateConsumerGroupRequest.class);
        verify(client).CreateConsumerGroup(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getConsumerGroup()).isEqualTo("GID_new");
        assertThat(captor.getValue().getMaxRetryTimes()).isEqualTo(20L);
        assertThat(created.getInstanceId()).isEqualTo(STUDIO_INSTANCE_ID);
        assertThat(created.getRetryMaxTimes()).isEqualTo(20);
    }

    @Test
    void deleteConsumerGroupShouldCallTencentOpenApiTest() throws Exception {
        when(client.DeleteConsumerGroup(any())).thenReturn(null);

        provider.deleteConsumerGroup(STUDIO_INSTANCE_ID, "GID_test");

        ArgumentCaptor<DeleteConsumerGroupRequest> captor = ArgumentCaptor.forClass(DeleteConsumerGroupRequest.class);
        verify(client).DeleteConsumerGroup(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getConsumerGroup()).isEqualTo("GID_test");
    }

    @Test
    void getGroupProgressAndSubscriptionsShouldMapSubscriptionDataTest() throws Exception {
        SubscriptionData subscription = new SubscriptionData();
        subscription.setTopic("orders");
        subscription.setSubString("*");
        subscription.setExpressionType("TAG");
        subscription.setConsumerLag(42L);
        subscription.setConsistency(0L);
        DescribeTopicListByGroupResponse response = new DescribeTopicListByGroupResponse();
        response.setData(new SubscriptionData[]{subscription});
        when(client.DescribeTopicListByGroup(any())).thenReturn(response);

        List<QueueProgressVO> progress = provider.getGroupProgress(STUDIO_INSTANCE_ID, "GID_test");
        assertThat(progress).hasSize(1);
        assertThat(progress.get(0).getBroker()).isEqualTo("topic:orders");
        assertThat(progress.get(0).getDiffTotal()).isEqualTo(42L);

        List<SubscriptionEntryVO> subscriptions = provider.getGroupSubscriptions(STUDIO_INSTANCE_ID, "GID_test");
        assertThat(subscriptions).hasSize(1);
        assertThat(subscriptions.get(0).getTopic()).isEqualTo("orders");
        assertThat(subscriptions.get(0).getExpression()).isEqualTo("*");
        assertThat(subscriptions.get(0).getType()).isEqualTo("TAG");
    }

    @Test
    void resetOffsetShouldCallTencentOpenApiTest() throws Exception {
        when(client.ResetConsumerGroupOffset(any())).thenReturn(null);

        provider.resetOffset(STUDIO_INSTANCE_ID, "GID_test", 1600000000000L, "orders");

        ArgumentCaptor<ResetConsumerGroupOffsetRequest> captor =
                ArgumentCaptor.forClass(ResetConsumerGroupOffsetRequest.class);
        verify(client).ResetConsumerGroupOffset(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getConsumerGroup()).isEqualTo("GID_test");
        assertThat(captor.getValue().getTopic()).isEqualTo("orders");
        assertThat(captor.getValue().getResetTimestamp()).isEqualTo(1600000000000L);
    }

    private static TopicItem topicItem(String name, String type, long queueNum) {
        TopicItem item = new TopicItem();
        item.setTopic(name);
        item.setTopicType(type);
        item.setQueueNum(queueNum);
        return item;
    }

    private static SubscriptionData subscription(String group) {
        SubscriptionData subscription = new SubscriptionData();
        subscription.setConsumerGroup(group);
        subscription.setConsumeType("CLUSTERING");
        subscription.setMessageModel("CLUSTERING");
        return subscription;
    }

    @Test
    void queryMessagesByMsgIdShouldReturnDetailWithBodyTest() throws Exception {
        DescribeMessageResponse detail = new DescribeMessageResponse();
        detail.setMessageId("MSG-1");
        detail.setShowTopicName("orders");
        detail.setBody("hello body");
        detail.setProducerAddr("1.2.3.4:5000");
        detail.setProduceTime("2024-09-12 14:06:55,591");
        detail.setProperties("{\"UNIQ_KEY\":\"MSG-1\",\"TAGS\":\"tagA\",\"KEYS\":\"keyA\",\"__CLIENT_HOST\":\"1.2.3.4\"}");
        when(client.DescribeMessage(any())).thenReturn(detail);

        List<MessageRecordVO> messages =
                provider.queryMessages(STUDIO_INSTANCE_ID, "orders", "MSG-1", null, null, null, null);

        assertThat(messages).hasSize(1);
        MessageRecordVO record = messages.get(0);
        assertThat(record.getMsgId()).isEqualTo("MSG-1");
        assertThat(record.getTopic()).isEqualTo("orders");
        assertThat(record.getBody()).isEqualTo("hello body");
        assertThat(record.getBornHost()).isEqualTo("1.2.3.4:5000");
        assertThat(record.getTag()).isEqualTo("tagA");
        assertThat(record.getKey()).isEqualTo("keyA");
        assertThat(record.getProperties()).containsEntry("UNIQ_KEY", "MSG-1");
        ArgumentCaptor<DescribeMessageRequest> captor = ArgumentCaptor.forClass(DescribeMessageRequest.class);
        verify(client).DescribeMessage(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getTopic()).isEqualTo("orders");
        assertThat(captor.getValue().getMsgId()).isEqualTo("MSG-1");
    }

    @Test
    void queryMessagesByTopicShouldUseMessageListTest() throws Exception {
        MessageItem one = new MessageItem();
        one.setMsgId("MSG-A");
        one.setTags("tagA");
        one.setKeys("keyA");
        one.setProducerAddr("1.2.3.4:5000");
        one.setProduceTime("2024-09-12 14:06:55,591");
        DescribeMessageListResponse response = new DescribeMessageListResponse();
        response.setData(new MessageItem[]{one});
        when(client.DescribeMessageList(any())).thenReturn(response);

        List<MessageRecordVO> messages = provider.queryMessages(STUDIO_INSTANCE_ID, "orders", null,
                "tagA", "keyA", 1600000000000L, 1600001000000L);

        assertThat(messages).hasSize(1);
        MessageRecordVO record = messages.get(0);
        assertThat(record.getMsgId()).isEqualTo("MSG-A");
        assertThat(record.getTag()).isEqualTo("tagA");
        assertThat(record.getKey()).isEqualTo("keyA");
        assertThat(record.getBornHost()).isEqualTo("1.2.3.4:5000");
        assertThat(record.getStoreTime()).isGreaterThan(0L);
        ArgumentCaptor<DescribeMessageListRequest> captor = ArgumentCaptor.forClass(DescribeMessageListRequest.class);
        verify(client).DescribeMessageList(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getTopic()).isEqualTo("orders");
        assertThat(captor.getValue().getMsgKey()).isEqualTo("keyA");
        assertThat(captor.getValue().getTag()).isEqualTo("tagA");
        assertThat(captor.getValue().getTaskRequestId()).isEqualTo("");
    }

    @Test
    void getMessageTraceShouldMapStagesTest() throws Exception {
        MessageTraceItem produce = new MessageTraceItem();
        produce.setStage("produce");
        produce.setData("{\"MsgId\":\"MSG-1\",\"Status\":0,\"ProduceTime\":\"2024-09-12 14:06:55,591\","
                + "\"ProducerAddr\":\"1.2.3.4:5000\",\"Duration\":2}");
        MessageTraceItem consume = new MessageTraceItem();
        consume.setStage("consume");
        consume.setData("{\"TotalCount\":1,\"RocketMqConsumeLogs\":[{\"MsgId\":\"MSG-1\",\"Status\":2,"
                + "\"PushTime\":\"2024-09-12 14:06:55,600\",\"ConsumerGroup\":\"GID_test\",\"RetryTimes\":1}]}");
        DescribeMessageTraceResponse response = new DescribeMessageTraceResponse();
        response.setData(new MessageTraceItem[]{produce, consume});
        when(client.DescribeMessageTrace(any())).thenReturn(response);

        TraceRecordVO trace = provider.getMessageTrace(STUDIO_INSTANCE_ID, "MSG-1");

        assertThat(trace.getNodes()).hasSize(2);
        TraceNodeVO produceNode = trace.getNodes().get(0);
        assertThat(produceNode.getTitle()).isEqualTo("produce");
        assertThat(produceNode.getStatus()).isEqualTo("finish");
        assertThat(produceNode.getCostTime()).isEqualTo(2L);
        assertThat(produceNode.getTimestamp()).isGreaterThan(0L);
        TraceNodeVO consumeNode = trace.getNodes().get(1);
        assertThat(consumeNode.getTitle()).isEqualTo("consume");
        assertThat(consumeNode.getStatus()).isEqualTo("finish");
        assertThat(trace.getConsumerStatus()).hasSize(1);
        assertThat(trace.getConsumerStatus().get(0).getGroup()).isEqualTo("GID_test");
        assertThat(trace.getConsumerStatus().get(0).getDeliveryStatus()).isEqualTo(DeliveryStatus.success);
        assertThat(trace.getConsumerStatus().get(0).getRetryCount()).isEqualTo(1);
        ArgumentCaptor<DescribeMessageTraceRequest> captor = ArgumentCaptor.forClass(DescribeMessageTraceRequest.class);
        verify(client).DescribeMessageTrace(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getMsgId()).isEqualTo("MSG-1");
    }
}
