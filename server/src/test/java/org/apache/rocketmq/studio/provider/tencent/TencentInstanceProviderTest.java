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

import com.tencentcloudapi.trocket.v20230308.models.CreateTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicResponse;
import com.tencentcloudapi.trocket.v20230308.models.ModifyTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.SubscriptionData;
import com.tencentcloudapi.trocket.v20230308.models.TopicItem;
import com.tencentcloudapi.trocket.v20230308.TrocketClient;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
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
        when(clientFactory.call(anyString(), anyString(), any())).thenAnswer(invocation -> {
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
}
