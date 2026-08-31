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

package org.apache.rocketmq.studio.provider.apache;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.remoting.netty.NettyClientConfig;
import org.apache.rocketmq.remoting.netty.NettyRemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RequestCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.header.GetConsumerConnectionListRequestHeader;
import org.apache.rocketmq.remoting.protocol.header.GetConsumerRunningInfoRequestHeader;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Resolves consumer connection info from the cluster proxies.
 *
 * <p>Clients connected through a proxy (remoting heartbeats stop at the proxy, gRPC clients
 * register via gRPC heartbeats) are invisible to broker-side consumer stats, so the broker
 * reports those groups as offline. Every proxy registers its remoting address through the
 * broadcast heartbeat-syncer group on the brokers, which lets us discover the proxy nodes
 * indirectly and query {@code GET_CONSUMER_CONNECTION_LIST} on the proxy's remoting port,
 * where the proxy answers from its own client manager.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProxyConsumerResolver {

    private static final String HEARTBEAT_SYNCER_CONSUMER_GROUP = "CID_DefaultHeartBeatSyncerTopic";
    private static final int PROXY_REMOTING_PORT = 8080;
    private static final long PROXY_QUERY_TIMEOUT_MILLIS = 2_000L;
    // Capturing a jstack on the client takes far longer than reading the proxy's own client
    // manager, so the running info query gets its own, more generous budget.
    private static final long PROXY_RUNNING_INFO_TIMEOUT_MILLIS = 10_000L;
    static final long PROXY_ADDRESS_CACHE_TTL_MILLIS = 60_000L;
    static final int MAX_PROXY_ADDRESS_CACHE_ENTRIES = 128;
    private static final String DEFAULT_INSTANCE_KEY = "__default__";

    private final MqAdminExtFactory adminFactory;
    private final RuntimeAdminClientResolver runtimeAdminClientResolver;
    private final RocketMQProperties properties;

    private final Map<String, CachedProxyAddresses> proxyAddressCache = new ConcurrentHashMap<>();
    private final AtomicBoolean clientStarted = new AtomicBoolean(false);
    private volatile NettyRemotingClient remotingClient;
    private long proxyAddressCacheTtlMillis = PROXY_ADDRESS_CACHE_TTL_MILLIS;
    private int maxProxyAddressCacheEntries = MAX_PROXY_ADDRESS_CACHE_ENTRIES;

    /**
     * Queries the proxies of the given instance for the consumer connection info of the group.
     * Returns {@code null} when no proxy knows the group (offline everywhere) or no proxy is
     * reachable.
     */
    public ConsumerConnection resolveConsumerConnection(String instanceId, String group) {
        for (String addr : discoverProxyAddresses(instanceId)) {
            try {
                ConsumerConnection connection = queryProxy(addr, group);
                if (connection != null) {
                    return connection;
                }
            } catch (Exception e) {
                log.debug("Proxy consumer connection query failed for {} via {}: {}",
                        group, addr, e.getMessage());
            }
        }
        return null;
    }

    ConsumerConnection queryProxy(String proxyAddr, String group) throws Exception {
        GetConsumerConnectionListRequestHeader header = new GetConsumerConnectionListRequestHeader();
        header.setConsumerGroup(group);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_CONNECTION_LIST, header);
        RemotingCommand response = remotingClient().invokeSync(proxyAddr, request, PROXY_QUERY_TIMEOUT_MILLIS);
        if (response == null || response.getCode() != ResponseCode.SUCCESS || response.getBody() == null) {
            return null;
        }
        return ConsumerConnection.decode(response.getBody(), ConsumerConnection.class);
    }

    /**
     * Asks the proxies for the running info (including the client jstack) of one consumer client.
     * Proxy-connected clients keep their channel on the proxy and never register on a broker, so
     * the proxy is the only component able to reach them. Returns {@code null} when no proxy can
     * answer, letting the caller fall back to the broker for directly connected clients.
     */
    public ConsumerRunningInfo resolveConsumerRunningInfo(String instanceId, String group, String clientId) {
        for (String addr : discoverProxyAddresses(instanceId)) {
            try {
                ConsumerRunningInfo runningInfo = queryProxyRunningInfo(addr, group, clientId);
                if (runningInfo != null) {
                    return runningInfo;
                }
            } catch (Exception e) {
                log.debug("Proxy consumer running info query failed for {}/{} via {}: {}",
                        group, clientId, addr, e.getMessage());
            }
        }
        return null;
    }

    ConsumerRunningInfo queryProxyRunningInfo(String proxyAddr, String group, String clientId) throws Exception {
        GetConsumerRunningInfoRequestHeader header = new GetConsumerRunningInfoRequestHeader();
        header.setConsumerGroup(group);
        header.setClientId(clientId);
        header.setJstackEnable(true);
        RemotingCommand request =
                RemotingCommand.createRequestCommand(RequestCode.GET_CONSUMER_RUNNING_INFO, header);
        RemotingCommand response =
                remotingClient().invokeSync(proxyAddr, request, PROXY_RUNNING_INFO_TIMEOUT_MILLIS);
        if (response == null) {
            return null;
        }
        if (response.getCode() != ResponseCode.SUCCESS || response.getBody() == null) {
            log.info("Proxy {} cannot report running info for {}/{}: code={} remark={}",
                    proxyAddr, group, clientId, response.getCode(), response.getRemark());
            return null;
        }
        return ConsumerRunningInfo.decode(response.getBody(), ConsumerRunningInfo.class);
    }

    List<String> discoverProxyAddresses(String instanceId) {
        String cacheKey = StringUtils.hasText(instanceId) ? instanceId : DEFAULT_INSTANCE_KEY;
        CachedProxyAddresses cached = proxyAddressCache.get(cacheKey);
        if (cached != null && cached.expiresAtMillis() > System.currentTimeMillis()) {
            return cached.addresses();
        }
        Set<String> ips = new LinkedHashSet<>();
        try {
            executeAdmin(instanceId, admin -> {
                ConsumerConnection connection =
                        admin.examineConsumerConnectionInfo(HEARTBEAT_SYNCER_CONSUMER_GROUP);
                if (connection != null && connection.getConnectionSet() != null) {
                    for (Connection conn : connection.getConnectionSet()) {
                        String clientAddr = conn.getClientAddr();
                        if (clientAddr == null || clientAddr.isBlank()) {
                            continue;
                        }
                        int separator = clientAddr.lastIndexOf(':');
                        ips.add(separator > 0 ? clientAddr.substring(0, separator) : clientAddr);
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.debug("Proxy discovery via heartbeat syncer failed for instance {}: {}",
                    instanceId, e.getMessage());
            // A failed lookup is transient and must not suppress discovery for the full cache TTL.
            return List.of();
        }
        List<String> addresses = ips.stream()
                .map(ip -> ip + ":" + PROXY_REMOTING_PORT)
                .toList();
        proxyAddressCache.put(cacheKey,
                new CachedProxyAddresses(addresses, System.currentTimeMillis() + proxyAddressCacheTtlMillis));
        evictStaleProxyAddresses();
        return addresses;
    }

    /**
     * Removes expired cache entries and, when the cache exceeds its bound, the entries with the
     * earliest expiry. The cache is keyed by the caller-supplied instance id and entries are only
     * revalidated lazily on read, so without active eviction every instance id ever queried would
     * leave a permanently stale entry behind.
     */
    private void evictStaleProxyAddresses() {
        long now = System.currentTimeMillis();
        proxyAddressCache.entrySet().removeIf(entry -> entry.getValue().expiresAtMillis() <= now);
        while (proxyAddressCache.size() > maxProxyAddressCacheEntries) {
            proxyAddressCache.entrySet().stream()
                    .min(Comparator.comparingLong(entry -> entry.getValue().expiresAtMillis()))
                    .ifPresent(oldest -> proxyAddressCache.remove(oldest.getKey(), oldest.getValue()));
        }
    }

    int proxyAddressCacheSize() {
        return proxyAddressCache.size();
    }

    void setCacheLimitsForTest(long ttlMillis, int maxEntries) {
        this.proxyAddressCacheTtlMillis = ttlMillis;
        this.maxProxyAddressCacheEntries = maxEntries;
    }

    private <T> T executeAdmin(String instanceId, MqAdminExtFactory.AdminAction<T> action) {
        if (StringUtils.hasText(instanceId)) {
            return runtimeAdminClientResolver.execute(instanceId, action);
        }
        return adminFactory.execute(properties.getNamesrvAddr(), null, action);
    }

    private NettyRemotingClient remotingClient() {
        NettyRemotingClient client = remotingClient;
        if (client == null) {
            synchronized (this) {
                if (remotingClient == null) {
                    remotingClient = new NettyRemotingClient(new NettyClientConfig());
                }
                client = remotingClient;
            }
        }
        if (clientStarted.compareAndSet(false, true)) {
            client.start();
        }
        return client;
    }

    @PreDestroy
    public void shutdownRemotingClient() {
        if (clientStarted.get() && remotingClient != null) {
            remotingClient.shutdown();
        }
    }

    void setRemotingClientForTest(NettyRemotingClient client) {
        this.remotingClient = client;
        clientStarted.set(true);
    }

    private record CachedProxyAddresses(List<String> addresses, long expiresAtMillis) {
    }
}
