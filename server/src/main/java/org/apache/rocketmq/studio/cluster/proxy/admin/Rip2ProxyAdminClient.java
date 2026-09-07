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
package org.apache.rocketmq.studio.cluster.proxy.admin;

import apache.rocketmq.v2.AdminGrpc;
import apache.rocketmq.v2.ClientInfo;
import apache.rocketmq.v2.Code;
import apache.rocketmq.v2.ListConsumerConnectionRequest;
import apache.rocketmq.v2.ListConsumerConnectionResponse;
import apache.rocketmq.v2.ListSubscriptionRequest;
import apache.rocketmq.v2.ListSubscriptionResponse;
import apache.rocketmq.v2.Resource;
import apache.rocketmq.v2.SubscriptionInfo;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.grpc.StatusRuntimeException;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * RIP-2 admin client: talks to the proxy's dedicated admin gRPC server
 * (upstream {@code Admin} service from rocketmq-apis 2.2.0, default port 8083
 * on {@code feat/rip2-proxy-admin}-style proxies).
 *
 * <p>gRPC SDK clients do NOT register with the broker's remoting admin, so the
 * remoting-based client view cannot see them. This client surfaces them through
 * {@code listConsumerConnection} / {@code listSubscription} served by the proxy
 * itself. Every call degrades safely: when no admin endpoint answers, results are
 * simply empty and the remoting view stays authoritative.
 */
@Slf4j
@Component
public class Rip2ProxyAdminClient {

    private static final long DEADLINE_MILLIS = 3_000L;

    private final String configuredAddresses;
    private final int adminPort;
    private final ObjectProvider<org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService> proxyAddressService;
    private final Map<String, ManagedChannel> channels = new ConcurrentHashMap<>();

    public Rip2ProxyAdminClient(
            @Value("${studio.rocketmq.proxy-admin-addresses:}") String configuredAddresses,
            @Value("${studio.rocketmq.proxy-admin-port:8083}") int adminPort,
            ObjectProvider<org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService> proxyAddressService) {
        this.configuredAddresses = configuredAddresses == null ? "" : configuredAddresses.trim();
        this.adminPort = adminPort;
        this.proxyAddressService = proxyAddressService;
    }

    /** Candidate admin endpoints: configured list first, then derived from known proxy hosts. */
    public List<String> candidateAddresses() {
        Set<String> result = new LinkedHashSet<>();
        if (!configuredAddresses.isEmpty()) {
            for (String addr : configuredAddresses.split(",")) {
                if (!addr.isBlank()) {
                    result.add(addr.trim());
                }
            }
        }
        org.apache.rocketmq.studio.cluster.proxy.ProxyAddressService pas = proxyAddressService.getIfAvailable();
        if (pas != null) {
            for (String proxyAddr : pas.knownProxyAddresses()) {
                int idx = proxyAddr.lastIndexOf(':');
                if (idx > 0) {
                    result.add(proxyAddr.substring(0, idx) + ":" + adminPort);
                }
            }
        }
        return new ArrayList<>(result);
    }

    /**
     * Fetch every gRPC consumer visible through the RIP-2 admin surface, mapped to its
     * subscription group. Returns a list of {@link GrpcConsumer} records; empty when no
     * admin endpoint answers.
     */
    public List<GrpcConsumer> listGrpcConsumers() {
        List<GrpcConsumer> result = new ArrayList<>();
        for (String addr : candidateAddresses()) {
            try {
                result.addAll(listGrpcConsumersAt(addr));
            } catch (Exception e) {
                log.info("RIP-2 admin endpoint {} unavailable: {}", addr, e.getMessage());
            }
        }
        return result;
    }

    private List<GrpcConsumer> listGrpcConsumersAt(String addr) throws StatusRuntimeException {
        AdminGrpc.AdminBlockingStub stub = stub(addr);
        List<SubscriptionInfo> subs = listSubscriptions(stub);
        Set<String> groups = new LinkedHashSet<>();
        for (SubscriptionInfo sub : subs) {
            if (!sub.getGroup().getName().isBlank()) {
                groups.add(sub.getGroup().getName());
            }
        }
        List<GrpcConsumer> consumers = new ArrayList<>();
        for (String group : groups) {
            ListConsumerConnectionResponse resp = stub.withDeadlineAfter(DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
                .listConsumerConnection(ListConsumerConnectionRequest.newBuilder()
                    .setGroup(Resource.newBuilder().setName(group).build())
                    .build());
            if (resp.getStatus().getCode() != Code.OK) {
                continue;
            }
            for (ClientInfo ci : resp.getClientInfoList()) {
                consumers.add(new GrpcConsumer(addr, group, ci.getClientId(), ci.getHostname(),
                    ci.getLanguage(), ci.getVersion(), ci.getEgressIp()));
            }
        }
        return consumers;
    }

    private List<SubscriptionInfo> listSubscriptions(AdminGrpc.AdminBlockingStub stub) {
        try {
            ListSubscriptionResponse resp = stub.withDeadlineAfter(DEADLINE_MILLIS, TimeUnit.MILLISECONDS)
                .listSubscription(ListSubscriptionRequest.newBuilder()
                    .setGroup(Resource.getDefaultInstance())
                    .setTopic(Resource.getDefaultInstance())
                    .build());
            if (resp.getStatus().getCode() == Code.OK) {
                return resp.getSubscriptionInfoList();
            }
        } catch (StatusRuntimeException e) {
            log.debug("listSubscription via RIP-2 admin failed: {}", e.getMessage());
        }
        return List.of();
    }

    private AdminGrpc.AdminBlockingStub stub(String addr) {
        ManagedChannel channel = channels.computeIfAbsent(addr, a -> {
            log.info("Opening RIP-2 admin channel to {}", a);
            return ManagedChannelBuilder.forTarget(a).usePlaintext().build();
        });
        return AdminGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    public void shutdown() {
        channels.values().forEach(ManagedChannel::shutdownNow);
        channels.clear();
    }

    /** One gRPC consumer client as reported by the proxy. */
    public record GrpcConsumer(String adminAddr, String group, String clientId, String hostname,
                               String language, String version, String egressIp) {
    }
}
