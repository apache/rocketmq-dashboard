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

import apache.rocketmq.proxy.admin.v1.ClientDetail;
import apache.rocketmq.proxy.admin.v1.ClientInstance;
import apache.rocketmq.proxy.admin.v1.DescribeBatchConsumeDiagnosticsResponse;
import apache.rocketmq.proxy.admin.v1.DescribePopReceiptHandlesResponse;
import apache.rocketmq.proxy.admin.v1.ProxyRuntimeConfig;
import apache.rocketmq.proxy.admin.v1.RouteChangeEventType;
import apache.rocketmq.proxy.admin.v1.SubscribeRouteEventsRequest;
import apache.rocketmq.proxy.admin.v1.SubscribeRouteEventsResponse;
import apache.rocketmq.proxy.admin.v1.ProxyClientAdminServiceGrpc;
import apache.rocketmq.proxy.admin.v1.UpdateConfigResponse;
import io.grpc.ManagedChannel;
import io.grpc.stub.StreamObserver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Multi-Proxy aggregation client for RIP-2 Proxy Admin Service.
 *
 * <p>Manages connections to multiple Proxy Admin gRPC endpoints (port 8086)
 * and provides aggregated results from all Proxy instances. This implements
 * the Dashboard-layer multi-Proxy aggregation requirement.</p>
 *
 * <h3>Aggregation Strategy</h3>
 * <ul>
 *   <li><b>M1 (ListClients/DescribeClient):</b> Fan-out to all proxies, merge + dedup by clientId</li>
 *   <li><b>M2 (GetConfig):</b> Fan-out to all proxies, return map of proxy→config</li>
 *   <li><b>M2 (UpdateConfig):</b> Broadcast to all proxies (cluster-wide hot update)</li>
 *   <li><b>M2 (DisconnectClient):</b> Fan-out to all proxies (client may be on any proxy)</li>
 *   <li><b>M3 (POP Diagnostics):</b> Fan-out + merge handles across proxies</li>
 *   <li><b>M4 (Batch Diagnostics):</b> Fan-out + merge diagnostics across proxies</li>
 *   <li><b>SubscribeRouteEvents:</b> Subscribe to all proxies, merge event streams</li>
 * </ul>
 *
 * <p>Thread-safety: all public methods are thread-safe.</p>
 */
public class MultiProxyAdminClient {

    private static final Logger log = LoggerFactory.getLogger(MultiProxyAdminClient.class);

    private final List<ProxyAdminGrpcClient> clients;
    private final String[] proxyAddresses;
    private final ExecutorService fanOutExecutor;

    /**
     * Create a MultiProxyAdminClient connecting to multiple proxy addresses.
     *
     * @param proxyAddresses array of proxy addresses in format "host:port"
     */
    public MultiProxyAdminClient(String[] proxyAddresses) {
        this(proxyAddresses, 8086);
    }

    /**
     * Create a MultiProxyAdminClient with explicit admin port.
     *
     * @param proxyAddresses array of proxy addresses
     * @param adminPort      the admin gRPC server port
     */
    public MultiProxyAdminClient(String[] proxyAddresses, int adminPort) {
        this.proxyAddresses = proxyAddresses != null ? proxyAddresses : new String[0];
        this.clients = new CopyOnWriteArrayList<>();
        this.fanOutExecutor = Executors.newFixedThreadPool(
            Math.max(this.proxyAddresses.length, 2),
            r -> {
                Thread t = new Thread(r, "multi-proxy-admin-fanout");
                t.setDaemon(true);
                return t;
            });

        for (String addr : this.proxyAddresses) {
            clients.add(new ProxyAdminGrpcClient(addr, adminPort));
        }

        log.info("MultiProxyAdminClient created with {} proxy endpoints: {}",
            this.proxyAddresses.length, String.join(", ", this.proxyAddresses));
    }

    // ==================== M1: Client Queries (Aggregated) ====================

    /**
     * List online clients from ALL proxy instances, merge + dedup by clientId.
     */
    public List<ClientInstance> listClients(String group, String topic,
                                             String clientIdPrefix, int pageNum, int pageSize) {
        Map<String, ClientInstance> merged = new LinkedHashMap<>();

        for (ProxyAdminGrpcClient client : clients) {
            try {
                List<ClientInstance> result = client.listClients(group, topic, clientIdPrefix, pageNum, pageSize);
                for (ClientInstance ci : result) {
                    merged.putIfAbsent(ci.getClientId(), ci);
                }
            } catch (Exception e) {
                log.warn("ListClients failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * Describe a client from ANY proxy (fan-out, return first match).
     */
    public ClientDetail describeClient(String clientId) {
        for (ProxyAdminGrpcClient client : clients) {
            try {
                ClientDetail detail = client.describeClient(clientId);
                if (detail != null) {
                    return detail;
                }
            } catch (Exception e) {
                log.warn("DescribeClient failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }
        return null;
    }

    /**
     * List clients by group from ALL proxies, merge + dedup.
     */
    public List<ClientInstance> listClientsByGroup(String group, int pageNum, int pageSize) {
        Map<String, ClientInstance> merged = new LinkedHashMap<>();

        for (ProxyAdminGrpcClient client : clients) {
            try {
                List<ClientInstance> result = client.listClientsByGroup(group, pageNum, pageSize);
                for (ClientInstance ci : result) {
                    merged.putIfAbsent(ci.getClientId(), ci);
                }
            } catch (Exception e) {
                log.warn("ListClientsByGroup failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }

        return new ArrayList<>(merged.values());
    }

    /**
     * List clients by topic from ALL proxies, merge + dedup.
     */
    public List<ClientInstance> listClientsByTopic(String topic, int pageNum, int pageSize) {
        Map<String, ClientInstance> merged = new LinkedHashMap<>();

        for (ProxyAdminGrpcClient client : clients) {
            try {
                List<ClientInstance> result = client.listClientsByTopic(topic, pageNum, pageSize);
                for (ClientInstance ci : result) {
                    merged.putIfAbsent(ci.getClientId(), ci);
                }
            } catch (Exception e) {
                log.warn("ListClientsByTopic failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }

        return new ArrayList<>(merged.values());
    }

    // ==================== M2: Config & Connection Management ====================

    /**
     * Get runtime config from ALL proxies. Returns map of proxyAddress → config.
     */
    public Map<String, ProxyRuntimeConfig> getConfigFromAll() {
        Map<String, ProxyRuntimeConfig> configMap = new LinkedHashMap<>();

        for (int i = 0; i < clients.size(); i++) {
            ProxyAdminGrpcClient client = clients.get(i);
            try {
                ProxyRuntimeConfig config = client.getConfig();
                if (config != null) {
                    configMap.put(proxyAddresses[i], config);
                }
            } catch (Exception e) {
                log.warn("GetConfig failed on proxy {}: {}", proxyAddresses[i], e.getMessage());
            }
        }

        return configMap;
    }

    /**
     * Get runtime config from the first available proxy.
     */
    public ProxyRuntimeConfig getConfig() {
        for (ProxyAdminGrpcClient client : clients) {
            try {
                ProxyRuntimeConfig config = client.getConfig();
                if (config != null) {
                    return config;
                }
            } catch (Exception e) {
                // try next
            }
        }
        return null;
    }

    /**
     * Broadcast config update to ALL proxies.
     *
     * @return map of proxyAddress → update response (null if failed)
     */
    public Map<String, UpdateConfigResponse> updateConfigAll(ProxyRuntimeConfig config) {
        Map<String, UpdateConfigResponse> results = new LinkedHashMap<>();

        for (int i = 0; i < clients.size(); i++) {
            ProxyAdminGrpcClient client = clients.get(i);
            try {
                UpdateConfigResponse response = client.updateConfig(config);
                results.put(proxyAddresses[i], response);
            } catch (Exception e) {
                log.warn("UpdateConfig failed on proxy {}: {}", proxyAddresses[i], e.getMessage());
                results.put(proxyAddresses[i], null);
            }
        }

        return results;
    }

    /**
     * Disconnect client from ALL proxies (client may be on any proxy).
     *
     * @return true if disconnected on at least one proxy
     */
    public boolean disconnectClient(String clientId, String reason) {
        boolean disconnected = false;

        for (ProxyAdminGrpcClient client : clients) {
            try {
                if (client.disconnectClient(clientId, reason)) {
                    disconnected = true;
                    log.info("Client {} disconnected from proxy {}:{}",
                        clientId, client.getProxyHost(), client.getProxyAdminPort());
                }
            } catch (Exception e) {
                log.warn("DisconnectClient failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }

        return disconnected;
    }

    // ==================== M3: POP Diagnostics (Aggregated) ====================

    /**
     * Query POP receipt handles from ALL proxies, merge results.
     */
    public List<DescribePopReceiptHandlesResponse> describePopReceiptHandlesFromAll(
            String group, String topic, int pageNum, int pageSize) {
        List<DescribePopReceiptHandlesResponse> responses = new ArrayList<>();

        for (ProxyAdminGrpcClient client : clients) {
            try {
                DescribePopReceiptHandlesResponse response =
                    client.describePopReceiptHandles(group, topic, pageNum, pageSize);
                if (response != null) {
                    responses.add(response);
                }
            } catch (Exception e) {
                log.warn("DescribePopReceiptHandles failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }

        return responses;
    }

    // ==================== M4: Batch Consumption Diagnostics (Aggregated) ====================

    /**
     * Query batch consumption diagnostics from ALL proxies, merge results.
     */
    public List<DescribeBatchConsumeDiagnosticsResponse> describeBatchConsumeDiagnosticsFromAll(
            String group, String topic, String clientId, int pageNum, int pageSize) {
        List<DescribeBatchConsumeDiagnosticsResponse> responses = new ArrayList<>();

        for (ProxyAdminGrpcClient client : clients) {
            try {
                DescribeBatchConsumeDiagnosticsResponse response =
                    client.describeBatchConsumeDiagnostics(group, topic, clientId, pageNum, pageSize);
                if (response != null) {
                    responses.add(response);
                }
            } catch (Exception e) {
                log.warn("DescribeBatchConsumeDiagnostics failed on proxy {}:{}: {}",
                    client.getProxyHost(), client.getProxyAdminPort(), e.getMessage());
            }
        }

        return responses;
    }

    // ==================== Route Events Streaming ====================

    /**
     * Subscribe to route change events from ALL proxies.
     * Events from all proxies are merged and forwarded to the callback.
     *
     * @param topics     optional topic filter (empty = all)
     * @param eventTypes optional event type filter (empty = all)
     * @param callback   callback invoked for each route change event
     */
    public void subscribeRouteEvents(List<String> topics,
                                      List<RouteChangeEventType> eventTypes,
                                      Consumer<SubscribeRouteEventsResponse> callback) {
        SubscribeRouteEventsRequest.Builder builder = SubscribeRouteEventsRequest.newBuilder();
        if (topics != null && !topics.isEmpty()) {
            builder.addAllTopics(topics);
        }
        if (eventTypes != null && !eventTypes.isEmpty()) {
            builder.addAllEventTypes(eventTypes);
        }
        SubscribeRouteEventsRequest request = builder.build();

        for (ProxyAdminGrpcClient client : clients) {
            ManagedChannel channel = client.getChannel();
            if (channel == null || channel.isShutdown()) {
                continue;
            }

            ProxyClientAdminServiceGrpc.ProxyClientAdminServiceStub asyncStub =
                ProxyClientAdminServiceGrpc.newStub(channel);

            asyncStub.subscribeRouteEvents(request, new StreamObserver<SubscribeRouteEventsResponse>() {
                @Override
                public void onNext(SubscribeRouteEventsResponse response) {
                    callback.accept(response);
                }

                @Override
                public void onError(Throwable t) {
                    log.warn("RouteEvents stream error from proxy {}:{}: {}",
                        client.getProxyHost(), client.getProxyAdminPort(), t.getMessage());
                }

                @Override
                public void onCompleted() {
                    log.info("RouteEvents stream completed from proxy {}:{}",
                        client.getProxyHost(), client.getProxyAdminPort());
                }
            });
        }
    }

    // ==================== Lifecycle & Status ====================

    /**
     * Check if any proxy client is available.
     */
    public boolean isAvailable() {
        for (ProxyAdminGrpcClient client : clients) {
            if (client.isAvailable()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Get the number of available proxy connections.
     */
    public int getAvailableCount() {
        int count = 0;
        for (ProxyAdminGrpcClient client : clients) {
            if (client.isAvailable()) {
                count++;
            }
        }
        return count;
    }

    /**
     * Get total number of configured proxy endpoints.
     */
    public int getTotalCount() {
        return clients.size();
    }

    /**
     * Get the list of proxy addresses.
     */
    public String[] getProxyAddresses() {
        return proxyAddresses;
    }

    /**
     * Get underlying individual clients (for advanced usage).
     */
    public List<ProxyAdminGrpcClient> getClients() {
        return Collections.unmodifiableList(clients);
    }

    /**
     * Shutdown all gRPC channels.
     */
    public void shutdown() {
        log.info("Shutting down MultiProxyAdminClient ({} clients)", clients.size());
        for (ProxyAdminGrpcClient client : clients) {
            try {
                client.shutdown();
            } catch (Exception e) {
                log.warn("Error shutting down proxy client: {}", e.getMessage());
            }
        }
        fanOutExecutor.shutdown();
        try {
            if (!fanOutExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                fanOutExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            fanOutExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("MultiProxyAdminClient shutdown complete");
    }
}
