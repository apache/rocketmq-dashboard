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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Huawei Cloud (DMS) RocketMQ metadata provider implementation.
 *
 * <p>Implements the {@link org.apache.rocketmq.dashboard.architecture.MetadataProvider} SPI
 * for Huawei Cloud DMS RocketMQ using SDK-HMAC-SHA256 signature authentication.</p>
 *
 * <h3>Huawei Cloud DMS API Mapping</h3>
 * <ul>
 *   <li>GET /v2/{project_id}/instances/{instance_id}/topics → listTopics</li>
 *   <li>POST /v2/{project_id}/instances/{instance_id}/topics → createTopic</li>
 *   <li>DELETE /v2/{project_id}/instances/{instance_id}/topics/{topic} → deleteTopic</li>
 *   <li>GET /v2/{project_id}/instances/{instance_id}/groups → listConsumerGroups</li>
 *   <li>POST /v2/{project_id}/instances/{instance_id}/groups → createConsumerGroup</li>
 *   <li>DELETE /v2/{project_id}/instances/{instance_id}/groups/{group} → deleteConsumerGroup</li>
 * </ul>
 *
 * <p>Per RIP-1 META-01, this adapter enables unified metadata management
 * for Huawei Cloud-hosted RocketMQ instances.</p>
 */
public class HuaweiCloudMetadataProvider extends AbstractCloudMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(HuaweiCloudMetadataProvider.class);

    /** Huawei Cloud DMS API endpoint pattern. */
    private static final String DEFAULT_ENDPOINT_PATTERN = "dms.%s.myhuaweicloud.com";

    /** Huawei Cloud DMS service name for signing. */
    private static final String SERVICE = "dms";

    /** API path pattern for instance operations. */
    private static final String API_PATH_PREFIX = "/v2/%s/instances";

    /** Resolved API endpoint host. */
    private String resolvedEndpoint;

    /** Project ID (from extendedConfig or regionId). */
    private String projectId;

    /** Shared HTTP client. */
    private CloudApiHttpClient httpClient;

    public HuaweiCloudMetadataProvider(CloudProviderConfig config) {
        super(config);
        log.info("HuaweiCloudMetadataProvider created for instance: {}", config.getInstanceId());
    }

    /**
     * Initialize the HTTP client and resolve the API endpoint.
     */
    public void initialize() throws Exception {
        this.httpClient = new CloudApiHttpClient();
        this.resolvedEndpoint = config.getEndpoint() != null && !config.getEndpoint().trim().isEmpty()
            ? config.getEndpoint()
            : String.format(DEFAULT_ENDPOINT_PATTERN, config.getRegionId());
        this.projectId = config.getExtendedConfig() != null
            ? config.getExtendedConfig().getOrDefault("projectId", config.getRegionId())
            : config.getRegionId();
        log.info("HuaweiCloudMetadataProvider initialized: instance={}, region={}, endpoint={}",
            config.getInstanceId(), config.getRegionId(), resolvedEndpoint);
    }

    @Override
    public boolean supportsCapability(String capability) {
        switch (capability) {
            case "namespace":
            case "popConsume":
            case "aclV2":
                return true;
            case "liteTopic":
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
        log.info("Listing namespaces for Huawei Cloud instance: {}", config.getInstanceId());
        // Huawei Cloud DMS RocketMQ uses the instance itself as the primary namespace
        NamespaceInfo ns = new NamespaceInfo();
        ns.setNamespaceName(config.getInstanceId());
        ns.setDisplayName(config.getDisplayName() != null ? config.getDisplayName() : config.getInstanceId());
        ns.setStatus("ACTIVE");
        ns.setClusterName(config.getInstanceId());
        return Collections.singletonList(ns);
    }

    @Override
    protected Optional<NamespaceInfo> doGetNamespace(String namespace) throws Exception {
        if (config.getInstanceId().equals(namespace)) {
            List<NamespaceInfo> namespaces = doListNamespaces();
            return namespaces.isEmpty() ? Optional.empty() : Optional.of(namespaces.get(0));
        }
        return Optional.empty();
    }

    @Override
    protected void doCreateNamespace(NamespaceInfo namespace) throws Exception {
        throw new UnsupportedOperationException(
            "Huawei Cloud DMS namespace creation is not supported via Dashboard API. "
            + "Please create instances through the Huawei Cloud console.");
    }

    @Override
    protected void doUpdateNamespace(NamespaceInfo namespace) throws Exception {
        throw new UnsupportedOperationException(
            "Huawei Cloud DMS namespace update is not supported via Dashboard API.");
    }

    @Override
    protected void doDeleteNamespace(String namespace) throws Exception {
        throw new UnsupportedOperationException(
            "Huawei Cloud DMS namespace deletion is not supported via Dashboard API.");
    }

    // ==================== Topic Operations ====================

    @Override
    protected List<TopicInfo> doListTopics(Optional<String> namespace) throws Exception {
        log.info("Listing topics for Huawei Cloud instance: {}", config.getInstanceId());
        try {
            String path = buildTopicsPath();
            Map<String, Object> result = callApi("GET", path, null, null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> topicList =
                (List<Map<String, Object>>) result.get("topics");
            if (topicList == null || topicList.isEmpty()) {
                return Collections.emptyList();
            }

            List<TopicInfo> topics = new ArrayList<>();
            for (Map<String, Object> topicData : topicList) {
                TopicInfo topic = new TopicInfo();
                topic.setTopicName(CloudApiHttpClient.getString(topicData, "name"));
                topic.setClusterName(config.getInstanceId());

                int permission = CloudApiHttpClient.getInt(topicData, "permission", 6);
                topic.setReadQueueNums(CloudApiHttpClient.getInt(topicData, "total_read_queue_num", 16));
                topic.setWriteQueueNums(CloudApiHttpClient.getInt(topicData, "total_write_queue_num", 16));

                int messageType = CloudApiHttpClient.getInt(topicData, "message_type", 0);
                topic.setTopicType(mapHuaweiTopicType(messageType));
                topics.add(topic);
            }

            log.info("Listed {} topics for Huawei Cloud instance: {}", topics.size(), config.getInstanceId());
            return topics;
        } catch (CloudApiException e) {
            log.error("Failed to list topics for Huawei Cloud: {}", e.getMessage());
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
        log.info("Creating topic: {} for Huawei Cloud instance: {}",
            topic.getTopicName(), config.getInstanceId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", topic.getTopicName());
        body.put("total_read_queue_num", topic.getReadQueueNums() != null && topic.getReadQueueNums() > 0 ? topic.getReadQueueNums() : 16);
        body.put("total_write_queue_num", topic.getWriteQueueNums() != null && topic.getWriteQueueNums() > 0 ? topic.getWriteQueueNums() : 16);
        body.put("permission", 6); // read + write
        body.put("message_type", mapToHuaweiTopicType(topic.getTopicType()));

        String jsonBody = CloudApiHttpClient.toJson(body);
        callApi("POST", buildTopicsPath(), null, jsonBody);
        log.info("Topic created: {} for Huawei Cloud instance: {}", topic.getTopicName(), config.getInstanceId());
    }

    @Override
    protected void doUpdateTopic(TopicInfo topic) throws Exception {
        log.info("Updating topic: {} for Huawei Cloud instance: {}",
            topic.getTopicName(), config.getInstanceId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("total_read_queue_num", topic.getReadQueueNums() != null && topic.getReadQueueNums() > 0 ? topic.getReadQueueNums() : 16);
        body.put("total_write_queue_num", topic.getWriteQueueNums() != null && topic.getWriteQueueNums() > 0 ? topic.getWriteQueueNums() : 16);
        body.put("permission", 6);

        String jsonBody = CloudApiHttpClient.toJson(body);
        callApi("PUT", buildTopicsPath() + "/" + topic.getTopicName(), null, jsonBody);
        log.info("Topic updated: {} for Huawei Cloud instance: {}", topic.getTopicName(), config.getInstanceId());
    }

    @Override
    protected void doDeleteTopic(String topic, Optional<String> namespace) throws Exception {
        log.info("Deleting topic: {} for Huawei Cloud instance: {}", topic, config.getInstanceId());
        callApi("DELETE", buildTopicsPath() + "/" + topic, null, null);
        log.info("Topic deleted: {} for Huawei Cloud instance: {}", topic, config.getInstanceId());
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
        log.info("Listing consumer groups for Huawei Cloud instance: {}", config.getInstanceId());
        try {
            String path = buildGroupsPath();
            Map<String, Object> result = callApi("GET", path, null, null);

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groupList =
                (List<Map<String, Object>>) result.get("groups");
            if (groupList == null || groupList.isEmpty()) {
                return Collections.emptyList();
            }

            List<ConsumerGroupInfo> groups = new ArrayList<>();
            for (Map<String, Object> groupData : groupList) {
                ConsumerGroupInfo group = new ConsumerGroupInfo();
                group.setConsumerGroupName(CloudApiHttpClient.getString(groupData, "name"));
                group.setClusterName(config.getInstanceId());
                group.setOnline(CloudApiHttpClient.getBoolean(groupData, "enabled", true));
                groups.add(group);
            }

            log.info("Listed {} consumer groups for Huawei Cloud instance: {}",
                groups.size(), config.getInstanceId());
            return groups;
        } catch (CloudApiException e) {
            log.error("Failed to list consumer groups for Huawei Cloud: {}", e.getMessage());
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
        log.info("Creating consumer group: {} for Huawei Cloud instance: {}",
            consumerGroup.getConsumerGroupName(), config.getInstanceId());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("name", consumerGroup.getConsumerGroupName());
        body.put("enabled", true);
        body.put("broadcast", false);
        body.put("retry_max_time", 16);

        String jsonBody = CloudApiHttpClient.toJson(body);
        callApi("POST", buildGroupsPath(), null, jsonBody);
        log.info("Consumer group created: {}", consumerGroup.getConsumerGroupName());
    }

    @Override
    protected void doUpdateConsumerGroup(ConsumerGroupInfo consumerGroup) throws Exception {
        throw new UnsupportedOperationException(
            "Huawei Cloud consumer group update is not directly supported via Dashboard API.");
    }

    @Override
    protected void doDeleteConsumerGroup(String consumerGroup, Optional<String> namespace) throws Exception {
        log.info("Deleting consumer group: {} for Huawei Cloud instance: {}",
            consumerGroup, config.getInstanceId());
        callApi("DELETE", buildGroupsPath() + "/" + consumerGroup, null, null);
        log.info("Consumer group deleted: {}", consumerGroup);
    }

    @Override
    protected List<SubscriptionInfo> doListSubscriptions(String groupName) throws Exception {
        log.warn("Subscription listing not directly available for Huawei Cloud DMS");
        return Collections.emptyList();
    }

    @Override
    protected void doResetConsumerGroupOffset(String groupName, String topic, long timestamp) throws Exception {
        throw new UnsupportedOperationException(
            "Consumer group offset reset not directly available via Huawei Cloud DMS API.");
    }

    // ==================== Client Operations ====================

    @Override
    protected List<ClientInstance> doListClientInstances(Optional<String> topic, Optional<String> group) throws Exception {
        log.warn("Client instance listing not directly available for Huawei Cloud DMS");
        return Collections.emptyList();
    }

    @Override
    protected Optional<ClientInstance> doGetClientInstance(String clientId) throws Exception {
        return Optional.empty();
    }

    // ==================== SDK-HMAC-SHA256 API Signing ====================

    /**
     * Call Huawei Cloud DMS API using SDK-HMAC-SHA256 signing.
     */
    private Map<String, Object> callApi(String method, String path, Map<String, String> queryParams,
                                        String body) throws CloudApiException {
        Instant now = Instant.now();
        String xSdkDate = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC).format(now);
        String dateStamp = DateTimeFormatter.ofPattern("yyyyMMdd")
            .withZone(ZoneOffset.UTC).format(now);

        String contentType = (body != null && !body.isEmpty()) ? "application/json" : "";
        String hashedPayload = CloudApiHttpClient.sha256Hex(body != null ? body : "");

        // Build canonical query string
        String canonicalQueryString = "";
        if (queryParams != null && !queryParams.isEmpty()) {
            TreeMap<String, String> sorted = new TreeMap<>(queryParams);
            canonicalQueryString = CloudApiHttpClient.buildQueryString(sorted);
        }

        // Build canonical headers
        TreeMap<String, String> headerMap = new TreeMap<>();
        headerMap.put("host", resolvedEndpoint);
        headerMap.put("x-sdk-date", xSdkDate);
        if (!contentType.isEmpty()) {
            headerMap.put("content-type", contentType);
        }

        StringBuilder canonicalHeaders = new StringBuilder();
        StringBuilder signedHeaders = new StringBuilder();
        for (Map.Entry<String, String> entry : headerMap.entrySet()) {
            canonicalHeaders.append(entry.getKey()).append(":").append(entry.getValue()).append("\n");
            if (signedHeaders.length() > 0) {
                signedHeaders.append(";");
            }
            signedHeaders.append(entry.getKey());
        }

        // Build Canonical Request
        String canonicalRequest = method.toUpperCase() + "\n"
            + path + "\n"
            + canonicalQueryString + "\n"
            + canonicalHeaders + "\n"
            + signedHeaders + "\n"
            + hashedPayload;

        // Build String to Sign
        String credentialScope = dateStamp + "/" + config.getRegionId() + "/" + SERVICE + "/sdk_request";
        String hashedCanonicalRequest = CloudApiHttpClient.sha256Hex(canonicalRequest);
        String stringToSign = "SDK-HMAC-SHA256\n"
            + xSdkDate + "\n"
            + credentialScope + "\n"
            + hashedCanonicalRequest;

        // Calculate Signature
        byte[] kDate = CloudApiHttpClient.hmacSha256(
            config.getSecretKey().getBytes(StandardCharsets.UTF_8), dateStamp);
        byte[] kRegion = CloudApiHttpClient.hmacSha256(kDate, config.getRegionId());
        byte[] kService = CloudApiHttpClient.hmacSha256(kRegion, SERVICE);
        byte[] kSigning = CloudApiHttpClient.hmacSha256(kService, "sdk_request");
        String signature = CloudApiHttpClient.hmacSha256Hex(kSigning, stringToSign);

        // Build Authorization header
        String authorization = "SDK-HMAC-SHA256"
            + " Credential=" + config.getAccessKey() + "/" + credentialScope
            + ", SignedHeaders=" + signedHeaders
            + ", Signature=" + signature;

        // Execute request
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", authorization);
        headers.put("Host", resolvedEndpoint);
        headers.put("X-Sdk-Date", xSdkDate);
        if (!contentType.isEmpty()) {
            headers.put("Content-Type", contentType);
        }

        String url = "https://" + resolvedEndpoint + path;
        if (canonicalQueryString != null && !canonicalQueryString.isEmpty()) {
            url += "?" + canonicalQueryString;
        }

        String responseBody;
        switch (method.toUpperCase()) {
            case "POST":
                responseBody = httpClient.postJson(url, body != null ? body : "", headers);
                break;
            case "PUT":
                responseBody = httpClient.putJson(url, body != null ? body : "", headers);
                break;
            case "DELETE":
                responseBody = httpClient.delete(url, headers);
                break;
            default:
                responseBody = httpClient.get(url, headers);
                break;
        }

        // Parse response
        Map<String, Object> result = CloudApiHttpClient.parseJson(responseBody);

        // Check for error
        String errorCode = CloudApiHttpClient.getString(result, "error_code");
        if (errorCode != null && !errorCode.isEmpty()) {
            String errorMsg = CloudApiHttpClient.getString(result, "error_msg");
            throw new CloudApiException(
                String.format("Huawei Cloud API error: code=%s, msg=%s, path=%s",
                    errorCode, errorMsg, path));
        }

        return result;
    }

    // ==================== Path & Type Helpers ====================

    private String buildTopicsPath() {
        return String.format(API_PATH_PREFIX, projectId) + "/" + config.getInstanceId() + "/topics";
    }

    private String buildGroupsPath() {
        return String.format(API_PATH_PREFIX, projectId) + "/" + config.getInstanceId() + "/groups";
    }

    private TopicType mapHuaweiTopicType(int messageType) {
        switch (messageType) {
            case 0: return TopicType.NORMAL;
            case 1: return TopicType.FIFO;
            case 2: return TopicType.DELAY;
            case 4: return TopicType.TRANSACTION;
            default: return TopicType.NORMAL;
        }
    }

    private int mapToHuaweiTopicType(TopicType type) {
        if (type == null) return 0;
        switch (type) {
            case NORMAL: return 0;
            case FIFO: return 1;
            case DELAY: return 2;
            case TRANSACTION: return 4;
            default: return 0;
        }
    }
}
