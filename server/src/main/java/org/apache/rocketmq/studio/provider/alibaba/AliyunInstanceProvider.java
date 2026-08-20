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

import com.aliyun.sdk.service.rocketmq20220801.models.CreateConsumerGroupRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.CreateTopicRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.DeleteConsumerGroupRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.DeleteTopicRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.GetTraceResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupSubscriptionsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupSubscriptionsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupSubscriptionsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListConsumerGroupsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListMessagesResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicSubscriptionsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicSubscriptionsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicSubscriptionsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsResponse;
import com.aliyun.sdk.service.rocketmq20220801.models.ListTopicsResponseBody;
import com.aliyun.sdk.service.rocketmq20220801.models.ResetConsumeOffsetRequest;
import com.aliyun.sdk.service.rocketmq20220801.models.UpdateTopicRequest;
import org.springframework.util.StringUtils;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.Pagination;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceCapability;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Aliyun RocketMQ 5.x implementation of the instance-scoped operations SPI, backed by the
 * OpenAPI async SDK through {@link AliyunClientFactory}.
 */
@RequiredArgsConstructor
@Component
public class AliyunInstanceProvider implements InstanceProvider {

    private static final String DEFAULT_DELIVERY_ORDER_TYPE = "Concurrently";
    private static final String ORDERLY_DELIVERY_ORDER_TYPE = "Orderly";
    private static final String DEFAULT_RETRY_POLICY = "DefaultRetryPolicy";
    private static final String FIXED_RETRY_POLICY = "FixedRetryPolicy";
    private static final int DEFAULT_MAX_RETRY_TIMES = 16;
    private static final int DEFAULT_FIXED_RETRY_INTERVAL_SECONDS = 10;
    private static final int COUNT_PAGE_SIZE = 10;
    private static final String RESET_TYPE_SPECIFIED_TIME = "SPECIFIED_TIME";
    private static final String RESET_TYPE_LATEST_OFFSET = "LATEST_OFFSET";

    private final AliyunClientFactory clientFactory;
    private final InstanceRepository instanceRepository;

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.ALIYUN;
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
        Context ctx = resolve(instanceId);
        ListTopicsRequest request = ListTopicsRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .pageNumber(1)
                .pageSize(COUNT_PAGE_SIZE)
                .build();
        ListTopicsResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.listTopics(request));
        ListTopicsResponseBody body = response == null ? null : response.getBody();
        ListTopicsResponseBody.Data data = body == null ? null : body.getData();
        Long totalCount = data == null ? null : data.getTotalCount();
        return totalCount == null || totalCount < 0
                ? listTopics(instanceId, null, null).size()
                : boundedCount(totalCount);
    }

    @Override
    public int countGroups(String instanceId) {
        Context ctx = resolve(instanceId);
        ListConsumerGroupsRequest request = ListConsumerGroupsRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .pageNumber(1)
                .pageSize(COUNT_PAGE_SIZE)
                .build();
        ListConsumerGroupsResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.listConsumerGroups(request));
        ListConsumerGroupsResponseBody body = response == null ? null : response.getBody();
        ListConsumerGroupsResponseBody.Data data = body == null ? null : body.getData();
        Long totalCount = data == null ? null : data.getTotalCount();
        return totalCount == null || totalCount < 0
                ? listConsumerGroups(instanceId, null).size()
                : boundedCount(totalCount);
    }

    private int boundedCount(long totalCount) {
        return totalCount > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalCount;
    }

    @Override
    public List<TopicVO> listTopics(String instanceId, String type, String search) {
        Context ctx = resolve(instanceId);
        List<TopicVO> topics = new ArrayList<>();
        for (int page = 1; ; page++) {
            ListTopicsResponseBody.Data data = fetchTopicPage(ctx, type, search, page, AliyunConverters.PAGE_SIZE);
            List<ListTopicsResponseBody.List> list = data == null ? null : data.getList();
            if (list == null || list.isEmpty()) {
                break;
            }
            topics.addAll(toTopics(list, instanceId));
            if (hasFetchedAll(page, AliyunConverters.PAGE_SIZE, data.getTotalCount())
                    || list.size() < AliyunConverters.PAGE_SIZE) {
                break;
            }
        }

        return topics;
    }

    @Override
    public PageResult<TopicVO> listTopicsPage(String instanceId, String type, String search, int page, int pageSize) {
        Context ctx = resolve(instanceId);
        ListTopicsResponseBody.Data data = fetchTopicPage(ctx, type, search, page, pageSize);
        Long totalCount = data == null ? null : data.getTotalCount();
        if (totalCount == null || totalCount < 0L) {
            return paginate(listTopics(instanceId, type, search), page, pageSize);
        }
        return PageResult.of(toTopics(data.getList(), instanceId), boundedCount(totalCount), page, pageSize);
    }

    private ListTopicsResponseBody.Data fetchTopicPage(Context ctx, String type, String search, int page, int pageSize) {
        ListTopicsRequest request = buildTopicRequest(ctx.cloudInstanceId(), type, search, page, pageSize);
        ListTopicsResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.listTopics(request));
        ListTopicsResponseBody body = response == null ? null : response.getBody();
        return body == null ? null : body.getData();
    }

    private ListTopicsRequest buildTopicRequest(String cloudInstanceId, String type, String search,
            int page, int pageSize) {
        ListTopicsRequest.Builder builder = ListTopicsRequest.builder()
                .instanceId(cloudInstanceId)
                .pageNumber(page)
                .pageSize(pageSize);
        if (StringUtils.hasText(search)) {
            builder.filter(search);
        }
        if (StringUtils.hasText(type)) {
            builder.messageTypes(List.of(type.trim().toUpperCase(Locale.ROOT)));
        }
        return builder.build();
    }

    private static List<TopicVO> toTopics(List<ListTopicsResponseBody.List> rows, String instanceId) {
        if (rows == null || rows.isEmpty()) {
            return List.of();
        }
        List<TopicVO> topics = new ArrayList<>(rows.size());
        for (ListTopicsResponseBody.List row : rows) {
            if (row != null) {
                topics.add(AliyunConverters.toTopicVO(row, instanceId));

            }
        }
        return topics;
    }

    private static boolean hasFetchedAll(int page, int pageSize, Long totalCount) {
        return totalCount != null && totalCount >= 0L && (long) page * pageSize >= totalCount;
    }

    private static PageResult<TopicVO> paginate(List<TopicVO> topics, int page, int pageSize) {
        int total = topics.size();
        long offset = Pagination.pageOffset(page, pageSize);
        int from = (int) Math.min(offset, total);
        int to = from + (int) Math.min(pageSize, total - from);
        return PageResult.of(topics.subList(from, to), total, page, pageSize);
    }

    @Override
    public TopicVO createTopic(String instanceId, TopicVO topic) {
        Context ctx = resolve(instanceId);
        if (topic == null || !StringUtils.hasText(topic.getName())) {
            throw new BusinessException(400, "Topic name is required");
        }
        if (topic.getType() == null) {
            throw new BusinessException(400, "Topic type is required");
        }
        CreateTopicRequest request = CreateTopicRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .topicName(topic.getName())
                .messageType(topic.getType().name())
                .remark(topic.getRemark())
                .build();
        clientFactory.call(ctx.credentialId(), ctx.regionId(), client -> client.createTopic(request));
        topic.setInstanceId(instanceId);
        topic.setGmtCreate(java.time.LocalDateTime.now());
        topic.setGmtModified(java.time.LocalDateTime.now());
        return topic;
    }

    @Override
    public TopicVO updateTopic(String instanceId, TopicVO topic) {
        Context ctx = resolve(instanceId);
        if (topic == null || !StringUtils.hasText(topic.getName())) {
            throw new BusinessException(400, "Topic name is required");
        }
        UpdateTopicRequest request = UpdateTopicRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .topicName(topic.getName())
                .remark(topic.getRemark())
                .build();
        clientFactory.call(ctx.credentialId(), ctx.regionId(), client -> client.updateTopic(request));
        topic.setInstanceId(instanceId);
        return topic;
    }

    @Override
    public void deleteTopic(String instanceId, String topicName) {
        Context ctx = resolve(instanceId);
        DeleteTopicRequest request = DeleteTopicRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .topicName(topicName)
                .build();
        clientFactory.call(ctx.credentialId(), ctx.regionId(), client -> client.deleteTopic(request));
    }

    @Override
    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String topicName) {
        Context ctx = resolve(instanceId);
        ListTopicSubscriptionsRequest request = ListTopicSubscriptionsRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .topicName(topicName)
                .build();
        ListTopicSubscriptionsResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.listTopicSubscriptions(request));
        ListTopicSubscriptionsResponseBody body = response == null ? null : response.getBody();
        List<ListTopicSubscriptionsResponseBody.Data> data = body == null ? null : body.getData();
        List<TopicConsumerVO> consumers = new ArrayList<>();
        if (data == null) {
            return consumers;
        }
        for (ListTopicSubscriptionsResponseBody.Data item : data) {
            if (item == null) {
                continue;
            }
            consumers.add(AliyunConverters.toTopicConsumerVO(item));
        }
        return consumers;
    }

    @Override
    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String search) {
        Context ctx = resolve(instanceId);
        List<ListConsumerGroupsResponseBody.List> all = new ArrayList<>();
        for (int page = 1; ; page++) {
            ListConsumerGroupsRequest.Builder builder = ListConsumerGroupsRequest.builder()
                    .instanceId(ctx.cloudInstanceId())
                    .pageNumber(page)
                    .pageSize(AliyunConverters.PAGE_SIZE);
            if (!!StringUtils.hasText(search)) {
                builder.filter(search);
            }
            ListConsumerGroupsRequest request = builder.build();
            ListConsumerGroupsResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                    client -> client.listConsumerGroups(request));
            ListConsumerGroupsResponseBody body = response == null ? null : response.getBody();
            ListConsumerGroupsResponseBody.Data data = body == null ? null : body.getData();
            List<ListConsumerGroupsResponseBody.List> list = data == null ? null : data.getList();
            if (list == null || list.isEmpty()) {
                break;
            }
            all.addAll(list);
            if (hasFetchedAll(page, AliyunConverters.PAGE_SIZE, data.getTotalCount())
                    || list.size() < AliyunConverters.PAGE_SIZE) {
                break;
            }
        }
        List<ConsumerGroupVO> groups = new ArrayList<>();
        for (ListConsumerGroupsResponseBody.List item : all) {
            if (item == null) {
                continue;
            }
            groups.add(AliyunConverters.toConsumerGroupVO(item, instanceId));
        }
        return groups;
    }

    @Override
    public ConsumerGroupVO createConsumerGroup(String instanceId, ConsumerGroupVO group) {
        Context ctx = resolve(instanceId);
        if (group == null || !StringUtils.hasText(group.getName())) {
            throw new BusinessException(400, "Consumer group name is required");
        }
        String deliveryOrderType = normalizeDeliveryOrderType(group.getDeliveryOrderType());
        int maxRetryTimes = group.getRetryMaxTimes() > 0 ? group.getRetryMaxTimes() : DEFAULT_MAX_RETRY_TIMES;
        CreateConsumerGroupRequest.ConsumeRetryPolicy.Builder retryPolicy =
                CreateConsumerGroupRequest.ConsumeRetryPolicy.builder()
                        .maxRetryTimes(maxRetryTimes);
        if (ORDERLY_DELIVERY_ORDER_TYPE.equals(deliveryOrderType)) {
            // ordered groups reject DefaultRetryPolicy and require a fixed interval
            retryPolicy.retryPolicy(FIXED_RETRY_POLICY)
                    .fixedIntervalRetryTime(DEFAULT_FIXED_RETRY_INTERVAL_SECONDS);
        } else {
            retryPolicy.retryPolicy(DEFAULT_RETRY_POLICY);
        }
        CreateConsumerGroupRequest request = CreateConsumerGroupRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .consumerGroupId(group.getName())
                .deliveryOrderType(deliveryOrderType)
                .consumeRetryPolicy(retryPolicy.build())
                .build();
        clientFactory.call(ctx.credentialId(), ctx.regionId(), client -> client.createConsumerGroup(request));
        group.setInstanceId(instanceId);
        group.setDeliveryOrderType(deliveryOrderType);
        group.setRetryMaxTimes(maxRetryTimes);
        group.setSubscribedTopics(java.util.List.of());
        group.setGmtCreate(java.time.LocalDateTime.now());
        group.setGmtModified(java.time.LocalDateTime.now());
        return group;
    }

    /**
     * OpenAPI accepts Concurrently/Orderly; tolerate FIFO/ordered spellings from the UI.
     */
    static String normalizeDeliveryOrderType(String raw) {
        if (raw == null || raw.isBlank()) {
            return DEFAULT_DELIVERY_ORDER_TYPE;
        }
        String value = raw.trim();
        if ("FIFO".equalsIgnoreCase(value) || "ORDERLY".equalsIgnoreCase(value)) {
            return ORDERLY_DELIVERY_ORDER_TYPE;
        }
        return DEFAULT_DELIVERY_ORDER_TYPE;
    }

    @Override
    public void deleteConsumerGroup(String instanceId, String groupName) {
        Context ctx = resolve(instanceId);
        DeleteConsumerGroupRequest request = DeleteConsumerGroupRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .consumerGroupId(groupName)
                .build();
        clientFactory.call(ctx.credentialId(), ctx.regionId(), client -> client.deleteConsumerGroup(request));
    }

    @Override
    public List<QueueProgressVO> getGroupProgress(String instanceId, String groupName) {
        Context ctx = resolve(instanceId);
        GetConsumerGroupLagRequest request = GetConsumerGroupLagRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .consumerGroupId(groupName)
                .build();
        GetConsumerGroupLagResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.getConsumerGroupLag(request));
        GetConsumerGroupLagResponseBody body = response == null ? null : response.getBody();
        GetConsumerGroupLagResponseBody.Data data = body == null ? null : body.getData();
        if (data == null) {
            return new ArrayList<>();
        }
        return AliyunConverters.toQueueProgressRows(data);
    }

    @Override
    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String groupName) {
        Context ctx = resolve(instanceId);
        ListConsumerGroupSubscriptionsRequest request = ListConsumerGroupSubscriptionsRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .consumerGroupId(groupName)
                .build();
        ListConsumerGroupSubscriptionsResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.listConsumerGroupSubscriptions(request));
        ListConsumerGroupSubscriptionsResponseBody body = response == null ? null : response.getBody();
        List<ListConsumerGroupSubscriptionsResponseBody.Data> data = body == null ? null : body.getData();
        List<SubscriptionEntryVO> subscriptions = new ArrayList<>();
        if (data == null) {
            return subscriptions;
        }
        for (ListConsumerGroupSubscriptionsResponseBody.Data item : data) {
            if (item == null) {
                continue;
            }
            subscriptions.add(AliyunConverters.toSubscriptionEntry(item));
        }
        return subscriptions;
    }

    @Override
    public void resetOffset(String instanceId, String groupName, long timestamp, String topic) {
        Context ctx = resolve(instanceId);
        ResetConsumeOffsetRequest.Builder builder = ResetConsumeOffsetRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .consumerGroupId(groupName);
        if (!!StringUtils.hasText(topic)) {
            builder.topicName(topic);
        }
        if (timestamp > 0L) {
            builder.resetType(RESET_TYPE_SPECIFIED_TIME)
                    .resetTime(AliyunConverters.formatTimeMillis(timestamp));
        } else {
            builder.resetType(RESET_TYPE_LATEST_OFFSET);
        }
        ResetConsumeOffsetRequest request = builder.build();
        clientFactory.call(ctx.credentialId(), ctx.regionId(), client -> client.resetConsumeOffset(request));
    }

    @Override
    public List<MessageRecordVO> queryMessages(String instanceId, String topic, String msgId,
                                               String tag, String key, Long startTime, Long endTime) {
        Context ctx = resolve(instanceId);
        List<MessageRecordVO> records = new ArrayList<>();
        for (int page = 1; page <= AliyunConverters.MESSAGE_MAX_PAGES; page++) {
            ListMessagesRequest.Builder builder = ListMessagesRequest.builder()
                    .instanceId(ctx.cloudInstanceId())
                    .pageNumber(page)
                    .pageSize(AliyunConverters.MESSAGE_PAGE_SIZE);
            if (!!StringUtils.hasText(topic)) {
                builder.topicName(topic);
            }
            if (!!StringUtils.hasText(msgId)) {
                builder.messageId(msgId);
            }
            if (!!StringUtils.hasText(key)) {
                builder.messageKey(key);
            }
            if (startTime != null) {
                builder.startTime(AliyunConverters.formatTimeMillis(startTime));
            }
            if (endTime != null) {
                builder.endTime(AliyunConverters.formatTimeMillis(endTime));
            }
            ListMessagesRequest request = builder.build();
            ListMessagesResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                    client -> client.listMessages(request));
            ListMessagesResponseBody body = response == null ? null : response.getBody();
            ListMessagesResponseBody.Data data = body == null ? null : body.getData();
            List<ListMessagesResponseBody.List> list = data == null ? null : data.getList();
            if (list == null || list.isEmpty()) {
                break;
            }
            for (ListMessagesResponseBody.List item : list) {
                if (item == null) {
                    continue;
                }
                MessageRecordVO vo = AliyunConverters.toMessageRecord(item);
                if (!StringUtils.hasText(tag) || tag.equals(vo.getTag())) {
                    records.add(vo);
                }
            }
            if (list.size() < AliyunConverters.MESSAGE_PAGE_SIZE) {
                break;
            }
        }
        return records;
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId, String topic) {
        Context ctx = resolve(instanceId);
        GetTraceRequest request = GetTraceRequest.builder()
                .instanceId(ctx.cloudInstanceId())
                .messageId(msgId)
                .build();
        GetTraceResponse response = clientFactory.call(ctx.credentialId(), ctx.regionId(),
                client -> client.getTrace(request));
        GetTraceResponseBody body = response == null ? null : response.getBody();
        GetTraceResponseBody.Data data = body == null ? null : body.getData();
        if (data == null) {
            return emptyTraceRecord();
        }
        return AliyunConverters.toTraceRecord(data);
    }

    private static TraceRecordVO emptyTraceRecord() {
        return TraceRecordVO.builder()
                .nodes(Collections.emptyList())
                .consumerStatus(Collections.emptyList())
                .build();
    }

    private Context resolve(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findByIdentifier(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (!StringUtils.hasText(instance.getCloudInstanceId()) || !StringUtils.hasText(instance.getRegionId())
                || instance.getCredentialId() == null) {
            throw new BusinessException(400, "Instance " + instanceId + " is missing Aliyun cloud binding");
        }
        return new Context(instance.getCloudInstanceId(), instance.getRegionId(), instance.getCredentialId());
    }

    private record Context(String cloudInstanceId, String regionId, Long credentialId) {
    }
}
