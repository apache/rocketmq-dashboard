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

import org.apache.rocketmq.studio.audit.OperationAuditConstants.Operation;
import org.apache.rocketmq.studio.audit.OperationAuditConstants.ResourceType;
import org.apache.rocketmq.studio.audit.OperationAuditConstants.Result;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.provider.apache.AdminClient;
import org.apache.rocketmq.studio.provider.apache.MetadataProvider;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CsvUtil;
import org.apache.rocketmq.studio.common.util.SystemTopicFilter;
import org.apache.rocketmq.studio.instance.group.CreateConsumerGroupDTO;
import org.apache.rocketmq.studio.instance.group.ImportConsumerGroupsResultVO;
import org.springframework.util.StringUtils;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsVO;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.SubscriptionEntryVO;
import org.apache.rocketmq.studio.provider.InstanceProvider;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class MetadataService {

    private static final int MAX_PAGE_SIZE = 100;

    private final MetadataProvider metadataProvider;
    private final AdminClient adminClient;
    private final InstanceProviderRegistry providerRegistry;
    private final org.apache.rocketmq.studio.instance.InstanceRepository instanceRepository;
    private final OperationAuditService operationAuditService;

    /**
     * External callers address instances by their globally unique instance ID (name);
     * internal storage keys rows by the numeric primary key. Resolve the identifier
     * before any DB-backed lookup; unknown identifiers pass through unchanged so the
     * downstream 404/empty semantics stay intact.
     */
    String normalizeInstanceId(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            return instanceId;
        }
        return instanceRepository.findByIdentifier(instanceId)
                .map(org.apache.rocketmq.studio.instance.InstanceVO::getName)
                .orElse(instanceId);
    }

    // ── TopicVO ───────────────────────────────────────────────────────


    public List<TopicVO> listTopics(String clusterId, String type, String search) {
        return listTopics(null, clusterId, type, search);
    }

    public List<TopicVO> listTopics(String instanceId, String clusterId, String type, String search) {
        instanceId = normalizeInstanceId(instanceId);
        if (!StringUtils.hasText(instanceId) && StringUtils.hasText(clusterId)) {
            // legacy cluster-scoped read kept for AI tool handlers
            return metadataProvider.listTopics(
                    normalizeFilter(clusterId), normalizeFilter(type), normalizeFilter(search));
        }
        return resolve(instanceId).listTopics(instanceId, normalizeFilter(type), normalizeFilter(search));
    }

    public PageResult<TopicVO> listTopicsPage(String instanceId, String clusterId, String type,
            String search, int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "pageSize must be between 1 and 100");
        }
        instanceId = normalizeInstanceId(instanceId);
        if (!StringUtils.hasText(instanceId) && StringUtils.hasText(clusterId)) {
            return metadataProvider.listTopicsPage(normalizeFilter(clusterId),
                    normalizeFilter(type), normalizeFilter(search), page, pageSize);
        }
        return resolve(instanceId).listTopicsPage(instanceId, normalizeFilter(type),
                normalizeFilter(search), page, pageSize);
    }


    public TopicVO createTopic(TopicVO topic) {
        requireTopic(topic);
        if (SystemTopicFilter.isSystem(topic.getName())) {
            throw new BusinessException(400, "System topics cannot be created: " + topic.getName());
        }
        String instanceId = topic.getInstanceId();
        InstanceProvider provider = resolve(instanceId);
        return executeWithAudit(provider, Operation.CREATE_TOPIC, ResourceType.TOPIC, topic.getName(),
                instanceId, topicDetail(topic), () -> provider.createTopic(instanceId, topic));
    }


    public TopicVO updateTopic(TopicVO topic) {
        requireTopic(topic);
        String instanceId = topic.getInstanceId();
        InstanceProvider provider = resolve(instanceId);
        return executeWithAudit(provider, Operation.UPDATE_TOPIC, ResourceType.TOPIC, topic.getName(),
                instanceId, topicDetail(topic), () -> provider.updateTopic(instanceId, topic));
    }


    public void deleteTopic(String name) {
        deleteTopic(null, name);
    }

    public void deleteTopic(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String topicName = requireName(name, "topic name");
        InstanceProvider provider = resolve(instanceId);
        String normalizedInstanceId = instanceId;
        executeWithAudit(provider, Operation.DELETE_TOPIC, ResourceType.TOPIC,
                topicName, instanceId, null, () -> provider.deleteTopic(normalizedInstanceId, topicName));
    }


    public List<BrokerRouteVO> getTopicRoutes(String name) {
        return getTopicRoutes(null, name);
    }

    public List<BrokerRouteVO> getTopicRoutes(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String topicName = requireName(name, "topic name");
        if (resolve(instanceId).vendor() != InstanceVendor.APACHE) {
            // broker routing does not apply to serverless cloud instances
            return List.of();
        }
        return metadataProvider.getTopicRoutes(instanceId, topicName);
    }


    public List<TopicConsumerVO> getTopicConsumers(String name) {
        return getTopicConsumers(null, name);
    }

    public List<TopicConsumerVO> getTopicConsumers(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String topicName = requireName(name, "topic name");
        return resolve(instanceId).getTopicConsumers(instanceId, topicName);
    }

    public TopicConsumerPageVO getTopicConsumersPage(String instanceId, String name, int page, int pageSize) {
        instanceId = normalizeInstanceId(instanceId);
        String topicName = requireName(name, "topic name");
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > 100) {
            throw new BusinessException(400, "pageSize must be between 1 and 100");
        }
        return resolve(instanceId).getTopicConsumersPage(instanceId, topicName, page, pageSize);
    }


    public SendMessageVO sendMessage(SendMessageDTO request) {
        requireSendMessageRequest(request);
        if (resolve(request.getInstanceId()).vendor() != InstanceVendor.APACHE) {
            throw new BusinessException(501, "Sending messages is not supported for cloud instances");
        }
        return adminClient.sendMessage(request);
    }

    // ── ConsumerGroupVO ───────────────────────────────────────────────


    public List<ConsumerGroupVO> listConsumerGroups(String clusterId, String search) {
        return listConsumerGroups(null, clusterId, search);
    }

    public List<ConsumerGroupVO> listConsumerGroups(String instanceId, String clusterId, String search) {
        instanceId = normalizeInstanceId(instanceId);
        if (!StringUtils.hasText(instanceId) && StringUtils.hasText(clusterId)) {
            return metadataProvider.listConsumerGroups(normalizeFilter(clusterId), normalizeFilter(search));
        }
        return resolve(instanceId).listConsumerGroups(instanceId, normalizeFilter(search));
    }

    public PageResult<ConsumerGroupVO> listConsumerGroupsPage(String instanceId, String clusterId, String search,
                                                              int page, int pageSize) {
        validatePagination(page, pageSize);
        instanceId = normalizeInstanceId(instanceId);
        if (!StringUtils.hasText(instanceId) && StringUtils.hasText(clusterId)) {
            return metadataProvider.listConsumerGroupsPage(normalizeFilter(clusterId),
                    normalizeFilter(search), page, pageSize);
        }
        return resolve(instanceId).listConsumerGroupsPage(instanceId, normalizeFilter(search),
                page, pageSize);
    }


    public ConsumerGroupVO getConsumerGroup(String name) {
        return getConsumerGroup(null, name);
    }

    public ConsumerGroupVO getConsumerGroup(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        if (resolve(instanceId).vendor() != InstanceVendor.APACHE) {
            throw new BusinessException(501, "Consumer group detail is not supported for cloud instances");
        }
        return adminClient.getConsumerGroup(instanceId, groupName);
    }

    /**
     * Re-reads a single consumer group so an operator can refresh one row without reloading the
     * whole group list. Implementation queries the provider's list path with the group name as
     * the search filter and then exact-matches the name among the returned entries (no direct
     * per-group lookup exists that works for every vendor).
     *
     * <p>Returns {@code null} when the group no longer exists: a missing group is an empty
     * business state, not an RPC error, so the endpoint responds 200 with empty data and the
     * frontend keeps the existing row unchanged.
     */
    public ConsumerGroupVO refreshConsumerGroup(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        return listConsumerGroups(instanceId, null, normalizeFilter(groupName))
                .stream()
                .filter(group -> groupName.equals(group.getName()))
                .findFirst()
                .orElse(null);
    }


    public List<QueueProgressVO> getGroupProgress(String name) {
        return getGroupProgress(null, name);
    }

    public List<QueueProgressVO> getGroupProgress(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        return resolve(instanceId).getGroupProgress(instanceId, groupName);
    }


    public List<SubscriptionEntryVO> getGroupSubscriptions(String name) {
        return getGroupSubscriptions(null, name);
    }

    public List<SubscriptionEntryVO> getGroupSubscriptions(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        return resolve(instanceId).getGroupSubscriptions(instanceId, groupName);
    }


    public ConsumerGroupVO createConsumerGroup(ConsumerGroupVO group) {
        String instanceId = group == null ? null : group.getInstanceId();
        InstanceProvider provider = resolve(instanceId);
        return executeWithAudit(provider, Operation.CREATE_GROUP, ResourceType.GROUP,
                group == null ? null : group.getName(), instanceId, consumerGroupDetail(group),
                () -> provider.createConsumerGroup(instanceId, group));
    }

    public ConsumerGroupSettingsVO getConsumerGroupSettings(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        requireApacheInstance(instanceId);
        return adminClient.getConsumerGroupSettings(instanceId, requireName(name, "consumer group name"));
    }

    public ConsumerGroupSettingsVO updateConsumerGroupSettings(String instanceId, String name, int retryQueueNums,
                                                                 int retryMaxTimes) {
        instanceId = normalizeInstanceId(instanceId);
        requireApacheInstance(instanceId);
        String groupName = requireName(name, "consumer group name");
        return adminClient.updateConsumerGroupSettings(instanceId, groupName, retryQueueNums, retryMaxTimes);
    }


    public void deleteConsumerGroup(String name) {
        deleteConsumerGroup(null, name);
    }

    public void deleteConsumerGroup(String instanceId, String name) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        InstanceProvider provider = resolve(instanceId);
        String normalizedInstanceId = instanceId;
        executeWithAudit(provider, Operation.DELETE_GROUP, ResourceType.GROUP,
                groupName, instanceId, null, () -> provider.deleteConsumerGroup(normalizedInstanceId, groupName));
    }


    public void resetOffset(String name, long timestamp, String topic) {
        resetOffset(null, name, timestamp, topic);
    }

    public ResetConsumerOffsetPreviewVO previewResetOffset(String instanceId, String name,
                                                           long timestamp, String topic) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        String topicName = requireName(topic, "topic name");
        return resolve(instanceId).previewResetOffset(instanceId, groupName, timestamp, topicName);
    }

    public void resetOffset(String instanceId, String name, long timestamp, String topic) {
        instanceId = normalizeInstanceId(instanceId);
        String groupName = requireName(name, "consumer group name");
        String topicName = requireName(topic, "topic name");
        InstanceProvider provider = resolve(instanceId);
        String normalizedInstanceId = instanceId;
        executeWithAudit(provider, Operation.RESET_OFFSET, ResourceType.GROUP, groupName, instanceId,
                "topic=" + topicName + ", timestamp=" + timestamp,
                () -> provider.resetOffset(normalizedInstanceId, groupName, timestamp, topicName));
    }


    /**
     * Every metadata operation goes through the provider registry; a blank instance id keeps the
     * legacy global behavior by defaulting to the Apache provider.
     */
    private InstanceProvider resolve(String instanceId) {
        return providerRegistry.byInstanceId(instanceId)
                .orElseGet(() -> providerRegistry.forVendor(InstanceVendor.APACHE));
    }

    private void requireApacheInstance(String instanceId) {
        if (resolve(instanceId).vendor() != InstanceVendor.APACHE) {
            throw new BusinessException(501, "Consumer group settings are not supported for cloud instances");
        }
    }

    private String normalizeFilter(String value) {
        return !StringUtils.hasText(value) ? null : value.trim();
    }

    private void validatePagination(int page, int pageSize) {
        if (page < 1) {
            throw new BusinessException(400, "page must be greater than zero");
        }
        if (pageSize < 1 || pageSize > MAX_PAGE_SIZE) {
            throw new BusinessException(400, "pageSize must be between 1 and " + MAX_PAGE_SIZE);
        }
    }

    private void requireTopic(TopicVO topic) {
        if (topic == null) {
            throw new BusinessException(400, "Topic request is required");
        }
    }

    private void requireSendMessageRequest(SendMessageDTO request) {
        if (request == null) {
            throw new BusinessException(400, "Topic send message request is required");
        }
    }

    private static final int MAX_IMPORT_GROUPS = 100;

    public ImportConsumerGroupsResultVO importConsumerGroups(String instanceId,
                                                             List<CreateConsumerGroupDTO> groups) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        if (groups == null || groups.isEmpty()) {
            throw new BusinessException(400, "groups is required");
        }
        if (groups.size() > MAX_IMPORT_GROUPS) {
            throw new BusinessException(400, "At most 100 consumer groups are allowed per import");
        }

        String normalizedInstanceId = normalizeInstanceId(instanceId);
        List<ConsumerGroupVO> imported = new ArrayList<>();
        List<ImportConsumerGroupsResultVO.Failure> failures = new ArrayList<>();
        for (int index = 0; index < groups.size(); index++) {
            CreateConsumerGroupDTO request = groups.get(index);
            String name = request == null ? null : request.getName();
            try {
                if (request == null) {
                    throw new BusinessException(400, "consumer group request is required");
                }
                ConsumerGroupVO group = request.toConsumerGroupVO();
                group.setInstanceId(normalizedInstanceId);
                imported.add(createConsumerGroup(group));
            } catch (Exception exception) {
                failures.add(ImportConsumerGroupsResultVO.Failure.builder()
                        .index(index)
                        .name(name)
                        .message(StringUtils.hasText(exception.getMessage())
                                ? exception.getMessage() : "Failed to create consumer group")
                        .build());
            }
        }

        return ImportConsumerGroupsResultVO.builder()
                .imported(imported.size())
                .failed(failures.size())
                .groups(imported)
                .failures(failures)
                .build();
    }

    public String exportConsumerGroups(String instanceId, String search, String subscriptionMode,
                                       List<String> names) {
        instanceId = normalizeInstanceId(instanceId);
        List<ConsumerGroupVO> groups = new ArrayList<>(listConsumerGroups(instanceId, null, search));
        Set<String> selectedNames = new HashSet<>(names == null ? List.of() : names);
        if (!selectedNames.isEmpty()) {
            groups.removeIf(group -> !selectedNames.contains(group.getName()));
        }
        if (StringUtils.hasText(subscriptionMode) && !"ALL".equals(subscriptionMode)) {
            groups.removeIf(group -> !subscriptionMode.equals(toText(group.getSubscriptionMode())));
        }

        groups.sort(this::compareNames);
        return buildConsumerGroupCsv(groups);
    }

    private int compareNames(ConsumerGroupVO left, ConsumerGroupVO right) {
        String leftName = left.getName() == null ? "" : left.getName();
        String rightName = right.getName() == null ? "" : right.getName();
        return leftName.compareTo(rightName);
    }

    private String buildConsumerGroupCsv(List<ConsumerGroupVO> groups) {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, "Name", "Namespace", "Cluster ID", "Subscription Mode", "Consume Type",
                "Online Instances", "Total Lag", "Delay Seconds", "Subscription Data Type",
                "Delivery Order Type", "Retry Max Times", "Subscribed Topics", "Created At", "Updated At");
        for (ConsumerGroupVO group : groups) {
            CsvUtil.appendRow(csv, group.getName(), group.getNamespace(), group.getClusterId(),
                    toText(group.getSubscriptionMode()), toText(group.getConsumeType()),
                    group.getOnlineInstances(), group.getTotalLag(), group.getDelaySeconds(),
                    group.getSubscriptionDataType(), group.getDeliveryOrderType(), group.getRetryMaxTimes(),
                    String.join(";", group.getSubscribedTopics() == null ? List.of() : group.getSubscribedTopics()),
                    group.getGmtCreate(), group.getGmtModified());
        }
        return csv.toString();
    }

    private String toText(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof SubscriptionMode mode) {
            return mode.name();
        }
        return value.toString();
    }

    private static final int MAX_IMPORT_TOPICS = 100;

    public ImportTopicsResultVO importTopics(String instanceId, List<CreateTopicDTO> topics) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        if (topics == null || topics.isEmpty()) {
            throw new BusinessException(400, "topics is required");
        }
        if (topics.size() > MAX_IMPORT_TOPICS) {
            throw new BusinessException(400, "At most 100 topics are allowed per import");
        }

        String normalizedInstanceId = normalizeInstanceId(instanceId);
        List<TopicVO> imported = new ArrayList<>();
        List<ImportTopicsResultVO.Failure> failures = new ArrayList<>();
        for (int index = 0; index < topics.size(); index++) {
            CreateTopicDTO request = topics.get(index);
            String name = request == null ? null : request.getName();
            try {
                if (request == null) {
                    throw new BusinessException(400, "topic request is required");
                }
                TopicVO topic = request.toTopicVO();
                topic.setInstanceId(normalizedInstanceId);
                imported.add(createTopic(topic));
            } catch (Exception exception) {
                failures.add(ImportTopicsResultVO.Failure.builder()
                        .index(index)
                        .name(name)
                        .message(StringUtils.hasText(exception.getMessage())
                                ? exception.getMessage() : "Failed to create topic")
                        .build());
            }
        }

        return ImportTopicsResultVO.builder()
                .imported(imported.size())
                .failed(failures.size())
                .topics(imported)
                .failures(failures)
                .build();
    }

    public String exportTopics(String instanceId, String type, String search, List<String> names) {
        instanceId = normalizeInstanceId(instanceId);
        List<TopicVO> topics = new ArrayList<>(listTopics(instanceId, null, type, search));
        Set<String> selectedNames = new HashSet<>(names == null ? List.of() : names);
        if (!selectedNames.isEmpty()) {
            topics.removeIf(topic -> !selectedNames.contains(topic.getName()));
        }
        topics.sort((left, right) -> compareNames(left.getName(), right.getName()));
        return buildTopicCsv(topics);
    }

    private int compareNames(String left, String right) {
        String leftName = left == null ? "" : left;
        String rightName = right == null ? "" : right;
        return leftName.compareTo(rightName);
    }

    private String buildTopicCsv(List<TopicVO> topics) {
        StringBuilder csv = new StringBuilder();
        CsvUtil.appendRow(csv, "Name", "Namespace", "Type", "Cluster ID", "Write Queues", "Read Queues",
                "Permission", "Message Count", "TPS", "Consumer Groups", "Remark", "Created At", "Updated At");
        for (TopicVO topic : topics) {
            CsvUtil.appendRow(csv, topic.getName(), topic.getNamespace(), toText(topic.getType()), topic.getClusterId(),
                    topic.getWriteQueues(), topic.getReadQueues(), toText(topic.getPerm()), topic.getMessageCount(),
                    topic.getTps(), topic.getConsumerGroupCount(), topic.getRemark(),
                    topic.getGmtCreate(), topic.getGmtModified());
        }
        return csv.toString();
    }

    private String requireName(String value, String fieldName) {
        if (!StringUtils.hasText(value)) {
            throw new BusinessException(400, fieldName + " is required");
        }
        return value.trim();
    }

    private String topicDetail(TopicVO topic) {
        return "type=" + optionalDetail(topic.getType())
                + ", writeQueues=" + topic.getWriteQueues()
                + ", readQueues=" + topic.getReadQueues()
                + ", perm=" + optionalDetail(topic.getPerm());
    }

    private String consumerGroupDetail(ConsumerGroupVO group) {
        if (group == null) {
            return null;
        }
        return "consumeType=" + optionalDetail(group.getConsumeType())
                + ", subscriptionMode=" + optionalDetail(group.getSubscriptionMode())
                + ", retryMaxTimes=" + group.getRetryMaxTimes();
    }

    private String optionalDetail(Object value) {
        if (value == null) {
            return "-";
        }
        String text = value.toString();
        return StringUtils.hasText(text) ? text.trim() : "-";
    }

    private <T> T executeWithAudit(InstanceProvider provider, String operation, String resourceType,
                                   String resourceName, String instanceId, String detail, Supplier<T> action) {
        if (provider.vendor() == InstanceVendor.APACHE) {
            return action.get();
        }
        try {
            T result = action.get();
            recordAudit(operation, resourceType, resourceName, instanceId, detail, Result.SUCCESS, null);
            return result;
        } catch (RuntimeException failure) {
            recordAudit(operation, resourceType, resourceName, instanceId, detail, Result.FAILED,
                    failure.getMessage());
            throw failure;
        }
    }

    private void executeWithAudit(InstanceProvider provider, String operation, String resourceType,
                                  String resourceName, String instanceId, String detail, Runnable action) {
        if (provider.vendor() == InstanceVendor.APACHE) {
            action.run();
            return;
        }
        try {
            action.run();
            recordAudit(operation, resourceType, resourceName, instanceId, detail, Result.SUCCESS, null);
        } catch (RuntimeException failure) {
            recordAudit(operation, resourceType, resourceName, instanceId, detail, Result.FAILED,
                    failure.getMessage());
            throw failure;
        }
    }

    private void recordAudit(String operation, String resourceType, String resourceName, String instanceId,
                             String detail, String result, String errorMessage) {
        try {
            operationAuditService.record(operation, resourceType, resourceName, instanceId,
                    detail, result, errorMessage);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }
}
