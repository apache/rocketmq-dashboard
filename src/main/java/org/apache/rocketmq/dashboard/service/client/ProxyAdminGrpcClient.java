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
package org.apache.rocketmq.dashboard.service.client;

import apache.rocketmq.proxy.admin.v1.AdminCode;
import apache.rocketmq.proxy.admin.v1.BatchConsumeClientDiagnostics;
import apache.rocketmq.proxy.admin.v1.ClientDetail;
import apache.rocketmq.proxy.admin.v1.ClientInstance;
import apache.rocketmq.proxy.admin.v1.DescribeBatchConsumeDiagnosticsRequest;
import apache.rocketmq.proxy.admin.v1.DescribeBatchConsumeDiagnosticsResponse;
import apache.rocketmq.proxy.admin.v1.DescribeClientRequest;
import apache.rocketmq.proxy.admin.v1.DescribeClientResponse;
import apache.rocketmq.proxy.admin.v1.DescribePopReceiptHandlesRequest;
import apache.rocketmq.proxy.admin.v1.DescribePopReceiptHandlesResponse;
import apache.rocketmq.proxy.admin.v1.DisconnectClientRequest;
import apache.rocketmq.proxy.admin.v1.DisconnectClientResponse;
import apache.rocketmq.proxy.admin.v1.GetConfigRequest;
import apache.rocketmq.proxy.admin.v1.GetConfigResponse;
import apache.rocketmq.proxy.admin.v1.ListClientsByGroupRequest;
import apache.rocketmq.proxy.admin.v1.ListClientsByGroupResponse;
import apache.rocketmq.proxy.admin.v1.ListClientsByTopicRequest;
import apache.rocketmq.proxy.admin.v1.ListClientsByTopicResponse;
import apache.rocketmq.proxy.admin.v1.ListClientsRequest;
import apache.rocketmq.proxy.admin.v1.ListClientsResponse;
import apache.rocketmq.proxy.admin.v1.PopReceiptHandleInfo;
import apache.rocketmq.proxy.admin.v1.ProxyClientAdminServiceGrpc;
import apache.rocketmq.proxy.admin.v1.ProxyClientAdminServiceGrpc.ProxyClientAdminServiceBlockingStub;
import apache.rocketmq.proxy.admin.v1.ProxyRuntimeConfig;
import apache.rocketmq.proxy.admin.v1.UpdateConfigRequest;
import apache.rocketmq.proxy.admin.v1.UpdateConfigResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * gRPC client wrapper for RIP-2 Proxy Admin Service.
 *
 * <p>Manages a gRPC channel to the Proxy Admin server (default port 8086)
 * and provides typed methods for all 10 RPCs defined in RIP-2:
 * <ul>
 *   <li>M1: ListClients, DescribeClient, ListClientsByGroup, ListClientsByTopic</li>
 *   <li>M2: GetConfig, UpdateConfig, DisconnectClient</li>
 *   <li>M3: DescribePopReceiptHandles</li>
 *   <li>M4: DescribeBatchConsumeDiagnostics</li>
 *   <li>Streaming: SubscribeRouteEvents (handled separately via async stub)</li>
 * </ul>
 *
 * <p>Lifecycle: created as a Spring bean, initialized on first use (lazy),
 * shut down by Spring's @PreDestroy or explicit shutdown() call.</p>
 *
 * <p>Resilience: if the gRPC channel cannot be established, all methods return
 * empty results rather than throwing exceptions, enabling graceful degradation
 * to the Remoting-only fallback path.</p>
 */
public class ProxyAdminGrpcClient {

    private static final Logger log = LoggerFactory.getLogger(ProxyAdminGrpcClient.class);

    private static final int DEFAULT_ADMIN_PORT = 8086;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int CONNECTION_TIMEOUT_SECONDS = 10;

    private final String proxyHost;
    private final int proxyAdminPort;

    private ManagedChannel channel;
    private ProxyClientAdminServiceBlockingStub stub;
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private volatile boolean available = false;

    /**
     * Create a new ProxyAdminGrpcClient.
     *
     * @param proxyAddress proxy address in format "host:port" (data plane port, e.g. "localhost:8080").
     *                     The admin port is derived by replacing the port with the admin port.
     */
    public ProxyAdminGrpcClient(String proxyAddress) {
        this(proxyAddress, DEFAULT_ADMIN_PORT);
    }

    /**
     * Create a new ProxyAdminGrpcClient with explicit host and admin port.
     *
     * @param proxyAddress   proxy address in format "host:port" or "host"
     * @param proxyAdminPort the admin gRPC server port
     */
    public ProxyAdminGrpcClient(String proxyAddress, int proxyAdminPort) {
        String host = extractHost(proxyAddress);
        this.proxyHost = host;
        this.proxyAdminPort = proxyAdminPort;
    }

    /**
     * Check whether the gRPC channel is available and healthy.
     */
    public boolean isAvailable() {
        return available && channel != null && !channel.isShutdown();
    }

    /**
     * Lazy-initialize the gRPC channel on first use.
     */
    private synchronized void ensureInitialized() {
        if (initialized.get()) {
            return;
        }
        try {
            log.info("Initializing gRPC channel to Proxy Admin at {}:{}", proxyHost, proxyAdminPort);
            this.channel = ManagedChannelBuilder
                .forAddress(proxyHost, proxyAdminPort)
                .usePlaintext()
                .build();
            this.stub = ProxyClientAdminServiceGrpc.newBlockingStub(channel);
            this.available = true;
            log.info("gRPC channel to Proxy Admin established at {}:{}", proxyHost, proxyAdminPort);
        } catch (Exception e) {
            log.warn("Failed to initialize gRPC channel to Proxy Admin at {}:{}. "
                + "gRPC client queries will return empty results. Error: {}",
                proxyHost, proxyAdminPort, e.getMessage());
            this.available = false;
        } finally {
            initialized.set(true);
        }
    }

    /**
     * List online gRPC clients with optional filters and pagination.
     * RIP-2 M1: ListClients RPC.
     *
     * @param group          optional consumer group filter
     * @param topic          optional topic filter
     * @param clientIdPrefix optional client ID prefix filter
     * @param pageNum        page number, starting from 1
     * @param pageSize       page size, max 100
     * @return list of ClientInstance protos, or empty list on failure
     */
    public List<ClientInstance> listClients(String group, String topic,
                                             String clientIdPrefix, int pageNum, int pageSize) {
        ensureInitialized();
        if (!isAvailable()) {
            return Collections.emptyList();
        }

        try {
            ListClientsRequest.Builder builder = ListClientsRequest.newBuilder()
                .setPageNum(Math.max(pageNum, 1))
                .setPageSize(Math.min(pageSize, MAX_PAGE_SIZE));

            if (group != null && !group.isEmpty()) {
                builder.setGroup(group);
            }
            if (topic != null && !topic.isEmpty()) {
                builder.setTopic(topic);
            }
            if (clientIdPrefix != null && !clientIdPrefix.isEmpty()) {
                builder.setClientIdPrefix(clientIdPrefix);
            }

            ListClientsResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .listClients(builder.build());

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response.getListList();
            }

            log.warn("ListClients returned non-OK status: {} - {}", response.getCode(), response.getMessage());
            return Collections.emptyList();
        } catch (StatusRuntimeException e) {
            log.warn("ListClients gRPC call failed: {}", e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("ListClients unexpected error", e);
            return Collections.emptyList();
        }
    }

    /**
     * Describe a single client by clientId.
     * RIP-2 M1: DescribeClient RPC.
     *
     * @param clientId the unique client identifier
     * @return ClientDetail proto, or null if not found or on failure
     */
    public ClientDetail describeClient(String clientId) {
        ensureInitialized();
        if (!isAvailable() || clientId == null || clientId.isEmpty()) {
            return null;
        }

        try {
            DescribeClientRequest request = DescribeClientRequest.newBuilder()
                .setClientId(clientId)
                .build();

            DescribeClientResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .describeClient(request);

            if (response.getCode() == AdminCode.ADMIN_CODE_OK && response.hasClientDetail()) {
                return response.getClientDetail();
            }

            log.debug("DescribeClient returned non-OK status for {}: {} - {}",
                clientId, response.getCode(), response.getMessage());
            return null;
        } catch (StatusRuntimeException e) {
            log.warn("DescribeClient gRPC call failed for {}: {}", clientId, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("DescribeClient unexpected error for {}", clientId, e);
            return null;
        }
    }

    /**
     * List clients by consumer group.
     * RIP-2 M1: ListClientsByGroup RPC.
     *
     * @param group    consumer group name
     * @param pageNum  page number, starting from 1
     * @param pageSize page size, max 100
     * @return list of ClientInstance protos, or empty list on failure
     */
    public List<ClientInstance> listClientsByGroup(String group, int pageNum, int pageSize) {
        ensureInitialized();
        if (!isAvailable() || group == null || group.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            ListClientsByGroupRequest request = ListClientsByGroupRequest.newBuilder()
                .setGroup(group)
                .setPageNum(Math.max(pageNum, 1))
                .setPageSize(Math.min(pageSize, MAX_PAGE_SIZE))
                .build();

            ListClientsByGroupResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .listClientsByGroup(request);

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response.getListList();
            }

            log.debug("ListClientsByGroup returned non-OK status for {}: {} - {}",
                group, response.getCode(), response.getMessage());
            return Collections.emptyList();
        } catch (StatusRuntimeException e) {
            log.warn("ListClientsByGroup gRPC call failed for {}: {}", group, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("ListClientsByGroup unexpected error for {}", group, e);
            return Collections.emptyList();
        }
    }

    /**
     * List clients by topic subscription.
     * RIP-2 M1: ListClientsByTopic RPC.
     *
     * @param topic    topic name
     * @param pageNum  page number, starting from 1
     * @param pageSize page size, max 100
     * @return list of ClientInstance protos, or empty list on failure
     */
    public List<ClientInstance> listClientsByTopic(String topic, int pageNum, int pageSize) {
        ensureInitialized();
        if (!isAvailable() || topic == null || topic.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            ListClientsByTopicRequest request = ListClientsByTopicRequest.newBuilder()
                .setTopic(topic)
                .setPageNum(Math.max(pageNum, 1))
                .setPageSize(Math.min(pageSize, MAX_PAGE_SIZE))
                .build();

            ListClientsByTopicResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .listClientsByTopic(request);

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response.getListList();
            }

            log.debug("ListClientsByTopic returned non-OK status for {}: {} - {}",
                topic, response.getCode(), response.getMessage());
            return Collections.emptyList();
        } catch (StatusRuntimeException e) {
            log.warn("ListClientsByTopic gRPC call failed for {}: {}", topic, e.getMessage());
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("ListClientsByTopic unexpected error for {}", topic, e);
            return Collections.emptyList();
        }
    }

    // ==================== M2: Runtime Config & Connection Management ====================

    /**
     * Get current runtime configuration of the proxy.
     * RIP-2 M2: GetConfig RPC.
     *
     * @return ProxyRuntimeConfig proto, or null on failure
     */
    public ProxyRuntimeConfig getConfig() {
        ensureInitialized();
        if (!isAvailable()) {
            return null;
        }

        try {
            GetConfigResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .getConfig(GetConfigRequest.newBuilder().build());

            if (response.getCode() == AdminCode.ADMIN_CODE_OK && response.hasConfig()) {
                return response.getConfig();
            }

            log.warn("GetConfig returned non-OK status: {} - {}", response.getCode(), response.getMessage());
            return null;
        } catch (StatusRuntimeException e) {
            log.warn("GetConfig gRPC call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("GetConfig unexpected error", e);
            return null;
        }
    }

    /**
     * Hot-update runtime configuration without restarting proxy.
     * RIP-2 M2: UpdateConfig RPC.
     *
     * @param config the desired config state
     * @return UpdateConfigResponse proto, or null on failure
     */
    public UpdateConfigResponse updateConfig(ProxyRuntimeConfig config) {
        ensureInitialized();
        if (!isAvailable() || config == null) {
            return null;
        }

        try {
            UpdateConfigRequest request = UpdateConfigRequest.newBuilder()
                .setConfig(config)
                .build();

            UpdateConfigResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .updateConfig(request);

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response;
            }

            log.warn("UpdateConfig returned non-OK status: {} - {}", response.getCode(), response.getMessage());
            return null;
        } catch (StatusRuntimeException e) {
            log.warn("UpdateConfig gRPC call failed: {}", e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("UpdateConfig unexpected error", e);
            return null;
        }
    }

    /**
     * Force disconnect a specific client connection.
     * RIP-2 M2: DisconnectClient RPC.
     *
     * @param clientId the unique client identifier to disconnect
     * @param reason   human-readable reason for audit logging
     * @return true if client was disconnected, false otherwise
     */
    public boolean disconnectClient(String clientId, String reason) {
        ensureInitialized();
        if (!isAvailable() || clientId == null || clientId.isEmpty()) {
            return false;
        }

        try {
            DisconnectClientRequest request = DisconnectClientRequest.newBuilder()
                .setClientId(clientId)
                .setReason(reason != null ? reason : "Dashboard admin action")
                .build();

            DisconnectClientResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .disconnectClient(request);

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response.getDisconnected();
            }

            log.warn("DisconnectClient returned non-OK status for {}: {} - {}",
                clientId, response.getCode(), response.getMessage());
            return false;
        } catch (StatusRuntimeException e) {
            log.warn("DisconnectClient gRPC call failed for {}: {}", clientId, e.getMessage());
            return false;
        } catch (Exception e) {
            log.error("DisconnectClient unexpected error for {}", clientId, e);
            return false;
        }
    }

    // ==================== M3: POP Diagnostics ====================

    /**
     * Query POP receipt handles for diagnostics.
     * RIP-2 M3: DescribePopReceiptHandles RPC.
     *
     * @param group    consumer group name (required)
     * @param topic    optional topic filter
     * @param pageNum  page number, starting from 1
     * @param pageSize page size, max 100
     * @return response proto, or null on failure
     */
    public DescribePopReceiptHandlesResponse describePopReceiptHandles(
            String group, String topic, int pageNum, int pageSize) {
        ensureInitialized();
        if (!isAvailable() || group == null || group.isEmpty()) {
            return null;
        }

        try {
            DescribePopReceiptHandlesRequest.Builder builder = DescribePopReceiptHandlesRequest.newBuilder()
                .setGroup(group)
                .setPageNum(Math.max(pageNum, 1))
                .setPageSize(Math.min(pageSize, MAX_PAGE_SIZE));

            if (topic != null && !topic.isEmpty()) {
                builder.setTopic(topic);
            }

            DescribePopReceiptHandlesResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .describePopReceiptHandles(builder.build());

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response;
            }

            log.warn("DescribePopReceiptHandles returned non-OK for group {}: {} - {}",
                group, response.getCode(), response.getMessage());
            return null;
        } catch (StatusRuntimeException e) {
            log.warn("DescribePopReceiptHandles gRPC call failed for group {}: {}", group, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("DescribePopReceiptHandles unexpected error for group {}", group, e);
            return null;
        }
    }

    // ==================== M4: Batch Consumption Diagnostics ====================

    /**
     * Query batch consumption diagnostics.
     * RIP-2 M4: DescribeBatchConsumeDiagnostics RPC.
     *
     * @param group    consumer group name (required)
     * @param topic    optional topic filter
     * @param clientId optional client ID filter
     * @param pageNum  page number, starting from 1
     * @param pageSize page size, max 100
     * @return response proto, or null on failure
     */
    public DescribeBatchConsumeDiagnosticsResponse describeBatchConsumeDiagnostics(
            String group, String topic, String clientId, int pageNum, int pageSize) {
        ensureInitialized();
        if (!isAvailable() || group == null || group.isEmpty()) {
            return null;
        }

        try {
            DescribeBatchConsumeDiagnosticsRequest.Builder builder =
                DescribeBatchConsumeDiagnosticsRequest.newBuilder()
                    .setGroup(group)
                    .setPageNum(Math.max(pageNum, 1))
                    .setPageSize(Math.min(pageSize, MAX_PAGE_SIZE));

            if (topic != null && !topic.isEmpty()) {
                builder.setTopic(topic);
            }
            if (clientId != null && !clientId.isEmpty()) {
                builder.setClientId(clientId);
            }

            DescribeBatchConsumeDiagnosticsResponse response = stub
                .withDeadlineAfter(CONNECTION_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .describeBatchConsumeDiagnostics(builder.build());

            if (response.getCode() == AdminCode.ADMIN_CODE_OK) {
                return response;
            }

            log.warn("DescribeBatchConsumeDiagnostics returned non-OK for group {}: {} - {}",
                group, response.getCode(), response.getMessage());
            return null;
        } catch (StatusRuntimeException e) {
            log.warn("DescribeBatchConsumeDiagnostics gRPC call failed for group {}: {}", group, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("DescribeBatchConsumeDiagnostics unexpected error for group {}", group, e);
            return null;
        }
    }

    // ==================== Accessors ====================

    /**
     * Get the proxy host this client connects to.
     */
    public String getProxyHost() {
        return proxyHost;
    }

    /**
     * Get the admin port this client connects to.
     */
    public int getProxyAdminPort() {
        return proxyAdminPort;
    }

    /**
     * Get the underlying ManagedChannel (for streaming operations).
     * Returns null if not initialized.
     */
    public ManagedChannel getChannel() {
        ensureInitialized();
        return channel;
    }

    // ==================== Lifecycle ====================

    /**
     * Shutdown the gRPC channel.
     */
    public void shutdown() {
        if (channel != null && !channel.isShutdown()) {
            try {
                channel.shutdown().awaitTermination(5, TimeUnit.SECONDS);
                log.info("gRPC channel to {}:{} shut down", proxyHost, proxyAdminPort);
            } catch (InterruptedException e) {
                channel.shutdownNow();
                Thread.currentThread().interrupt();
            }
            available = false;
        }
    }

    private static String extractHost(String proxyAddress) {
        if (proxyAddress == null || proxyAddress.isEmpty()) {
            return "localhost";
        }
        int colonIndex = proxyAddress.lastIndexOf(':');
        if (colonIndex > 0) {
            return proxyAddress.substring(0, colonIndex);
        }
        return proxyAddress;
    }
}
