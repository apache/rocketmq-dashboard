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
import com.tencentcloudapi.trocket.v20230308.models.DeleteTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeConsumerGroupListResponse;
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
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicResponse;
import com.tencentcloudapi.trocket.v20230308.models.Filter;
import com.tencentcloudapi.trocket.v20230308.models.ModifyTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.ResetConsumerGroupOffsetRequest;
import com.tencentcloudapi.trocket.v20230308.models.SubscriptionData;
import com.tencentcloudapi.trocket.v20230308.models.TopicItem;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.DeliveryStatus;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.Pagination;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.ConsumerStatusVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceNodeVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.UUID;

/**
 * Tencent Cloud TDMQ RocketMQ 5.x topic operations backed by Trocket v20230308 OpenAPI.
 *
 * <p>In addition to topic/consumer-group management, message query is supported via
 * DescribeMessageList (list), DescribeMessage (detail) and DescribeMessageTrace (trace).</p>
 */
@RequiredArgsConstructor
@Slf4j
@Component
public class TencentInstanceProvider implements InstanceProvider {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 100;
    static final int CONSUMER_PAGE_SIZE = 100;
    static final int DEFAULT_QUEUE_NUM = 8;
    static final int MIN_QUEUE_NUM = 3;
    static final int MAX_QUEUE_NUM = 16;
    static final int DEFAULT_MAX_RETRY_TIMES = 16;
    static final int MESSAGE_LIMIT = 100;
    private static final DateTimeFormatter TENCENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss[,SSS][,SS]");
    private static final DateTimeFormatter[] TENCENT_TIME_FORMATTERS = {
        TENCENT_TIME_FORMATTER,
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
    };
    private static final long ONE_HOUR_MILLIS = 60L * 60L * 1000L;
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String STAGE_PRODUCE = "produce";
    private static final String STAGE_PERSIST = "persist";
    private static final String STAGE_CONSUME = "consume";

    private final TencentClientFactory clientFactory;
    private final InstanceRepository instanceRepository;

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.TENCENT;
    }

    @Override
    public Set<InstanceCapability> capabilities() {
        return Set.of(
                InstanceCapability.TOPIC_MANAGEMENT,
                InstanceCapability.CONSUMER_GROUP_MANAGEMENT,
                InstanceCapability.MESSAGE_QUERY,
                InstanceCapability.MESSAGE_TRACE,
                InstanceCapability.ACL_MANAGEMENT);
    }

    @Override
    public int countTopics(String instanceId) {
        Context context = resolve(instanceId);
        DescribeTopicListRequest request = new DescribeTopicListRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setOffset(0L);
        request.setLimit(1L);
        DescribeTopicListResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.DescribeTopicList(request));
        if (response == null) {
            return 0;
        }
        // TotalCount is independent of the current page contents and remains authoritative
        // when Tencent returns an empty Data array for the minimal count request.
        Long total = response.getTotalCount();
        if (total != null) {
            if (total <= 0L) {
                return 0;
            }
            return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : total.intValue();
        }
        if (response.getData() == null || response.getData().length == 0) {
            return 0;
        }
        return listTopics(instanceId, null, null, false).size();
    }

    @Override
    public int countGroups(String instanceId) {
        Context context = resolve(instanceId);
        DescribeConsumerGroupListRequest request = new DescribeConsumerGroupListRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setOffset(0L);
        request.setLimit(1L);
        DescribeConsumerGroupListResponse response = clientFactory.call(
                context.credentialId(), context.regionId(), client -> client.DescribeConsumerGroupList(request));
        Long totalCount = response == null ? null : response.getTotalCount();
        if (totalCount == null) {
            return 0;
        }
        if (totalCount < 0L || totalCount > Integer.MAX_VALUE) {
            throw new BusinessException(502,
                    "Tencent Cloud returned an invalid consumer group count: " + totalCount);
        }
        return totalCount.intValue();
    }

    @Override
    public List<TopicVO> listTopics(String instanceId, String type, String search) {
        return listTopics(instanceId, type, search, true);
    }

    @Override
    public PageResult<TopicVO> listTopicsPage(String instanceId, String type, String search, int page, int pageSize) {
        Context context = resolve(instanceId);
        DescribeTopicListResponse response = describeTopics(context, type, search,
                Pagination.pageOffset(page, pageSize), pageSize);
        Long totalCount = response == null ? null : response.getTotalCount();
        if (totalCount == null || totalCount < 0L) {
            return paginate(listTopics(instanceId, type, search, true), page, pageSize);
        }
        return PageResult.of(toTopics(response.getData(), instanceId, context, true), totalCount, page, pageSize);
    }

    private List<TopicVO> listTopics(String instanceId, String type, String search, boolean enrichTimes) {
        Context context = resolve(instanceId);
        List<TopicVO> topics = new ArrayList<>();
        for (long offset = 0L; ; offset += PAGE_SIZE) {
            DescribeTopicListResponse response = describeTopics(context, type, search, offset, PAGE_SIZE);
            TopicItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            topics.addAll(toTopics(data, instanceId, context, enrichTimes));
            if (hasFetchedAll(offset, PAGE_SIZE, response.getTotalCount()) || data.length < PAGE_SIZE) {
                break;
            }
        }
        return topics;
    }

    private DescribeTopicListResponse describeTopics(Context context, String type, String search, long offset, long limit) {
        DescribeTopicListRequest request = new DescribeTopicListRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setOffset(offset);
        request.setLimit(limit);
        Filter[] filters = topicFilters(type, search);
        if (filters.length > 0) {
            request.setFilters(filters);
        }
        return clientFactory.call(context.credentialId(), context.regionId(), client -> client.DescribeTopicList(request));
    }

    private static Filter[] topicFilters(String type, String search) {
        List<Filter> filters = new ArrayList<>(2);
        if (StringUtils.hasText(search)) {
            Filter filter = new Filter();
            filter.setName("TopicName");
            filter.setValues(new String[]{search.trim()});
            filters.add(filter);
        }
        if (StringUtils.hasText(type)) {
            Filter filter = new Filter();
            filter.setName("TopicType");
            filter.setValues(new String[]{type.trim().toUpperCase(Locale.ROOT)});
            filters.add(filter);
        }
        return filters.toArray(Filter[]::new);
    }

    private List<TopicVO> toTopics(TopicItem[] data, String instanceId, Context context, boolean enrichTimes) {
        if (data == null || data.length == 0) {
            return List.of();
        }
        List<TopicVO> topics = new ArrayList<>(data.length);
        for (TopicItem item : data) {
            if (item == null) {
                continue;
            }
            TopicVO topic = toTopic(item, instanceId);
            if (enrichTimes) {
                enrichTopicTimes(context, topic);
            }
            topics.add(topic);
        }
        return topics;
    }

    private static boolean hasFetchedAll(long offset, int pageSize, Long totalCount) {
        return totalCount != null && totalCount >= 0L && offset + pageSize >= totalCount;
    }

    private static PageResult<TopicVO> paginate(List<TopicVO> topics, int page, int pageSize) {
        int total = topics.size();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, total);
        int to = from + (int) Math.min(pageSize, total - from);
        return PageResult.of(topics.subList(from, to), total, page, pageSize);
    }

    /**
     * DescribeTopicList does not expose creation/update timestamps, so resolve them per-topic
     * from DescribeTopic. Kept off the cheap count path to avoid N+1 calls for instance listings.
     */
    private void enrichTopicTimes(Context context, TopicVO topic) {
        try {
            DescribeTopicRequest request = new DescribeTopicRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setTopic(topic.getName());
            DescribeTopicResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeTopic(request));
            if (response == null) {
                return;
            }
            topic.setGmtCreate(toLocalDateTime(response.getCreatedTime()));
            topic.setGmtModified(toLocalDateTime(response.getLastUpdateTime()));
        } catch (BusinessException ignored) {
            // A single topic detail lookup failure should not fail the whole list.
        }
    }

    private static LocalDateTime toLocalDateTime(Long epoch) {
        if (epoch == null || epoch <= 0L) {
            return null;
        }
        // Tencent Cloud returns millisecond timestamps; tolerate second precision as a fallback.
        long epochMillis = epoch >= 10_000_000_000L ? epoch : epoch * 1000L;
        return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime();
    }

    @Override
    public TopicVO createTopic(String instanceId, TopicVO topic) {
        Context context = resolve(instanceId);
        validateCreateTopic(topic);
        CreateTopicRequest request = new CreateTopicRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setTopic(topic.getName());
        request.setTopicType(topic.getType().name());
        request.setQueueNum(queueNum(topic));
        request.setRemark(topic.getRemark());
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.CreateTopic(request));
        topic.setInstanceId(instanceId);
        topic.setPerm(defaultPerm(topic.getPerm()));
        topic.setWriteQueues(queueNum(topic).intValue());
        topic.setReadQueues(queueNum(topic).intValue());
        topic.setGmtCreate(LocalDateTime.now());
        topic.setGmtModified(LocalDateTime.now());
        return topic;
    }

    @Override
    public TopicVO updateTopic(String instanceId, TopicVO topic) {
        Context context = resolve(instanceId);
        validateUpdateTopic(topic);
        ModifyTopicRequest request = new ModifyTopicRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setTopic(topic.getName());
        request.setRemark(topic.getRemark());
        Long requestedQueueNum = null;
        if (topic.getWriteQueues() > 0 || topic.getReadQueues() > 0) {
            requestedQueueNum = queueNum(topic);
            request.setQueueNum(requestedQueueNum);
        }
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.ModifyTopic(request));
        topic.setInstanceId(instanceId);
        topic.setPerm(defaultPerm(topic.getPerm()));
        if (requestedQueueNum != null) {
            topic.setWriteQueues(requestedQueueNum.intValue());
            topic.setReadQueues(requestedQueueNum.intValue());
        }
        topic.setGmtModified(LocalDateTime.now());
        return topic;
    }

    @Override
    public void deleteTopic(String instanceId, String topicName) {
        Context context = resolve(instanceId);
        requireTopicName(topicName);
        DeleteTopicRequest request = new DeleteTopicRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setTopic(topicName);
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.DeleteTopic(request));
    }

    @Override
    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String topicName) {
        Context context = resolve(instanceId);
        requireTopicName(topicName);
        List<TopicConsumerVO> consumers = new ArrayList<>();
        long fetchedSubscriptions = 0L;
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeTopicRequest request = new DescribeTopicRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setTopic(topicName);
            request.setOffset((long) page * CONSUMER_PAGE_SIZE);
            request.setLimit((long) CONSUMER_PAGE_SIZE);
            DescribeTopicResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeTopic(request));
            SubscriptionData[] data = response == null ? null : response.getSubscriptionData();
            if (data == null || data.length == 0) {
                break;
            }
            fetchedSubscriptions += data.length;
            for (SubscriptionData subscription : data) {
                if (subscription != null) {
                    consumers.add(toTopicConsumer(subscription));
                }
            }
            Long subscriptionCount = response.getSubscriptionCount();
            if (data.length < CONSUMER_PAGE_SIZE
                    || subscriptionCount != null && fetchedSubscriptions >= subscriptionCount) {
                break;
            }
        }
        return consumers;
    }

    @Override
    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String search) {
        return listConsumerGroups(instanceId, search, true);
    }

    private List<ConsumerGroupVO> listConsumerGroups(String instanceId, String search, boolean enrichTimes) {
        Context context = resolve(instanceId);
        List<ConsumerGroupVO> groups = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeConsumerGroupListRequest request = new DescribeConsumerGroupListRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeConsumerGroupListResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeConsumerGroupList(request));
            ConsumeGroupItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            for (ConsumeGroupItem item : data) {
                if (item == null) {
                    continue;
                }
                ConsumerGroupVO group = toConsumerGroup(item, instanceId);
                if (matchesSearch(search, group.getName())) {
                    if (enrichTimes) {
                        enrichConsumerGroupDetail(context, group);
                    }
                    groups.add(group);
                }
            }
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        return groups;
    }

    /**
     * DescribeConsumerGroupList exposes limited fields, so resolve the creation timestamp and the
     * real consume model from DescribeConsumerGroup. Kept off the cheap count path to avoid N+1
     * calls for instance listings.
     */
    private void enrichConsumerGroupDetail(Context context, ConsumerGroupVO group) {
        try {
            DescribeConsumerGroupRequest request = new DescribeConsumerGroupRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setConsumerGroup(group.getName());
            DescribeConsumerGroupResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeConsumerGroup(request));
            if (response == null) {
                return;
            }
            group.setGmtCreate(toLocalDateTime(response.getCreatedTime()));
            if (StringUtils.hasText(response.getConsumeModel())) {
                group.setConsumeType(toConsumeType(response.getConsumeModel()));
            }
        } catch (BusinessException ignored) {
            // A single group detail lookup failure should not fail the whole list.
        }
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(String instanceId, ConsumerGroupVO group) {
        Context context = resolve(instanceId);
        validateConsumerGroup(group);
        CreateConsumerGroupRequest request = new CreateConsumerGroupRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setConsumerGroup(group.getName());
        request.setMaxRetryTimes((long) retryMaxTimes(group));
        request.setConsumeEnable(true);
        request.setConsumeMessageOrderly(isOrderly(group));
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.CreateConsumerGroup(request));
        group.setInstanceId(instanceId);
        group.setRetryMaxTimes(retryMaxTimes(group));
        group.setSubscribedTopics(java.util.List.of());
        group.setGmtCreate(LocalDateTime.now());
        group.setGmtModified(LocalDateTime.now());
        return group;
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String groupName) {
        Context context = resolve(instanceId);
        requireGroupName(groupName);
        DeleteConsumerGroupRequest request = new DeleteConsumerGroupRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setConsumerGroup(groupName);
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.DeleteConsumerGroup(request));
    }

    @Override
    public List<QueueProgressVO> getGroupProgress(String instanceId, String groupName) {
        Context context = resolve(instanceId);
        requireGroupName(groupName);
        List<SubscriptionData> subscriptions = listTopicSubscriptionsByGroup(context, groupName);
        List<QueueProgressVO> rows = new ArrayList<>();
        for (SubscriptionData subscription : subscriptions) {
            if (subscription == null) {
                continue;
            }
            rows.add(QueueProgressVO.builder()
                    .topic(subscription.getTopic())
                    .broker("topic:" + subscription.getTopic())
                    .queueId(0)
                    .brokerOffset(0L)
                    .consumerOffset(0L)
                    .diffTotal(subscription.getConsumerLag() == null ? 0L : subscription.getConsumerLag())
                    .build());
        }
        return rows;
    }

    @Override
    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String groupName) {
        Context context = resolve(instanceId);
        requireGroupName(groupName);
        List<SubscriptionData> subscriptions = listTopicSubscriptionsByGroup(context, groupName);
        List<SubscriptionEntryVO> entries = new ArrayList<>();
        for (SubscriptionData subscription : subscriptions) {
            if (subscription == null) {
                continue;
            }
            entries.add(toSubscriptionEntry(subscription));
        }
        return entries;
    }

    @Override
    public void resetOffset(String instanceId, String groupName, long timestamp, String topic) {
        Context context = resolve(instanceId);
        requireGroupName(groupName);
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "Topic is required to reset consumer group offset");
        }
        ResetConsumerGroupOffsetRequest request = new ResetConsumerGroupOffsetRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setConsumerGroup(groupName);
        request.setTopic(topic);
        if (timestamp > 0L) {
            request.setResetTimestamp(timestamp);
        } else {
            request.setResetTimestamp(System.currentTimeMillis());
        }
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.ResetConsumerGroupOffset(request));
    }

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId,
                                               String tag, String key, Long startTime, Long endTime) {
        Context context = resolve(instanceId);
        requireTopic(topic);
        // Querying by message ID returns the full detail (body, properties and tracks) via
        // DescribeMessage, mirroring the msgId path of the base provider.
        if (StringUtils.hasText(msgId)) {
            MessageRecordVO record = toRecordVO(describeMessage(context, topic, msgId));
            return record == null ? Collections.emptyList() : Collections.singletonList(record);
        }

        long end = endTime != null ? endTime : System.currentTimeMillis();
        long begin = startTime != null ? startTime : end - ONE_HOUR_MILLIS;
        if (begin >= end) {
            throw new BusinessException(400, "Message query start time must be before end time");
        }

        // DescribeMessageList is an async, task-based query: each logical query is identified by a
        // TaskRequestId, and paging through that query reuses the same id (the response returns it
        // for the next page). A fresh random id starts a brand-new query. The frontend message table
        // is not server-paginated (pagination=false), so page through the whole result set here.
        String taskRequestId = UUID.randomUUID().toString();
        List<MessageRecordVO> result = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeMessageListRequest request = new DescribeMessageListRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setTopic(topic);
            request.setStartTime(begin);
            request.setEndTime(end);
            // Reuse the task id returned by the previous page so paging continues the same
            // logical query; fall back to the initial random id when the API omits it.
            request.setTaskRequestId(taskRequestId);
            if (StringUtils.hasText(key)) {
                request.setMsgKey(key);
            }
            if (StringUtils.hasText(tag)) {
                request.setTag(tag);
            }
            request.setOffset((long) result.size());
            request.setLimit((long) MESSAGE_LIMIT);
            DescribeMessageListResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeMessageList(request));
            MessageItem[] data = response == null ? null : response.getData();
            long total = response == null ? 0L : (response.getTotalCount() == null ? 0L : response.getTotalCount());
            if (response != null && StringUtils.hasText(response.getTaskRequestId())) {
                taskRequestId = response.getTaskRequestId();
            }
            if (data != null) {
                for (MessageItem item : data) {
                    if (item != null) {
                        result.add(toRecordVO(item, topic));
                    }
                }
            }
            // Stop on the last page (returned fewer rows than requested) or once all results have
            // been collected. Like the Aliyun provider, the short-page check is the primary signal
            // so we do not rely on TotalCount, which may not be populated for every query.
            int returned = data == null ? 0 : data.length;
            // Stop on the last page (returned fewer rows than requested) or once all results have
            // been collected. Like the Aliyun provider, the short-page check is the primary signal
            // so we do not rely on TotalCount, which may not be populated for every query.
            if (isLastPage(returned, total, result.size())) {
                break;
            }
        }
        return result;
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic) {
        Context context = resolve(instanceId);
        requireMsgId(msgId);
        requireTopic(topic);

        DescribeMessageTraceRequest request = new DescribeMessageTraceRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setTopic(topic);
        request.setMsgId(msgId);
        DescribeMessageTraceResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.DescribeMessageTrace(request));

        List<TraceNodeVO> nodes = new ArrayList<>();
        List<ConsumerStatusVO> consumerStatus = new ArrayList<>();
        MessageTraceItem[] items = response == null ? null : response.getData();
        if (items != null) {
            for (MessageTraceItem item : items) {
                buildTraceStage(item, nodes, consumerStatus);
            }
        }
        return TraceRecordVO.builder()
                .nodes(nodes)
                .consumerStatus(consumerStatus)
                .build();
    }

    private DescribeMessageResponse describeMessage(Context context, String topic, String msgId) {
        DescribeMessageRequest request = new DescribeMessageRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setTopic(topic);
        request.setMsgId(msgId);
        return clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.DescribeMessage(request));
    }

    private static void buildTraceStage(MessageTraceItem item, List<TraceNodeVO> nodes,
                                        List<ConsumerStatusVO> consumerStatus) {
        String stage = item == null ? null : item.getStage();
        String data = item == null ? null : item.getData();
        if (!StringUtils.hasText(stage) || !StringUtils.hasText(data)) {
            return;
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(data);
            switch (stage) {
                case STAGE_PRODUCE:
                    nodes.add(buildProduceNode(root));
                    break;
                case STAGE_PERSIST:
                    nodes.add(buildPersistNode(root));
                    break;
                case STAGE_CONSUME:
                    buildConsumeNodes(root, nodes, consumerStatus);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            // Unparseable trace payloads are skipped rather than failing the whole trace.
        }
    }

    private static TraceNodeVO buildProduceNode(JsonNode root) {
        return TraceNodeVO.builder()
                .title(STAGE_PRODUCE)
                .timestamp(parseTraceTime(root.path("ProduceTime").asText(null)))
                .status(toTraceStatus(root.path("Status").asInt(0)))
                .costTime(root.path("Duration").asLong(0L))
                .description("producer=" + root.path("ProducerAddr").asText(""))
                .build();
    }

    private static TraceNodeVO buildPersistNode(JsonNode root) {
        return TraceNodeVO.builder()
                .title(STAGE_PERSIST)
                .timestamp(parseTraceTime(root.path("PersistTime").asText(null)))
                .status(toTraceStatus(root.path("Status").asInt(0)))
                .costTime(0L)
                .description("store persist")
                .build();
    }

    private static void buildConsumeNodes(JsonNode root, List<TraceNodeVO> nodes,
                                          List<ConsumerStatusVO> consumerStatus) {
        JsonNode logs = root.path("RocketMqConsumeLogs");
        if (!logs.isArray()) {
            return;
        }
        for (JsonNode log : logs) {
            String group = log.path("ConsumerGroup").asText("");
            long pushTime = parseTraceTime(log.path("PushTime").asText(null));
            int status = log.path("Status").asInt(0);
            int retryTimes = log.path("RetryTimes").asInt(0);
            nodes.add(TraceNodeVO.builder()
                    .title(STAGE_CONSUME)
                    .timestamp(pushTime)
                    .status(toConsumeTraceStatus(status))
                    .costTime(0L)
                    .description("group=" + group + ", consumer=" + log.path("ConsumerAddr").asText(""))
                    .build());
            consumerStatus.add(ConsumerStatusVO.builder()
                    .group(group)
                    .deliveryStatus(toDeliveryStatus(status))
                    .consumeTime(pushTime)
                    .retryCount(retryTimes)
                    .build());
        }
    }

    private static MessageRecordVO toRecordVO(DescribeMessageResponse response) {
        if (response == null) {
            return null;
        }
        Map<String, String> properties = parseProperties(response.getProperties());
        // DescribeMessage carries the tag and key inside Properties (TAGS / KEYS), not as
        // top-level fields, so surface them onto the record for the message list/detail page.
        return MessageRecordVO.builder()
                .msgId(defaultIfBlank(response.getMessageId(), ""))
                .topic(defaultIfBlank(response.getShowTopicName(), ""))
                .tag(properties.get("TAGS"))
                .key(properties.get("KEYS"))
                .body(response.getBody())
                .bodyEncoding("UTF-8")
                .bodyTruncated(false)
                .storeTime(parseTraceTime(response.getProduceTime()))
                .bornHost(response.getProducerAddr())
                .properties(properties)
                .propertiesTruncated(false)
                .size(0)
                .build();
    }

    private static MessageRecordVO toRecordVO(MessageItem item, String topic) {
        if (item == null) {
            return null;
        }
        return MessageRecordVO.builder()
                .msgId(defaultIfBlank(item.getMsgId(), ""))
                .topic(topic)
                .tag(item.getTags())
                .key(item.getKeys())
                .body(null)
                .bodyEncoding(null)
                .bodyTruncated(false)
                .storeTime(parseTraceTime(item.getProduceTime()))
                .bornHost(item.getProducerAddr())
                .properties(Collections.emptyMap())
                .propertiesTruncated(false)
                .size(0)
                .build();
    }

    private static Map<String, String> parseProperties(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyMap();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(raw);
            if (root == null || !root.isObject()) {
                return Collections.emptyMap();
            }
            Map<String, String> properties = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode value = entry.getValue();
                if (value != null && value.isValueNode() && !value.isNull()) {
                    properties.put(entry.getKey(), value.asText());
                }
            });
            return properties;
        } catch (Exception e) {
            return Collections.emptyMap();
        }
    }

    private static boolean isLastPage(int returned, long total, int collected) {
        // The last page is one that returned no rows or fewer rows than requested. When the API
        // reports a TotalCount, also stop once all expected results have been collected.
        boolean shortPage = returned == 0 || returned < MESSAGE_LIMIT;
        boolean allCollected = total > 0 && collected >= total;
        return shortPage || allCollected;
    }

    private static long parseTraceTime(String value) {
        if (!StringUtils.hasText(value)) {
            log.warn("Tencent message query: empty ProduceTime, storeTime=0");
            return 0L;
        }
        String trimmed = value.trim();
        for (DateTimeFormatter formatter : TENCENT_TIME_FORMATTERS) {
            try {
                return LocalDateTime.parse(trimmed, formatter)
                        .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
            } catch (Exception ignored) {
                // try next format
            }
        }
        try {
            return Long.parseLong(trimmed);
        } catch (NumberFormatException ignored) {
            log.warn("Tencent message query: unparseable ProduceTime={}, storeTime=0", value);
        }
        return 0L;
    }

    private static String toTraceStatus(int status) {
        return status == 0 ? "finish" : "failed";
    }

    private static String toConsumeTraceStatus(int status) {
        // Tencent consume log Status uses the RocketMQ convention where 0/1 are in-flight and
        // 2 means consumed; keep the trace status consistent with toDeliveryStatus and with the
        // frontend TraceNode.status values ('error' | 'wait' | 'process' | 'finish').
        if (status == 2) {
            return "finish";
        }
        if (status == 0 || status == 1) {
            return "process";
        }
        return "error";
    }

    private static DeliveryStatus toDeliveryStatus(int status) {
        // Tencent consume log status: 0/1 in-flight, 2 consumed, others failed.
        switch (status) {
            case 2:
                return DeliveryStatus.success;
            case 0:
            case 1:
                return DeliveryStatus.pending;
            default:
                return DeliveryStatus.failed;
        }
    }

    private static String defaultIfBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static void requireTopic(String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new BusinessException(400, "topic is required for Tencent Cloud message query");
        }
    }

    private static void requireMsgId(String msgId) {
        if (!StringUtils.hasText(msgId)) {
            throw new BusinessException(400, "msgId is required for message trace query");
        }
    }

    private Context resolve(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findByIdentifier(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (!StringUtils.hasText(instance.getCloudInstanceId()) || !StringUtils.hasText(instance.getRegionId())
                || instance.getCredentialId() == null) {
            throw new BusinessException(400, "Instance " + instanceId + " is missing Tencent Cloud binding");
        }
        return new Context(instance.getCloudInstanceId(), instance.getRegionId(), instance.getCredentialId());
    }

    private static TopicVO toTopic(TopicItem item, String instanceId) {
        TopicVO topic = new TopicVO();
        topic.setName(item.getTopic());
        topic.setInstanceId(instanceId);
        topic.setType(toTopicType(item.getTopicType()));
        topic.setWriteQueues(toInteger(item.getQueueNum()));
        topic.setReadQueues(toInteger(item.getQueueNum()));
        topic.setPerm(TopicPerm.RW);
        topic.setRemark(item.getRemark());
        topic.setNamespace(item.getNamespaceV4());
        topic.setClusterId(item.getClusterIdV4());
        return topic;
    }

    private static TopicConsumerVO toTopicConsumer(SubscriptionData subscription) {
        String messageModel = subscription.getMessageModel();
        if (!StringUtils.hasText(messageModel)) {
            messageModel = subscription.getConsumeType();
        }
        return TopicConsumerVO.builder()
                .group(subscription.getConsumerGroup())
                .consumeType(toConsumeType(subscription.getConsumeType(), messageModel))
                .messageModel(messageModel)
                .diffTotal(subscription.getConsumerLag() == null ? 0L : subscription.getConsumerLag())
                .build();
    }

    private static ConsumerGroupVO toConsumerGroup(ConsumeGroupItem item, String instanceId) {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName(item.getConsumerGroup());
        group.setInstanceId(instanceId);
        group.setClusterId(item.getClusterIdV4());
        group.setNamespace(item.getNamespaceV4());
        group.setConsumeType(toConsumeType(item.getConsumeMessageOrderly()));
        group.setDeliveryOrderType(item.getConsumeMessageOrderly() == null || !item.getConsumeMessageOrderly()
                ? "Concurrently" : "Orderly");
        group.setRetryMaxTimes(toInt(item.getMaxRetryTimes()));
        group.setSubscribedTopics(java.util.List.of());
        group.setInstances(java.util.List.of());
        return group;
    }

    private List<SubscriptionData> listTopicSubscriptionsByGroup(Context context, String groupName) {
        List<SubscriptionData> all = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeTopicListByGroupRequest request = new DescribeTopicListByGroupRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setConsumerGroup(groupName);
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeTopicListByGroupResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeTopicListByGroup(request));
            SubscriptionData[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            all.addAll(Arrays.asList(data));
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        return all;
    }

    private static SubscriptionEntryVO toSubscriptionEntry(SubscriptionData subscription) {
        return SubscriptionEntryVO.builder()
                .topic(subscription.getTopic())
                .expression(subscription.getSubString())
                .type(subscription.getExpressionType())
                .filterMode(subscription.getExpressionType())
                .consistency(subscription.getConsistency() == null ? null : String.valueOf(subscription.getConsistency()))
                .build();
    }

    private static void validateConsumerGroup(ConsumerGroupVO group) {
        if (group == null || !StringUtils.hasText(group.getName())) {
            throw new BusinessException(400, "Consumer group name is required");
        }
    }

    private static void requireGroupName(String groupName) {
        if (!StringUtils.hasText(groupName)) {
            throw new BusinessException(400, "Consumer group name is required");
        }
    }

    private static int retryMaxTimes(ConsumerGroupVO group) {
        return group.getRetryMaxTimes() > 0 ? group.getRetryMaxTimes() : DEFAULT_MAX_RETRY_TIMES;
    }

    private static boolean isOrderly(ConsumerGroupVO group) {
        String deliveryOrderType = group.getDeliveryOrderType();
        return deliveryOrderType != null
                && (deliveryOrderType.toUpperCase(Locale.ROOT).contains("FIFO")
                || deliveryOrderType.toUpperCase(Locale.ROOT).contains("ORDER"));
    }

    private static int toInt(Long value) {
        if (value == null || value <= 0L) {
            return 0;
        }
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value.intValue();
    }

    private static ConsumeType toConsumeType(Boolean consumeMessageOrderly) {
        // Tencent consumer groups use clustering consumption; orderly only affects delivery order.
        return ConsumeType.CLUSTERING;
    }

    private static ConsumeType toConsumeType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        if (raw.toUpperCase(Locale.ROOT).contains("BROADCAST")) {
            return ConsumeType.BROADCASTING;
        }
        if (raw.toUpperCase(Locale.ROOT).contains("CLUSTER")) {
            return ConsumeType.CLUSTERING;
        }
        return null;
    }

    private static TopicType toTopicType(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        try {
            return TopicType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static ConsumeType toConsumeType(String consumeType, String messageModel) {
        String value = StringUtils.hasText(consumeType) ? consumeType : messageModel;
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.toUpperCase(Locale.ROOT).contains("BROADCAST")) {
            return ConsumeType.BROADCASTING;
        }
        if (value.toUpperCase(Locale.ROOT).contains("CLUSTER")) {
            return ConsumeType.CLUSTERING;
        }
        return null;
    }

    private static boolean matchesSearch(String search, String value) {
        if (!StringUtils.hasText(search)) {
            return true;
        }
        return contains(value, search.trim().toLowerCase(Locale.ROOT));
    }

    private static boolean contains(String value, String needle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(needle);
    }

    private static void validateCreateTopic(TopicVO topic) {
        validateTopicName(topic);
        if (topic.getType() == null) {
            throw new BusinessException(400, "Topic type is required");
        }
        if (topic.getType() == TopicType.LITE) {
            throw new BusinessException(400, "Tencent Cloud RocketMQ does not support LiteTopic");
        }
        validateQueueNumber(topic);
    }

    private static void validateUpdateTopic(TopicVO topic) {
        validateTopicName(topic);
        validateQueueNumber(topic);
    }

    private static void validateTopicName(TopicVO topic) {
        if (topic == null || !StringUtils.hasText(topic.getName())) {
            throw new BusinessException(400, "Topic name is required");
        }
    }

    private static void validateQueueNumber(TopicVO topic) {
        if (topic.getWriteQueues() < 0 || topic.getReadQueues() < 0) {
            throw new BusinessException(400, "Topic queue number must not be negative");
        }
        if (topic.getWriteQueues() > 0 && topic.getReadQueues() > 0
                && topic.getWriteQueues() != topic.getReadQueues()) {
            throw new BusinessException(400,
                    "Topic write and read queue numbers must match for Tencent Cloud");
        }
        int queueNum = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : topic.getReadQueues();
        if (queueNum > 0 && (queueNum < MIN_QUEUE_NUM || queueNum > MAX_QUEUE_NUM)) {
            throw new BusinessException(400, "Topic queue number must be between 3 and 16");
        }
    }

    private static Long queueNum(TopicVO topic) {
        int queueNum = topic.getWriteQueues() > 0 ? topic.getWriteQueues() : topic.getReadQueues();
        return (long) (queueNum > 0 ? queueNum : DEFAULT_QUEUE_NUM);
    }

    private static TopicPerm defaultPerm(TopicPerm perm) {
        return perm == null ? TopicPerm.RW : perm;
    }

    private static int toInteger(Long value) {
        return value == null ? 0 : Math.toIntExact(value);
    }

    private static void requireTopicName(String topicName) {
        if (!StringUtils.hasText(topicName)) {
            throw new BusinessException(400, "Topic name is required");
        }
    }

    private record Context(String cloudInstanceId, String regionId, Long credentialId) {
    }
}
