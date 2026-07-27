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
package org.apache.rocketmq.dashboard.architecture.impl.cloud;

import org.apache.rocketmq.dashboard.architecture.impl.cloud.CloudApiHttpClient.CloudApiException;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Tencent Cloud (TDMQ) RocketMQ metadata provider implementation.
 *
 * <p>Implements the {@link org.apache.rocketmq.dashboard.architecture.MetadataProvider} SPI
 * for Tencent Cloud TDMQ RocketMQ using TC3-HMAC-SHA256 signature authentication.</p>
 *
 * <h3>Tencent Cloud TDMQ API Mapping</h3>
 * <ul>
 *   <li>DescribeRocketMQNamespaces → listNamespaces</li>
 *   <li>DescribeRocketMQTopics → listTopics</li>
 *   <li>CreateRocketMQTopic → createTopic</li>
 *   <li>DeleteRocketMQTopic → deleteTopic</li>
 *   <li>DescribeRocketMQGroups → listConsumerGroups</li>
 *   <li>CreateRocketMQGroup → createConsumerGroup</li>
 *   <li>DeleteRocketMQGroup → deleteConsumerGroup</li>
 * </ul>
 *
 * <p>Per RIP-1 META-01, this adapter enables unified metadata management
 * for Tencent Cloud-hosted RocketMQ instances.</p>
 */
public class TencentCloudMetadataProvider extends AbstractCloudMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(TencentCloudMetadataProvider.class);

    /** Tencent Cloud TDMQ API endpoint. */
    private static final String DEFAULT_ENDPOINT = "tdmq.tencentcloudapi.com";

    /** Tencent Cloud TDMQ service name for signing. */
    private static final String SERVICE = "tdmq";

    /** Tencent Cloud API version. */
    private static final String API_VERSION = "2020-02-17";

    /** Resolved API endpoint. */
    private String resolvedEndpoint;

    /** Shared HTTP client. */
    private CloudApiHttpClient httpClient;

    public TencentCloudMetadataProvider(CloudProviderConfig config) {
        super(config);
        log.info("TencentCloudMetadataProvider created for instance: {}", config.getInstanceId());
    }

    /**
     * Initialize the HTTP client and resolve the API endpoint.
     */
    public void initialize() throws Exception {
        this.httpClient = new CloudApiHttpClient();
        this.resolvedEndpoint = config.getEndpoint() != null && !config.getEndpoint().trim().isEmpty()
            ? config.getEndpoint() : DEFAULT_ENDPOINT;
        log.info("TencentCloudMetadataProvider initialized: instance={}, region={}, endpoint={}",
            config.getInstanceId(), config.getRegionId(), resolvedEndpoint);
    }

    @Override
    public boolean supportsCapability(String capability) {
        switch (capability) {
            case "namespace":
            case "popConsume":
            case "aclV2":
            case "liteTopic":
                return true;
            case "remotingClient":
            case "grpcClient":
                return false;
            default:
                return false;
        }
    }

    // ==================== Namespace Operations ====================

    @Override
    protected List<NamespaceInfo> doListNamespaces() throws Exception {
        log.info("Listing namespaces for Tencent Cloud instance: {}", config.getInstanceId());
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ClusterId", config.getInstanceId());
            params.put("Offset", 0);
            params.put("Limit", 100);
            Map<String, Object> result = callApi("DescribeRocketMQNamespaces", params);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nsList =
                (List<Map<String, Object>>) result.get("Namespaces");
            if (nsList == null || nsList.isEmpty()) {
                return Collections.emptyList();
            }

            List<NamespaceInfo> namespaces = new ArrayList<>();
            for (Map<String, Object> nsData : nsList) {
                NamespaceInfo ns = new NamespaceInfo();
                ns.setNamespaceName(CloudApiHttpClient.getString(nsData, "NamespaceId"));
                ns.setDisplayName(CloudApiHttpClient.getString(nsData, "Remark"));
                ns.setClusterName(config.getInstanceId());
                ns.setStatus("ACTIVE");

                NamespaceInfo.QuotaConfig quota = new NamespaceInfo.QuotaConfig();
                quota.setMaxTopicCount(CloudApiHttpClient.getInt(nsData, "TopicNum", 1000));
                quota.setMaxConsumerGroupCount(CloudApiHttpClient.getInt(nsData, "GroupNum", 500));
                ns.setQuotaConfig(quota);
                namespaces.add(ns);
            }

            log.info("Listed {} namespaces for Tencent Cloud instance: {}", namespaces.size(), config.getInstanceId());
            return namespaces;
        } catch (CloudApiException e) {
            log.error("Failed to list Tencent Cloud namespaces: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    protected Optional<NamespaceInfo> doGetNamespace(String namespace) throws Exception {
        List<NamespaceInfo> namespaces = doListNamespaces();
        return namespaces.stream()
            .filter(ns -> namespace.equals(ns.getNamespaceName()))
            .findFirst();
    }

    @Override
    protected void doCreateNamespace(NamespaceInfo namespace) throws Exception {
        log.info("Creating namespace: {} for Tencent Cloud instance: {}",
            namespace.getNamespaceName(), config.getInstanceId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", namespace.getNamespaceName());
        if (namespace.getDisplayName() != null) {
            params.put("Remark", namespace.getDisplayName());
        }
        callApi("CreateRocketMQNamespace", params);
        log.info("Namespace created: {} for Tencent Cloud instance: {}",
            namespace.getNamespaceName(), config.getInstanceId());
    }

    @Override
    protected void doUpdateNamespace(NamespaceInfo namespace) throws Exception {
        throw new UnsupportedOperationException(
            "Tencent Cloud TDMQ namespace update is not supported via Dashboard API.");
    }

    @Override
    protected void doDeleteNamespace(String namespace) throws Exception {
        log.info("Deleting namespace: {} for Tencent Cloud instance: {}", namespace, config.getInstanceId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", namespace);
        callApi("DeleteRocketMQNamespace", params);
        log.info("Namespace deleted: {} for Tencent Cloud instance: {}", namespace, config.getInstanceId());
    }

    // ==================== Topic Operations ====================

    @Override
    protected List<TopicInfo> doListTopics(Optional<String> namespace) throws Exception {
        log.info("Listing topics for Tencent Cloud instance: {}, namespace: {}",
            config.getInstanceId(), namespace);
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ClusterId", config.getInstanceId());
            params.put("NamespaceId", namespace.orElse("default"));
            params.put("Offset", 0);
            params.put("Limit", 100);
            Map<String, Object> result = callApi("DescribeRocketMQTopics", params);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topicList =
                (List<Map<String, Object>>) result.get("Topics");
            if (topicList == null || topicList.isEmpty()) {
                return Collections.emptyList();
            }

            List<TopicInfo> topics = new ArrayList<>();
            for (Map<String, Object> topicData : topicList) {
                TopicInfo topic = new TopicInfo();
                topic.setTopicName(CloudApiHttpClient.getString(topicData, "Name"));
                topic.setClusterName(config.getInstanceId());
                topic.setNamespace(namespace.orElse("default"));

                int type = CloudApiHttpClient.getInt(topicData, "Type", 0);
                topic.setTopicType(mapTencentTopicType(type));

                topic.setReadQueueNums(CloudApiHttpClient.getInt(topicData, "PartitionNum", 16));
                topic.setWriteQueueNums(CloudApiHttpClient.getInt(topicData, "PartitionNum", 16));
                topics.add(topic);
            }

            log.info("Listed {} topics for Tencent Cloud instance: {}", topics.size(), config.getInstanceId());
            return topics;
        } catch (CloudApiException e) {
            log.error("Failed to list topics for Tencent Cloud: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    protected Optional<TopicInfo> doGetTopic(String topic, Optional<String> namespace) throws Exception {
        List<TopicInfo> topics = doListTopics(namespace);
        return topics.stream()
            .filter(t -> topic.equals(t.getTopicName()))
            .findFirst();
    }

    @Override
    protected void doCreateTopic(TopicInfo topic) throws Exception {
        log.info("Creating topic: {} for Tencent Cloud instance: {}",
            topic.getTopicName(), config.getInstanceId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", topic.getNamespace() != null ? topic.getNamespace() : "default");
        params.put("Topic", topic.getTopicName());
        params.put("Partitions", topic.getWriteQueueNums() != null && topic.getWriteQueueNums() > 0 ? topic.getWriteQueueNums() : 16);
        params.put("Type", mapToTencentTopicType(topic.getTopicType()));
        if (topic.getAttributes() != null && topic.getAttributes().get("remark") != null) {
            params.put("Remark", topic.getAttributes().get("remark"));
        }
        callApi("CreateRocketMQTopic", params);
        log.info("Topic created: {} for Tencent Cloud instance: {}", topic.getTopicName(), config.getInstanceId());
    }

    @Override
    protected void doUpdateTopic(TopicInfo topic) throws Exception {
        throw new UnsupportedOperationException(
            "Tencent Cloud TDMQ topic update is not supported. Topic properties are immutable.");
    }

    @Override
    protected void doDeleteTopic(String topic, Optional<String> namespace) throws Exception {
        log.info("Deleting topic: {} for Tencent Cloud instance: {}", topic, config.getInstanceId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", namespace.orElse("default"));
        params.put("Topic", topic);
        callApi("DeleteRocketMQTopic", params);
        log.info("Topic deleted: {} for Tencent Cloud instance: {}", topic, config.getInstanceId());
    }

    @Override
    protected boolean doValidateTopicType(String topic, TopicType expectedType) throws Exception {
        Optional<TopicInfo> existing = doGetTopic(topic, Optional.empty());
        if (existing.isEmpty()) {
            return true;
        }
        return expectedType.equals(existing.get().getTopicType());
    }

    // ==================== Consumer Group Operations ====================

    @Override
    protected List<ConsumerGroupInfo> doListConsumerGroups(Optional<String> namespace) throws Exception {
        log.info("Listing consumer groups for Tencent Cloud instance: {}", config.getInstanceId());
        try {
            Map<String, Object> params = new LinkedHashMap<>();
            params.put("ClusterId", config.getInstanceId());
            params.put("NamespaceId", namespace.orElse("default"));
            params.put("Offset", 0);
            params.put("Limit", 100);
            Map<String, Object> result = callApi("DescribeRocketMQGroups", params);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupList =
                (List<Map<String, Object>>) result.get("Groups");
            if (groupList == null || groupList.isEmpty()) {
                return Collections.emptyList();
            }

            List<ConsumerGroupInfo> groups = new ArrayList<>();
            for (Map<String, Object> groupData : groupList) {
                ConsumerGroupInfo group = new ConsumerGroupInfo();
                group.setConsumerGroupName(CloudApiHttpClient.getString(groupData, "Name"));
                group.setClusterName(config.getInstanceId());
                group.setOnline(CloudApiHttpClient.getBoolean(groupData, "Online", false));
                groups.add(group);
            }

            log.info("Listed {} consumer groups for Tencent Cloud instance: {}",
                groups.size(), config.getInstanceId());
            return groups;
        } catch (CloudApiException e) {
            log.error("Failed to list consumer groups for Tencent Cloud: {}", e.getMessage());
            throw e;
        }
    }

    @Override
    protected Optional<ConsumerGroupInfo> doGetConsumerGroup(String consumerGroup,
            Optional<String> namespace) throws Exception {
        List<ConsumerGroupInfo> groups = doListConsumerGroups(namespace);
        return groups.stream()
            .filter(g -> consumerGroup.equals(g.getConsumerGroupName()))
            .findFirst();
    }

    @Override
    protected void doCreateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        log.info("Creating consumer group: {} for Tencent Cloud instance: {}",
            consumerGroup.getConsumerGroupName(), config.getInstanceId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", "default");
        params.put("GroupId", consumerGroup.getConsumerGroupName());
        params.put("ReadEnable", true);
        params.put("BroadcastEnable", false);
        callApi("CreateRocketMQGroup", params);
        log.info("Consumer group created: {}", consumerGroup.getConsumerGroupName());
    }

    @Override
    protected void doUpdateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        throw new UnsupportedOperationException(
            "Tencent Cloud consumer group update is not directly supported via Dashboard API.");
    }

    @Override
    protected void doDeleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        log.info("Deleting consumer group: {} for Tencent Cloud instance: {}",
            consumerGroup, config.getInstanceId());
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", namespace.orElse("default"));
        params.put("GroupId", consumerGroup);
        callApi("DeleteRocketMQGroup", params);
        log.info("Consumer group deleted: {}", consumerGroup);
    }

    @Override
    protected List<SubscriptionInfo> doListSubscriptions(String groupName) throws Exception {
        log.warn("Subscription listing not directly available for Tencent Cloud TDMQ");
        return Collections.emptyList();
    }

    @Override
    protected void doResetConsumerGroupOffset(String groupName, String topic, long timestamp) throws Exception {
        log.info("Resetting offset for group: {}, topic: {}, timestamp: {}", groupName, topic, timestamp);
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ClusterId", config.getInstanceId());
        params.put("NamespaceId", "default");
        params.put("GroupId", groupName);
        params.put("Topic", topic);
        params.put("Type", 1); // 1=reset by timestamp
        params.put("ResetTimestamp", timestamp);
        callApi("ResetRocketMQConsumerOffSet", params);
        log.info("Consumer group offset reset successfully for group: {}", groupName);
    }

    // ==================== Client Operations ====================

    @Override
    protected List<ClientInstance> doListClientInstances(Optional<String> topic, Optional<String> group) throws Exception {
        log.warn("Client instance listing not directly available for Tencent Cloud TDMQ");
        return Collections.emptyList();
    }

    @Override
    protected Optional<ClientInstance> doGetClientInstance(String clientId) throws Exception {
        return Optional.empty();
    }

    // ==================== TC3-HMAC-SHA256 API Signing ====================

    /**
     * Call a Tencent Cloud TDMQ API action using TC3-HMAC-SHA256 signing.
     */
    private Map<String, Object> callApi(String action, Map<String, Object> params) throws CloudApiException {
        long timestamp = Instant.now().getEpochSecond();
        String date = DateTimeFormatter.ofPattern("yyyy-MM-dd")
            .withZone(ZoneOffset.UTC)
            .format(Instant.ofEpochSecond(timestamp));

        String jsonBody = CloudApiHttpClient.toJson(params != null ? params : new HashMap<>());
        String hashedPayload = CloudApiHttpClient.sha256Hex(jsonBody);

        // Step 1: Build Canonical Request
        String canonicalRequest = "POST\n"
            + "/\n"
            + "\n"
            + "content-type:application/json\n"
            + "host:" + resolvedEndpoint + "\n"
            + "x-tc-action:" + action.toLowerCase() + "\n"
            + "\n"
            + "content-type;host;x-tc-action\n"
            + hashedPayload;

        // Step 2: Build String to Sign
        String credentialScope = date + "/" + SERVICE + "/tc3_request";
        String hashedCanonicalRequest = CloudApiHttpClient.sha256Hex(canonicalRequest);
        String stringToSign = "TC3-HMAC-SHA256\n"
            + timestamp + "\n"
            + credentialScope + "\n"
            + hashedCanonicalRequest;

        // Step 3: Calculate Signature
        byte[] secretDate = CloudApiHttpClient.hmacSha256(
            ("TC3" + config.getSecretKey()).getBytes(StandardCharsets.UTF_8), date);
        byte[] secretService = CloudApiHttpClient.hmacSha256(secretDate, SERVICE);
        byte[] secretSigning = CloudApiHttpClient.hmacSha256(secretService, "tc3_request");
        String signature = CloudApiHttpClient.hmacSha256Hex(secretSigning, stringToSign);

        // Step 4: Build Authorization header
        String authorization = "TC3-HMAC-SHA256"
            + " Credential=" + config.getAccessKey() + "/" + credentialScope
            + ", SignedHeaders=content-type;host;x-tc-action"
            + ", Signature=" + signature;

        // Step 5: Execute request
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Host", resolvedEndpoint);
        headers.put("X-TC-Action", action);
        headers.put("X-TC-Timestamp", String.valueOf(timestamp));
        headers.put("X-TC-Version", API_VERSION);
        headers.put("X-TC-Region", config.getRegionId() != null ? config.getRegionId() : "ap-guangzhou");

        String url = "https://" + resolvedEndpoint;
        String responseBody = httpClient.postJson(url, jsonBody, headers);

        // Parse response
        Map<String, Object> fullResponse = CloudApiHttpClient.parseJson(responseBody);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = (Map<String, Object>) fullResponse.get("Response");
        if (response == null) {
            throw new CloudApiException("Tencent Cloud API returned no Response field");
        }

        // Check for error
        @SuppressWarnings("unchecked")
        Map<String, Object> error = (Map<String, Object>) response.get("Error");
        if (error != null) {
            String code = CloudApiHttpClient.getString(error, "Code");
            String message = CloudApiHttpClient.getString(error, "Message");
            throw new CloudApiException(
                String.format("Tencent Cloud API error: code=%s, message=%s, action=%s",
                    code, message, action));
        }

        return response;
    }

    // ==================== Type Mapping Helpers ====================

    private TopicType mapTencentTopicType(int type) {
        switch (type) {
            case 1: return TopicType.NORMAL;
            case 2: return TopicType.FIFO;
            case 3: return TopicType.DELAY;
            case 4: return TopicType.TRANSACTION;
            default: return TopicType.NORMAL;
        }
    }

    private int mapToTencentTopicType(TopicType type) {
        if (type == null) return 1;
        switch (type) {
            case NORMAL: return 1;
            case FIFO: return 2;
            case DELAY: return 3;
            case TRANSACTION: return 4;
            default: return 1;
        }
    }
}
