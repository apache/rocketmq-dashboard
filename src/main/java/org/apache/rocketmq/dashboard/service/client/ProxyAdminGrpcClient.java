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
import apache.rocketmq.proxy.admin.v1.ClientDetail;
import apache.rocketmq.proxy.admin.v1.ClientInstance;
import apache.rocketmq.proxy.admin.v1.DescribeClientRequest;
import apache.rocketmq.proxy.admin.v1.DescribeClientResponse;
import apache.rocketmq.proxy.admin.v1.ListClientsByGroupRequest;
import apache.rocketmq.proxy.admin.v1.ListClientsByGroupResponse;
import apache.rocketmq.proxy.admin.v1.ListClientsByTopicRequest;
import apache.rocketmq.proxy.admin.v1.ListClientsByTopicResponse;
import apache.rocketmq.proxy.admin.v1.ListClientsRequest;
import apache.rocketmq.proxy.admin.v1.ListClientsResponse;
import apache.rocketmq.proxy.admin.v1.ProxyClientAdminServiceGrpc;
import apache.rocketmq.proxy.admin.v1.ProxyClientAdminServiceGrpc.ProxyClientAdminServiceBlockingStub;
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
 * <p>Manages a gRPC channel to the Proxy Admin server (default port 8082)
 * and provides typed methods for the 4 core RPCs defined in RIP-2 M1:
 * <ul>
 *   <li>ListClients - paginated listing with filters</li>
 *   <li>DescribeClient - detailed client info</li>
 *   <li>ListClientsByGroup - filter by consumer group</li>
 *   <li>ListClientsByTopic - filter by topic</li>
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

    private static final int DEFAULT_ADMIN_PORT = 8082;
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
