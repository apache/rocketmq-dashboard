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
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListByGroupRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListByGroupResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicListResponse;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.DescribeTopicResponse;
import com.tencentcloudapi.trocket.v20230308.models.ModifyTopicRequest;
import com.tencentcloudapi.trocket.v20230308.models.ResetConsumerGroupOffsetRequest;
import com.tencentcloudapi.trocket.v20230308.models.SubscriptionData;
import com.tencentcloudapi.trocket.v20230308.models.TopicItem;
import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.common.exception.BusinessException;
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
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import lombok.RequiredArgsConstructor;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * Tencent Cloud TDMQ RocketMQ 5.x topic operations backed by Trocket v20230308 OpenAPI.
 *
 * <p>Topic management is supported independently from the other instance-scoped operations. The
 * remaining operations intentionally retain the provider's unsupported-operation behavior until
 * their corresponding Tencent Cloud APIs are mapped to Studio's common models.</p>
 */
@RequiredArgsConstructor
@Component
public class TencentInstanceProvider implements InstanceProvider {

    static final int PAGE_SIZE = 100;
    static final int MAX_PAGES = 100;
    static final int CONSUMER_PAGE_SIZE = 100;
    static final int DEFAULT_QUEUE_NUM = 8;
    static final int DEFAULT_MAX_RETRY_TIMES = 16;
    private static final String NOT_IMPLEMENTED = "Tencent Cloud operation is not implemented yet";

    private final TencentClientFactory clientFactory;
    private final InstanceRepository instanceRepository;

    @Override
    public InstanceVendor vendor() {
        return InstanceVendor.TENCENT;
    }

    @Override
    public int countTopics(String instanceId) {
        return listTopics(instanceId, null, null, false).size();
    }

    @Override
    public int countGroups(String instanceId) {
        return listConsumerGroups(instanceId, null, false).size();
    }

    @Override
    public List<TopicVO> listTopics(String instanceId, String type, String search) {
        return listTopics(instanceId, type, search, true);
    }

    private List<TopicVO> listTopics(String instanceId, String type, String search, boolean enrichTimes) {
        Context context = resolve(instanceId);
        List<TopicVO> topics = new ArrayList<>();
        for (int page = 0; page < MAX_PAGES; page++) {
            DescribeTopicListRequest request = new DescribeTopicListRequest();
            request.setInstanceId(context.cloudInstanceId());
            request.setOffset((long) page * PAGE_SIZE);
            request.setLimit((long) PAGE_SIZE);
            DescribeTopicListResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                    client -> client.DescribeTopicList(request));
            TopicItem[] data = response == null ? null : response.getData();
            if (data == null || data.length == 0) {
                break;
            }
            for (TopicItem item : data) {
                if (item == null) {
                    continue;
                }
                TopicVO topic = toTopic(item, instanceId);
                if (matchesType(type, topic) && matchesSearch(search, topic)) {
                    if (enrichTimes) {
                        enrichTopicTimes(context, topic);
                    }
                    topics.add(topic);
                }
            }
            if (data.length < PAGE_SIZE) {
                break;
            }
        }
        return topics;
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
            topic.setCreatedAt(toLocalDateTime(response.getCreatedTime()));
            topic.setUpdatedAt(toLocalDateTime(response.getLastUpdateTime()));
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
        topic.setCreatedAt(LocalDateTime.now());
        topic.setUpdatedAt(LocalDateTime.now());
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
        if (topic.getWriteQueues() > 0 || topic.getReadQueues() > 0) {
            request.setQueueNum(queueNum(topic));
        }
        clientFactory.call(context.credentialId(), context.regionId(), client -> client.ModifyTopic(request));
        topic.setInstanceId(instanceId);
        topic.setPerm(defaultPerm(topic.getPerm()));
        topic.setUpdatedAt(LocalDateTime.now());
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
        DescribeTopicRequest request = new DescribeTopicRequest();
        request.setInstanceId(context.cloudInstanceId());
        request.setTopic(topicName);
        request.setOffset(0L);
        request.setLimit((long) CONSUMER_PAGE_SIZE);
        DescribeTopicResponse response = clientFactory.call(context.credentialId(), context.regionId(),
                client -> client.DescribeTopic(request));
        SubscriptionData[] data = response == null ? null : response.getSubscriptionData();
        List<TopicConsumerVO> consumers = new ArrayList<>();
        if (data == null) {
            return consumers;
        }
        for (SubscriptionData subscription : data) {
            if (subscription == null) {
                continue;
            }
            consumers.add(toTopicConsumer(subscription));
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
            group.setCreatedAt(toLocalDateTime(response.getCreatedTime()));
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
        group.setCreatedAt(LocalDateTime.now());
        group.setUpdatedAt(LocalDateTime.now());
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
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    @Override
    public TraceRecordVO getMessageTrace(String instanceId, String msgId) {
        throw new UnsupportedOperationException(NOT_IMPLEMENTED);
    }

    private Context resolve(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findById(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        if (!StringUtils.hasText(instance.getCloudInstanceId()) || !StringUtils.hasText(instance.getRegionId())
                || !StringUtils.hasText(instance.getCredentialId())) {
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
        return value == null ? 0 : Math.toIntExact(value);
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

    private static boolean matchesType(String type, TopicVO topic) {
        return !StringUtils.hasText(type)
                || topic.getType() != null && topic.getType().name().equalsIgnoreCase(type.trim());
    }

    private static boolean matchesSearch(String search, TopicVO topic) {
        if (!StringUtils.hasText(search)) {
            return true;
        }
        String needle = search.trim().toLowerCase(Locale.ROOT);
        return contains(topic.getName(), needle) || contains(topic.getRemark(), needle);
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

    private record Context(String cloudInstanceId, String regionId, String credentialId) {
    }
}
