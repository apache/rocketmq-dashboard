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
import org.apache.rocketmq.dashboard.model.CloudProviderConfig;
import org.apache.rocketmq.dashboard.model.ClusterCapability;
import org.apache.rocketmq.dashboard.model.ClusterTopology;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aliyun (Alibaba Cloud) RocketMQ cluster provider implementation.
 *
 * <p>Per RIP-1 ARCH-01, this adapter implements the {@link org.apache.rocketmq.dashboard.architecture.ClusterProvider}
 * SPI for Aliyun RocketMQ (formerly ONS). It uses the Aliyun OpenAPI with
 * HMAC-SHA1 signature authentication to call REST endpoints at
 * {@code rocketmq.aliyuncs.com}.</p>
 *
 * <h3>Aliyun API Signing (Signature Version 1.0)</h3>
 * <ol>
 *   <li>Build canonicalized query string from sorted parameters</li>
 *   <li>Construct StringToSign = GET&%2F&percentEncode(canonicalizedQueryString)</li>
 *   <li>Sign with HMAC-SHA1 using SecretKey + "&amp;"</li>
 *   <li>Append Signature to query parameters</li>
 * </ol>
 *
 * <h3>Key Aliyun RocketMQ API Actions</h3>
 * <ul>
 *   <li>{@code OnsInstanceBaseInfo} - Get instance metadata and status</li>
 *   <li>{@code OnsInstanceInServiceList} - List running instances</li>
 *   <li>{@code OnsTopicList} - List topics in the instance</li>
 *   <li>{@code OnsGroupList} - List consumer groups</li>
 * </ul>
 *
 * @see AbstractCloudClusterProvider
 * @see CloudApiHttpClient
 */
public class AliyunClusterProvider extends AbstractCloudClusterProvider {

    /** Aliyun RocketMQ API endpoint pattern. */
    private static final String DEFAULT_ENDPOINT_PATTERN = "rocketmq.%s.aliyuncs.com";

    /** Aliyun RocketMQ API version. */
    private static final String API_VERSION = "2022-08-01";

    /** Aliyun API signature method. */
    private static final String SIGNATURE_METHOD = "HMAC-SHA1";

    /** Aliyun API signature version. */
    private static final String SIGNATURE_VERSION = "1.0";

    /** Resolved API endpoint. */
    private String resolvedEndpoint;

    /** Shared HTTP client for API calls. */
    private CloudApiHttpClient httpClient;

    public AliyunClusterProvider(CloudProviderConfig config) {
        super(config);
    }

    @Override
    protected void doInitialize() throws Exception {
        this.httpClient = new CloudApiHttpClient();

        // Resolve endpoint
        if (config.getEndpoint() != null && !config.getEndpoint().trim().isEmpty()) {
            this.resolvedEndpoint = config.getEndpoint();
        } else {
            this.resolvedEndpoint = String.format(DEFAULT_ENDPOINT_PATTERN,
                config.getRegionId() != null ? config.getRegionId() : "cn-hangzhou");
        }

        // Validate credentials by calling a lightweight API
        try {
            Map<String, Object> result = callApi("OnsInstanceBaseInfo", buildBaseParams());
            log.info("Aliyun cluster provider initialized: instance={}, region={}, endpoint={}, status={}",
                config.getInstanceId(), config.getRegionId(), resolvedEndpoint,
                CloudApiHttpClient.getString(result, "Status"));
        } catch (CloudApiException e) {
            log.warn("Aliyun credential validation failed (may still work for other APIs): {}",
                e.getMessage());
            // Don't fail initialization - the instance might not exist yet or
            // the API version might differ. Continue with initialization.
            log.info("Aliyun cluster provider initialized (with warning): instance={}, region={}, endpoint={}",
                config.getInstanceId(), config.getRegionId(), resolvedEndpoint);
        }
    }

    @Override
    protected void doShutdown() {
        log.info("Aliyun cluster provider shutdown: instance={}", config.getInstanceId());
        // HttpClient doesn't need explicit cleanup in Java 11+
    }

    @Override
    protected ClusterTopology doDiscoverTopology() throws Exception {
        ClusterTopology topology = new ClusterTopology();
        topology.setClusterName(config.getInstanceId());

        try {
            // Call OnsInstanceBaseInfo to get instance details
            Map<String, String> params = buildBaseParams();
            params.put("InstanceId", config.getInstanceId());
            Map<String, Object> result = callApi("OnsInstanceBaseInfo", params);

            // Parse instance info from response
            @SuppressWarnings("unchecked")
            Map<String, Object> instanceBaseInfo = (Map<String, Object>) result.get("InstanceBaseInfo");
            if (instanceBaseInfo != null) {
                String instanceName = CloudApiHttpClient.getString(instanceBaseInfo, "InstanceName");
                String instanceStatus = CloudApiHttpClient.getString(instanceBaseInfo, "InstanceStatus");

                if (instanceName != null) {
                    topology.setClusterName(instanceName);
                }

                // Add a virtual broker node representing the managed instance
                topology.addNode(
                    config.getInstanceId(),
                    0L,
                    resolvedEndpoint,
                    "BROKER"
                );

                // Mark the node status based on instance status
                // Aliyun instance status: 1=deploying, 2=running, 5=frozen
                if (instanceBaseInfo.get("InstanceStatus") instanceof Number) {
                    int status = ((Number) instanceBaseInfo.get("InstanceStatus")).intValue();
                    String nodeStatus = status == 2 ? "ONLINE" : (status == 5 ? "OFFLINE" : "UNKNOWN");
                    if (!topology.getBrokerNodes().isEmpty()) {
                        topology.getBrokerNodes().get(0).setStatus(nodeStatus);
                    }
                }

                // Extract namespace info if available
                String namespaceId = CloudApiHttpClient.getString(instanceBaseInfo, "NamespaceId");
                if (namespaceId != null) {
                    topology.getBrokerNodes().get(0).getMetadata().put("namespaceId", namespaceId);
                }

                log.info("Aliyun topology discovered: instance={}, status={}", instanceName, instanceStatus);
            }

            // Try to list proxy nodes (5.0 instances have proxy endpoints)
            try {
                Map<String, String> endpointParams = buildBaseParams();
                endpointParams.put("InstanceId", config.getInstanceId());
                Map<String, Object> endpointResult = callApi("OnsInstanceEndpoint", endpointParams);

                @SuppressWarnings("unchecked")
                Map<String, Object> tcpEndpoint = (Map<String, Object>) endpointResult.get("TcpEndpoint");
                if (tcpEndpoint != null) {
                    String endpoint = CloudApiHttpClient.getString(tcpEndpoint, "Endpoint");
                    if (endpoint != null && !endpoint.isEmpty()) {
                        topology.getNamesrvAddresses().add(endpoint);
                        topology.addNode("proxy-1", 0L, endpoint, "PROXY");
                        log.debug("Aliyun proxy endpoint discovered: {}", endpoint);
                    }
                }
            } catch (CloudApiException e) {
                log.debug("Could not retrieve Aliyun endpoint info (non-critical): {}", e.getMessage());
            }

        } catch (CloudApiException e) {
            log.error("Failed to discover Aliyun topology for instance {}: {}",
                config.getInstanceId(), e.getMessage());
            // Return partial topology with at least the instance as a node
            topology.addNode(config.getInstanceId(), 0L, resolvedEndpoint, "BROKER");
        }

        return topology;
    }

    @Override
    protected ClusterCapability doDetectCapability() throws Exception {
        // Aliyun RocketMQ 5.0 supports all modern features via OpenAPI
        ClusterCapability capability = new ClusterCapability();
        capability.setNamespaceSupported(true);
        capability.setLiteTopicSupported(true);
        capability.setPopConsumeSupported(true);
        capability.setAclV2Supported(true);
        capability.setGrpcClientSupported(false);       // Cloud uses OpenAPI, not gRPC
        capability.setDelayMessageSupported(true);
        capability.setTransactionMessageSupported(true);
        capability.setFifoMessageSupported(true);
        capability.setArchitectureVersion("5.x-cloud");
        capability.setRocketmqVersion("5.x-aliyun");
        capability.setExtendedCapabilities(new HashSet<>(Arrays.asList(
            "dlq-batch-resend", "message-trace-v5", "cloud-auto-scaling", "cloud-monitor",
            "topicTypeValidation", "metricsQuery", "clientTrace"
        )));

        // Try to detect actual version from instance info
        try {
            Map<String, String> params = buildBaseParams();
            params.put("InstanceId", config.getInstanceId());
            Map<String, Object> result = callApi("OnsInstanceBaseInfo", params);

            @SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) result.get("InstanceBaseInfo");
            if (info != null) {
                String version = CloudApiHttpClient.getString(info, "RocketMQVersion");
                if (version != null && !version.isEmpty()) {
                    capability.setRocketmqVersion(version + "-aliyun");
                }
            }
        } catch (CloudApiException e) {
            log.debug("Could not detect Aliyun version from API, using defaults: {}", e.getMessage());
        }

        log.info("Aliyun cluster capability detected: version={}, namespace={}, liteTopic={}",
            capability.getRocketmqVersion(),
            capability.isNamespaceSupported(),
            capability.isLiteTopicSupported());
        return capability;
    }

    @Override
    protected List<String> doListNodeIds() throws Exception {
        List<String> nodeIds = new ArrayList<>();

        try {
            // The managed instance itself is the primary "node"
            nodeIds.add(config.getInstanceId());

            // Try to get endpoint info for more detailed node listing
            Map<String, String> params = buildBaseParams();
            params.put("InstanceId", config.getInstanceId());
            Map<String, Object> result = callApi("OnsInstanceEndpoint", params);

            @SuppressWarnings("unchecked")
            Map<String, Object> tcpEndpoint = (Map<String, Object>) result.get("TcpEndpoint");
            if (tcpEndpoint != null) {
                String endpoint = CloudApiHttpClient.getString(tcpEndpoint, "Endpoint");
                if (endpoint != null && !endpoint.isEmpty()) {
                    nodeIds.add("endpoint:" + endpoint);
                }
            }
        } catch (CloudApiException e) {
            log.warn("Failed to list Aliyun nodes for instance {}: {}",
                config.getInstanceId(), e.getMessage());
            // Return at least the instance ID
            if (nodeIds.isEmpty()) {
                nodeIds.add(config.getInstanceId());
            }
        }

        return nodeIds;
    }

    @Override
    protected boolean doHealthCheck() throws Exception {
        try {
            Map<String, String> params = buildBaseParams();
            params.put("InstanceId", config.getInstanceId());
            Map<String, Object> result = callApi("OnsInstanceBaseInfo", params);

            @SuppressWarnings("unchecked")
            Map<String, Object> info = (Map<String, Object>) result.get("InstanceBaseInfo");
            if (info != null && info.get("InstanceStatus") instanceof Number) {
                int status = ((Number) info.get("InstanceStatus")).intValue();
                // Aliyun status: 2 = running (healthy)
                boolean healthy = status == 2;
                log.debug("Aliyun health check: instance={}, status={}, healthy={}",
                    config.getInstanceId(), status, healthy);
                return healthy;
            }
            return true; // If we can reach the API, consider it healthy
        } catch (CloudApiException e) {
            log.error("Aliyun health check failed for instance {}: {}",
                config.getInstanceId(), e.getMessage());
            return false;
        }
    }

    // ==================== Aliyun API Signing & Calling ====================

    /**
     * Call an Aliyun RocketMQ API action.
     *
     * @param action the API action name (e.g., "OnsInstanceBaseInfo")
     * @param extraParams additional parameters beyond the base parameters
     * @return parsed JSON response as a Map
     */
    Map<String, Object> callApi(String action, Map<String, String> extraParams) throws CloudApiException {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.putAll(buildCommonParams(action));
        if (extraParams != null) {
            params.putAll(extraParams);
        }

        // Sign the request
        String signature = signRequest(params);
        params.put("Signature", signature);

        // Build URL and execute
        String url = CloudApiHttpClient.buildUrl("https://" + resolvedEndpoint, params);
        String responseBody = httpClient.get(url, null);

        // Parse and check for API errors
        Map<String, Object> result = CloudApiHttpClient.parseJson(responseBody);
        checkApiError(result);

        return result;
    }

    /**
     * Build base parameters for API calls (InstanceId only).
     */
    private Map<String, String> buildBaseParams() {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        if (config.getInstanceId() != null) {
            params.put("InstanceId", config.getInstanceId());
        }
        return params;
    }

    /**
     * Build common parameters required for every Aliyun API call.
     */
    private Map<String, String> buildCommonParams(String action) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("Action", action);
        params.put("Version", API_VERSION);
        params.put("Format", "JSON");
        params.put("AccessKeyId", config.getAccessKey());
        params.put("SignatureMethod", SIGNATURE_METHOD);
        params.put("SignatureVersion", SIGNATURE_VERSION);
        params.put("SignatureNonce", UUID.randomUUID().toString());
        params.put("Timestamp", DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
            .withZone(ZoneOffset.UTC)
            .format(Instant.now()));
        params.put("RegionId", config.getRegionId() != null ? config.getRegionId() : "cn-hangzhou");
        return params;
    }

    /**
     * Sign the request using Aliyun Signature V1.0 (HMAC-SHA1).
     *
     * <p>The signing process:
     * <ol>
     *   <li>Sort all parameters by key name</li>
     *   <li>Build canonicalized query string: key1=val1&key2=val2...</li>
     *   <li>StringToSign = HTTPMethod&percentEncode(/)&percentEncode(canonicalizedQueryString)</li>
     *   <li>Signature = Base64(HMAC-SHA1(accessKeySecret + "&", stringToSign))</li>
     * </ol>
     */
    private String signRequest(LinkedHashMap<String, String> params) throws CloudApiException {
        // Sort parameters by key
        LinkedHashMap<String, String> sorted = new LinkedHashMap<>();
        params.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(e -> sorted.put(e.getKey(), e.getValue()));

        // Build canonicalized query string
        String canonicalizedQueryString = CloudApiHttpClient.buildQueryString(sorted);

        // Build string to sign
        String stringToSign = "GET&" + CloudApiHttpClient.percentEncode("/") + "&"
            + CloudApiHttpClient.percentEncode(canonicalizedQueryString);

        // Sign with HMAC-SHA1
        String signKey = config.getSecretKey() + "&";
        return CloudApiHttpClient.hmacSha1(stringToSign, signKey);
    }

    /**
     * Check if the API response contains an error.
     */
    private void checkApiError(Map<String, Object> result) throws CloudApiException {
        String code = CloudApiHttpClient.getString(result, "Code");
        if (code != null && !code.isEmpty() && !"200".equals(code) && !"OK".equals(code)) {
            String message = CloudApiHttpClient.getString(result, "Message");
            String requestId = CloudApiHttpClient.getString(result, "RequestId");
            throw new CloudApiException(
                String.format("Aliyun API error: code=%s, message=%s, requestId=%s",
                    code, message, requestId));
        }
    }

    /**
     * Get the resolved API endpoint.
     */
    public String getResolvedEndpoint() {
        return resolvedEndpoint;
    }

    /**
     * Get the HTTP client for use by AliyunMetadataProvider.
     */
    CloudApiHttpClient getHttpClient() {
        return httpClient;
    }
}
