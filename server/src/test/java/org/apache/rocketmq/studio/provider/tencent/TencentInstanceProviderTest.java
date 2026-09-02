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
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListByGroupRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListByGroupResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicResponse;
import com.tencentcloudapi.trocket.v20230308.models.Filter;
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
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.TimeZone;
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

    private static final String STUDIO_INSTANCE_ID = "7";
    private static final String STUDIO_INSTANCE_PK = STUDIO_INSTANCE_ID;
    private static final String CLOUD_INSTANCE_ID = "rmq-abc";
    private static final String REGION = "ap-chengdu";
    private static final Long CREDENTIAL_ID = 1L;

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
        lenient().when(instanceRepository.findByIdentifier(STUDIO_INSTANCE_ID)).thenReturn(Optional.of(InstanceVO.builder()
                .name("tencent-prod")
                .vendor(InstanceVendor.TENCENT)
                .cloudInstanceId(CLOUD_INSTANCE_ID)
                .regionId(REGION)
                .credentialId(CREDENTIAL_ID)
                .build()));
        lenient().when(clientFactory.call(any(Long.class), anyString(), any())).thenAnswer(invocation -> {
            TencentClientFactory.TencentCall<Object> action = invocation.getArgument(2);
            return action.execute(client);
        });
    }

    @Test
    void capabilitiesShouldExcludeUnsupportedDlqOperationsTest() {
        assertThat(provider.capabilities())
                .contains(InstanceCapability.TOPIC_MANAGEMENT,
                        InstanceCapability.MESSAGE_QUERY,
                        InstanceCapability.ACL_MANAGEMENT)
                .doesNotContain(InstanceCapability.DLQ_MANAGEMENT);
    }

    @Test
    void countTopicsShouldClampOversizedTotals() throws Exception {
        DescribeTopicListResponse response = new DescribeTopicListResponse();
        response.setData(new TopicItem[]{topicItem("orders", "NORMAL", 8L)});
        response.setTotalCount(Long.MAX_VALUE);
        when(client.DescribeTopicList(any())).thenReturn(response);

        assertThat(provider.countTopics(STUDIO_INSTANCE_ID)).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void countTopicsShouldUseTotalCountFromSingleItemRequestTest() throws Exception {
        DescribeTopicListResponse response = new DescribeTopicListResponse();
        response.setTotalCount(501L);
        response.setData(new TopicItem[]{topicItem("orders", "NORMAL", 8L)});
        when(client.DescribeTopicList(any())).thenReturn(response);

        int count = provider.countTopics(STUDIO_INSTANCE_ID);

        assertThat(count).isEqualTo(501);
        ArgumentCaptor<DescribeTopicListRequest> captor =
                ArgumentCaptor.forClass(DescribeTopicListRequest.class);
        verify(client).DescribeTopicList(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getOffset()).isZero();
        assertThat(captor.getValue().getLimit()).isEqualTo(1L);
    }

    @Test
    void countTopicsShouldUseTotalCountEvenWhenDataIsEmptyTest() throws Exception {
        DescribeTopicListResponse response = new DescribeTopicListResponse();
        response.setTotalCount(501L);
        response.setData(new TopicItem[0]);
        when(client.DescribeTopicList(any())).thenReturn(response);

        assertThat(provider.countTopics(STUDIO_INSTANCE_ID)).isEqualTo(501);
        verify(client, times(1)).DescribeTopicList(any());
    }

    @Test
    void listTopicsShouldMapAndFilterAndEnrichTimesTest() throws Exception {
        TopicItem normal = topicItem("orders", "NORMAL", 8L);
        TopicItem fifo = topicItem("orders-fifo", "FIFO", 4L);
        when(client.DescribeTopicList(any())).thenAnswer(invocation -> {
            DescribeTopicListRequest request = invocation.getArgument(0);
            DescribeTopicListResponse response = new DescribeTopicListResponse();
            Filter[] filters = request.getFilters();
            if (filters != null && filters.length == 2) {
                response.setData(new TopicItem[]{fifo});
            } else {
                response.setData(new TopicItem[]{normal, fifo});
            }
            return response;
        });
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
        assertThat(topics.get(0).getInstanceId()).isEqualTo(STUDIO_INSTANCE_PK);
        assertThat(topics.get(0).getGmtCreate())
                .isEqualTo(java.time.Instant.ofEpochMilli(1600000000000L)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        assertThat(topics.get(0).getGmtModified())
                .isEqualTo(java.time.Instant.ofEpochMilli(1600000100000L)
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        ArgumentCaptor<DescribeTopicListRequest> captor =
                ArgumentCaptor.forClass(DescribeTopicListRequest.class);
        verify(client).DescribeTopicList(captor.capture());
        assertThat(captor.getValue().getFilters()).extracting(Filter::getName)
                .containsExactly("TopicName", "TopicType");
        assertThat(captor.getValue().getFilters()[0].getValues()).containsExactly("fifo");
        assertThat(captor.getValue().getFilters()[1].getValues()).containsExactly("FIFO");
    }

    @Test
    void listTopicsPageShouldUseTencentNativePaginationAndFiltersTest() throws Exception {
        TopicItem item = topicItem("orders-fifo-10000", "FIFO", 8L);
        DescribeTopicListResponse response = new DescribeTopicListResponse();
        response.setTotalCount(10001L);
        response.setData(new TopicItem[]{item});
        when(client.DescribeTopicList(any())).thenReturn(response);
        DescribeTopicResponse detail = new DescribeTopicResponse();
        detail.setCreatedTime(1700000000000L);
        detail.setLastUpdateTime(1700000100000L);
        when(client.DescribeTopic(any())).thenReturn(detail);

        var page = provider.listTopicsPage(STUDIO_INSTANCE_ID, "fifo", "orders", 101, 100);

        assertThat(page.getTotal()).isEqualTo(10001L);
        assertThat(page.getPage()).isEqualTo(101);
        assertThat(page.getSize()).isEqualTo(100);
        assertThat(page.getItems()).extracting(TopicVO::getName).containsExactly("orders-fifo-10000");
        ArgumentCaptor<DescribeTopicListRequest> captor =
                ArgumentCaptor.forClass(DescribeTopicListRequest.class);
        verify(client).DescribeTopicList(captor.capture());
        assertThat(captor.getValue().getOffset()).isEqualTo(10000L);
        assertThat(captor.getValue().getLimit()).isEqualTo(100L);
        assertThat(captor.getValue().getFilters()).extracting(Filter::getName)
                .containsExactly("TopicName", "TopicType");
    }

    @Test
    void countTopicsShouldFallBackToCompleteListingPastLegacyTenThousandCapTest() throws Exception {
        when(client.DescribeTopicList(any())).thenAnswer(invocation -> {
            DescribeTopicListRequest request = invocation.getArgument(0);
            DescribeTopicListResponse response = new DescribeTopicListResponse();
            if (request.getLimit() == 1L && request.getOffset() == 0L) {
                response.setData(new TopicItem[]{topicItem("seed", "NORMAL", 8L)});
                return response;
            }
            int start = request.getOffset().intValue();
            int count = Math.min(request.getLimit().intValue(), Math.max(10001 - start, 0));
            response.setTotalCount(10001L);
            response.setData(IntStream.range(0, count)
                    .mapToObj(index -> topicItem("topic-" + (start + index), "NORMAL", 8L))
                    .toArray(TopicItem[]::new));
            return response;
        });

        assertThat(provider.countTopics(STUDIO_INSTANCE_ID)).isEqualTo(10001);
        ArgumentCaptor<DescribeTopicListRequest> captor =
                ArgumentCaptor.forClass(DescribeTopicListRequest.class);
        verify(client, times(102)).DescribeTopicList(captor.capture());
        assertThat(captor.getAllValues().get(0).getLimit()).isEqualTo(1L);
        assertThat(captor.getAllValues().get(101).getOffset()).isEqualTo(10000L);
    }

    @Test
    void countGroupsShouldUseTencentTotalCountTest() throws Exception {
        DescribeConsumerGroupListResponse response = new DescribeConsumerGroupListResponse();
        response.setTotalCount(37L);
        when(client.DescribeConsumerGroupList(any())).thenReturn(response);

        int count = provider.countGroups(STUDIO_INSTANCE_ID);

        ArgumentCaptor<DescribeConsumerGroupListRequest> captor =
                ArgumentCaptor.forClass(DescribeConsumerGroupListRequest.class);
        verify(client).DescribeConsumerGroupList(captor.capture());
        assertThat(captor.getValue().getInstanceId()).isEqualTo(CLOUD_INSTANCE_ID);
        assertThat(captor.getValue().getOffset()).isZero();
        assertThat(captor.getValue().getLimit()).isEqualTo(1L);
        assertThat(count).isEqualTo(37);
    }

    @Test
    void countGroupsShouldTreatMissingTotalCountAsZeroTest() throws Exception {
        when(client.DescribeConsumerGroupList(any())).thenReturn(new DescribeConsumerGroupListResponse());

        assertThat(provider.countGroups(STUDIO_INSTANCE_ID)).isZero();
    }

    @Test
    void countGroupsShouldTreatNullResponseAsZeroTest() throws Exception {
        when(client.DescribeConsumerGroupList(any())).thenReturn(null);

        assertThat(provider.countGroups(STUDIO_INSTANCE_ID)).isZero();
    }

    @Test
    void countGroupsShouldRejectCountsOutsideIntegerRangeTest() throws Exception {
        DescribeConsumerGroupListResponse response = new DescribeConsumerGroupListResponse();
        response.setTotalCount((long) Integer.MAX_VALUE + 1L);
        when(client.DescribeConsumerGroupList(any())).thenReturn(response);

        assertThatThrownBy(() -> provider.countGroups(STUDIO_INSTANCE_ID))
                .hasMessage("Tencent Cloud returned an invalid consumer group count: 2147483648");
    }

    @Test
    void countGroupsShouldRejectNegativeCountsTest() throws Exception {
        DescribeConsumerGroupListResponse response = new DescribeConsumerGroupListResponse();
        response.setTotalCount(-1L);
        when(client.DescribeConsumerGroupList(any())).thenReturn(response);

        assertThatThrownBy(() -> provider.countGroups(STUDIO_INSTANCE_ID))
                .hasMessage("Tencent Cloud returned an invalid consumer group count: -1");
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
        assertThat(created.getInstanceId()).isEqualTo(STUDIO_INSTANCE_PK);
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
    void getTopicConsumersShouldStopAfterSubscriptionCountReachedOnFullPageTest() throws Exception {
        DescribeTopicResponse firstPage = new DescribeTopicResponse();
        firstPage.setSubscriptionCount(200L);
        firstPage.setSubscriptionData(IntStream.range(0, 100)
                .mapToObj(index -> subscription("GID_" + index))
                .toArray(SubscriptionData[]::new));
        DescribeTopicResponse secondPage = new DescribeTopicResponse();
        secondPage.setSubscriptionCount(200L);
        secondPage.setSubscriptionData(IntStream.range(100, 200)
                .mapToObj(index -> subscription("GID_" + index))
                .toArray(SubscriptionData[]::new));
        when(client.DescribeTopic(any())).thenReturn(firstPage, secondPage);

        List<TopicConsumerVO> consumers = provider.getTopicConsumers(STUDIO_INSTANCE_ID, "orders");

        assertThat(consumers).hasSize(200);
        assertThat(consumers.get(199).getGroup()).isEqualTo("GID_199");
        ArgumentCaptor<DescribeTopicRequest> captor = ArgumentCaptor.forClass(DescribeTopicRequest.class);
        verify(client, times(2)).DescribeTopic(captor.capture());
        assertThat(captor.getAllValues())
                .extracting(DescribeTopicRequest::getOffset)
                .containsExactly(0L, 100L);
        assertThat(captor.getAllValues())
                .extracting(DescribeTopicRequest::getLimit)
                .containsOnly(100L);
    }

    @Test
    void listConsumerGroupsShouldClampOversizedRetryCounts() throws Exception {
        ConsumeGroupItem item = new ConsumeGroupItem();
        item.setConsumerGroup("GID_test");
        item.setMaxRetryTimes(Long.MAX_VALUE);
        DescribeConsumerGroupListResponse response = new DescribeConsumerGroupListResponse();
        response.setData(new ConsumeGroupItem[]{item});
        when(client.DescribeConsumerGroupList(any())).thenReturn(response);

        assertThat(provider.listConsumerGroups(STUDIO_INSTANCE_ID, null))
                .singleElement()
                .satisfies(group -> assertThat(group.getRetryMaxTimes()).isEqualTo(Integer.MAX_VALUE));
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
        assertThat(groups.get(0).getInstanceId()).isEqualTo(STUDIO_INSTANCE_PK);
        assertThat(groups.get(0).getRetryMaxTimes()).isEqualTo(16);
        assertThat(groups.get(0).getGmtCreate()).isNotNull();
        assertThat(groups.get(0).getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(groups.get(0).getInstances()).isNotNull().isEmpty();
    }

    @Test
    void listConsumerGroupsShouldContinuePastTenThousandRecordsWhenTotalCountRequiresItTest() throws Exception {
        ConsumeGroupItem item = new ConsumeGroupItem();
        item.setConsumerGroup("GID_page");
        when(client.DescribeConsumerGroupList(any())).thenAnswer(invocation -> {
            DescribeConsumerGroupListRequest request = invocation.getArgument(0);
            DescribeConsumerGroupListResponse response = new DescribeConsumerGroupListResponse();
            response.setTotalCount(10_001L);
            response.setData(request.getOffset() < 10_000L
                    ? IntStream.range(0, 100).mapToObj(index -> item).toArray(ConsumeGroupItem[]::new)
                    : new ConsumeGroupItem[]{item});
            return response;
        });

        assertThat(provider.listConsumerGroups(STUDIO_INSTANCE_ID, "does-not-match")).isEmpty();

        ArgumentCaptor<DescribeConsumerGroupListRequest> captor =
                ArgumentCaptor.forClass(DescribeConsumerGroupListRequest.class);
        verify(client, times(101)).DescribeConsumerGroupList(captor.capture());
        assertThat(captor.getAllValues().get(100).getOffset()).isEqualTo(10_000L);
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
        assertThat(created.getInstanceId()).isEqualTo(STUDIO_INSTANCE_PK);
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
    void previewResetOffsetShouldAllowLimitedCloudPreviewTest() throws Exception {
        SubscriptionData subscription = new SubscriptionData();
        subscription.setTopic("orders");
        subscription.setConsumerLag(42L);
        DescribeTopicListByGroupResponse response = new DescribeTopicListByGroupResponse();
        response.setData(new SubscriptionData[]{subscription});
        when(client.DescribeTopicListByGroup(any())).thenReturn(response);

        ResetConsumerOffsetPreviewVO preview = provider.previewResetOffset(
                STUDIO_INSTANCE_ID, "GID_test", 1600000000000L, "orders");

        assertThat(preview.isComplete()).isFalse();
        assertThat(preview.isAllowReset()).isTrue();
        assertThat(preview.getQueueCount()).isEqualTo(1);
        assertThat(preview.getCurrentTotalLag()).isEqualTo(42L);
        assertThat(preview.getProjectedTotalLag()).isEqualTo(-1L);
        assertThat(preview.getWarnings())
                .containsExactly("Provider does not expose per-queue target offset preview; confirm with current lag only");
        assertThat(preview.getQueues().get(0).getTargetOffset()).isEqualTo(-1L);
        assertThat(preview.getQueues().get(0).getRiskLevel()).isEqualTo("WARNING");
    }

    @Test
    void getGroupSubscriptionsShouldFetchExactlyTenThousandTencentSubscriptionsTest() throws Exception {
        when(client.DescribeTopicListByGroup(any())).thenAnswer(invocation -> {
            DescribeTopicListByGroupRequest request = invocation.getArgument(0);
            DescribeTopicListByGroupResponse response = new DescribeTopicListByGroupResponse();
            response.setTotalCount(10000L);
            response.setData(subscriptionPage(request.getOffset(), request.getLimit(), 10000));
            return response;
        });

        assertThat(provider.getGroupSubscriptions(STUDIO_INSTANCE_ID, "GID_test")).hasSize(10000);
        verify(client, times(100)).DescribeTopicListByGroup(any());
    }

    @Test
    void getGroupProgressShouldFetchPastLegacyTenThousandTencentSubscriptionCapTest() throws Exception {
        when(client.DescribeTopicListByGroup(any())).thenAnswer(invocation -> {
            DescribeTopicListByGroupRequest request = invocation.getArgument(0);
            DescribeTopicListByGroupResponse response = new DescribeTopicListByGroupResponse();
            response.setTotalCount(10001L);
            response.setData(subscriptionPage(request.getOffset(), request.getLimit(), 10001));
            return response;
        });

        assertThat(provider.getGroupProgress(STUDIO_INSTANCE_ID, "GID_test")).hasSize(10001);
        verify(client, times(101)).DescribeTopicListByGroup(any());
    }

    @Test
    void getGroupSubscriptionsShouldStopOnShortPageWhenTencentTotalCountIsMissingTest() throws Exception {
        when(client.DescribeTopicListByGroup(any())).thenAnswer(invocation -> {
            DescribeTopicListByGroupRequest request = invocation.getArgument(0);
            DescribeTopicListByGroupResponse response = new DescribeTopicListByGroupResponse();
            response.setData(subscriptionPage(request.getOffset(), request.getLimit(), 150));
            return response;
        });

        assertThat(provider.getGroupSubscriptions(STUDIO_INSTANCE_ID, "GID_test")).hasSize(150);
        verify(client, times(2)).DescribeTopicListByGroup(any());
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

    private static SubscriptionData[] subscriptionPage(Long offset, Long limit, int total) {
        int start = offset == null ? 0 : offset.intValue();
        int size = Math.min(limit == null ? TencentInstanceProvider.PAGE_SIZE : limit.intValue(),
                Math.max(total - start, 0));
        return IntStream.range(0, size)
                .mapToObj(index -> {
                    SubscriptionData subscription = subscription("GID_test");
                    subscription.setTopic("topic-" + (start + index));
                    subscription.setSubString("*");
                    subscription.setExpressionType("TAG");
                    subscription.setConsumerLag((long) start + index);
                    return subscription;
                })
                .toArray(SubscriptionData[]::new);
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
    void getMessageTraceShouldPreserveOffsetsForAllTimestampedStagesTest() throws Exception {
        MessageTraceItem produce = new MessageTraceItem();
        produce.setStage("produce");
        produce.setData("{\"Status\":0,\"ProduceTime\":\"2026-08-20T10:00:00.000+0800\"}");
        MessageTraceItem persist = new MessageTraceItem();
        persist.setStage("persist");
        persist.setData("{\"Status\":0,\"PersistTime\":\"2026-08-20T10:00:01.000+0800\"}");
        MessageTraceItem consume = new MessageTraceItem();
        consume.setStage("consume");
        consume.setData("{\"RocketMqConsumeLogs\":[{\"Status\":2,"
                + "\"PushTime\":\"2026-08-20T10:00:02.000+0800\","
                + "\"ConsumerGroup\":\"GID_test\"}]}");
        DescribeMessageTraceResponse response = new DescribeMessageTraceResponse();
        response.setData(new MessageTraceItem[]{produce, persist, consume});
        when(client.DescribeMessageTrace(any())).thenReturn(response);

        TimeZone original = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            TraceRecordVO trace = provider.getMessageTrace(STUDIO_INSTANCE_ID, "MSG-OFFSET", "orders");

            assertThat(trace.getNodes()).extracting(TraceNodeVO::getTimestamp).containsExactly(
                    java.time.Instant.parse("2026-08-20T02:00:00Z").toEpochMilli(),
                    java.time.Instant.parse("2026-08-20T02:00:01Z").toEpochMilli(),
                    java.time.Instant.parse("2026-08-20T02:00:02Z").toEpochMilli());
            assertThat(trace.getConsumerStatus()).hasSize(1);
            assertThat(trace.getConsumerStatus().get(0).getConsumeTime())
                    .isEqualTo(java.time.Instant.parse("2026-08-20T02:00:02Z").toEpochMilli());
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void queryMessagesShouldPreserveExplicitTimestampOffsetsAcrossJvmTimezonesTest() throws Exception {
        DescribeMessageResponse detail = new DescribeMessageResponse();
        detail.setMessageId("MSG-OFFSET");
        detail.setShowTopicName("orders");
        when(client.DescribeMessage(any())).thenReturn(detail);

        TimeZone original = TimeZone.getDefault();
        long expected = java.time.Instant.parse("2026-08-20T02:00:00Z").toEpochMilli();
        List<String> timestamps = List.of(
                "2026-08-20T10:00:00.000+0800",
                "2026-08-20T10:00:00,000+0800",
                "2026-08-20T10:00:00.000+08:00",
                "2026-08-20T02:00:00Z"
        );
        try {
            for (String zone : List.of("UTC", "Asia/Shanghai")) {
                TimeZone.setDefault(TimeZone.getTimeZone(zone));
                for (String timestamp : timestamps) {
                    detail.setProduceTime(timestamp);
                    MessageRecordVO record = provider.queryMessages(
                            STUDIO_INSTANCE_ID, "orders", "MSG-OFFSET", null, null, null, null).get(0);
                    assertThat(record.getStoreTime())
                            .as("timestamp %s in JVM timezone %s", timestamp, zone)
                            .isEqualTo(expected);
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @Test
    void queryMessagesShouldSkipNullAndStructuredPropertyValues() throws Exception {
        DescribeMessageResponse detail = new DescribeMessageResponse();
        detail.setMessageId("MSG-1");
        detail.setShowTopicName("orders");
        detail.setProperties("{\"KEYS\":\"keyA\",\"TAGS\":null,"
                + "\"nested\":{\"x\":1},\"retry\":3,\"enabled\":true}");
        when(client.DescribeMessage(any())).thenReturn(detail);

        MessageRecordVO record = provider.queryMessages(
                STUDIO_INSTANCE_ID, "orders", "MSG-1", null, null, null, null).get(0);

        assertThat(record.getProperties())
                .containsEntry("KEYS", "keyA")
                .containsEntry("retry", "3")
                .containsEntry("enabled", "true")
                .doesNotContainKeys("TAGS", "nested");
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
        response.setTotalCount(1L);
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
        assertThat(captor.getValue().getTaskRequestId()).isNotBlank();
        assertThat(captor.getValue().getOffset()).isEqualTo(0L);
    }

    @Test
    void queryMessagesByTopicShouldPageWithSameTaskRequestIdTest() throws Exception {
        // Page 1 returns a full page (MESSAGE_LIMIT), forcing a second page; page 2 is a short
        // page, which terminates the loop.
        MessageItem[] page1Items = new MessageItem[TencentInstanceProvider.MESSAGE_LIMIT];
        for (int i = 0; i < page1Items.length; i++) {
            MessageItem item = new MessageItem();
            item.setMsgId("MSG-" + (i + 1));
            item.setProduceTime("2024-09-12 14:06:55,591");
            page1Items[i] = item;
        }
        MessageItem last = new MessageItem();
        last.setMsgId("MSG-LAST");
        last.setProduceTime("2024-09-12 14:06:56,591");
        DescribeMessageListResponse page1 = new DescribeMessageListResponse();
        page1.setData(page1Items);
        DescribeMessageListResponse page2 = new DescribeMessageListResponse();
        page2.setData(new MessageItem[]{last});
        when(client.DescribeMessageList(any()))
                .thenReturn(page1)
                .thenReturn(page2);

        List<MessageRecordVO> messages = provider.queryMessages(STUDIO_INSTANCE_ID, "orders", null,
                null, null, 1600000000000L, 1600001000000L);

        assertThat(messages).hasSize(TencentInstanceProvider.MESSAGE_LIMIT + 1);
        assertThat(messages.get(0).getMsgId()).isEqualTo("MSG-1");
        assertThat(messages.get(TencentInstanceProvider.MESSAGE_LIMIT - 1).getMsgId())
                .isEqualTo("MSG-" + TencentInstanceProvider.MESSAGE_LIMIT);
        assertThat(messages.get(TencentInstanceProvider.MESSAGE_LIMIT).getMsgId()).isEqualTo("MSG-LAST");
        ArgumentCaptor<DescribeMessageListRequest> captor = ArgumentCaptor.forClass(DescribeMessageListRequest.class);
        verify(client, org.mockito.Mockito.times(2)).DescribeMessageList(captor.capture());
        java.util.List<DescribeMessageListRequest> requests = captor.getAllValues();
        assertThat(requests).hasSize(2);
        assertThat(requests.get(0).getOffset()).isEqualTo(0L);
        assertThat(requests.get(1).getOffset()).isEqualTo((long) TencentInstanceProvider.MESSAGE_LIMIT);
        // A single logical query reuses the same TaskRequestId across pages; only offset advances.
        assertThat(requests.get(1).getTaskRequestId())
                .isEqualTo(requests.get(0).getTaskRequestId())
                .isNotBlank();
    }

    @Test
    void messageQueryReusesTaskRequestIdReturnedByPreviousPage() throws Exception {
        MessageItem[] page1Items = new MessageItem[TencentInstanceProvider.MESSAGE_LIMIT];
        for (int i = 0; i < TencentInstanceProvider.MESSAGE_LIMIT; i++) {
            MessageItem item = new MessageItem();
            item.setMsgId("MSG-" + (i + 1));
            item.setProduceTime("2024-09-12 14:06:55,591");
            page1Items[i] = item;
        }
        MessageItem last = new MessageItem();
        last.setMsgId("MSG-LAST");
        last.setProduceTime("2024-09-12 14:06:56,591");
        DescribeMessageListResponse page1 = new DescribeMessageListResponse();
        page1.setData(page1Items);
        page1.setTaskRequestId("task-abc");
        DescribeMessageListResponse page2 = new DescribeMessageListResponse();
        page2.setData(new MessageItem[]{last});
        page2.setTaskRequestId("task-abc");
        when(client.DescribeMessageList(any()))
                .thenReturn(page1)
                .thenReturn(page2);

        provider.queryMessages(STUDIO_INSTANCE_ID, "orders", null, null, null,
                1600000000000L, 1600001000000L);

        ArgumentCaptor<DescribeMessageListRequest> captor = ArgumentCaptor.forClass(DescribeMessageListRequest.class);
        verify(client, org.mockito.Mockito.times(2)).DescribeMessageList(captor.capture());
        java.util.List<DescribeMessageListRequest> requests = captor.getAllValues();
        // The second page must carry the task id returned by the first page, not a fresh random id.
        assertThat(requests.get(1).getTaskRequestId()).isEqualTo("task-abc");
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

        TraceRecordVO trace = provider.getMessageTrace(STUDIO_INSTANCE_ID, "MSG-1", "orders");

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
        assertThat(captor.getValue().getTopic()).isEqualTo("orders");
        assertThat(captor.getValue().getMsgId()).isEqualTo("MSG-1");
    }

    @Test
    void getMessageTraceMarksInFlightConsumeAsProcessNotFailed() throws Exception {
        MessageTraceItem consume = new MessageTraceItem();
        consume.setStage("consume");
        consume.setData("{\"TotalCount\":1,\"RocketMqConsumeLogs\":[{\"MsgId\":\"MSG-2\",\"Status\":1,"
                + "\"PushTime\":\"2024-09-12 14:06:55,600\",\"ConsumerGroup\":\"GID_test\",\"RetryTimes\":0}]}");
        DescribeMessageTraceResponse response = new DescribeMessageTraceResponse();
        response.setData(new MessageTraceItem[]{consume});
        when(client.DescribeMessageTrace(any())).thenReturn(response);

        TraceRecordVO trace = provider.getMessageTrace(STUDIO_INSTANCE_ID, "MSG-2", "orders");

        assertThat(trace.getNodes()).hasSize(1);
        // In-flight (Status 1) must read "process", consistent with toDeliveryStatus and the
        // frontend TraceNode status union, not "failed".
        assertThat(trace.getNodes().get(0).getStatus()).isEqualTo("process");
        assertThat(trace.getConsumerStatus().get(0).getDeliveryStatus())
                .isEqualTo(DeliveryStatus.pending);
    }
}
