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
package org.apache.rocketmq.dashboard.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.service.UnifiedClientService;
import org.apache.rocketmq.dashboard.service.client.GrpcClientCollector;
import org.apache.rocketmq.dashboard.service.client.RemotingClientCollector;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service implementation that aggregates client data from both Remoting and gRPC channels.
 * Implements RIP-1 CLIENT-01 dual-protocol unified view.
 *
 * Key behaviors:
 * - listAllClients: fetches from both collectors, merges, deduplicates by clientId
 *   (Remoting data takes precedence as it has richer fields)
 * - describeClient: queries Remoting first, falls back to gRPC
 * - getClientSubscriptions: resolves subscription info from the best-available channel
 * - getAvailableChannels: reports which channels are live based on GrpcClientCollector.hasDataAvailable()
 */
@Slf4j
@Service
public class UnifiedClientServiceImpl implements UnifiedClientService {

    private static final String CHANNEL_REMOTING = "REMOTING";
    private static final String CHANNEL_GRPC = "GRPC";

    @Resource
    private RemotingClientCollector remotingClientCollector;

    @Resource
    private GrpcClientCollector grpcClientCollector;

    /**
     * Get unified client list from all available channels with deduplication.
     *
     * Deduplication rules:
     * - Same clientId appearing in both channels → keep Remoting data (more complete fields)
     * - If only gRPC has the client → include it with REMOTING-fallback defaults
     * - Topic/group filters are applied after merging
     */
    @Override
    public List<ClientInstance> listAllClients(Optional<String> topic, Optional<String> group) {
        List<ClientInstance> allClients = new ArrayList<>();

        try {
            // Fetch from Remoting channel (primary source, always available)
            List<ClientInstance> remotingClients = remotingClientCollector.listClientInstances(topic, group);
            allClients.addAll(remotingClients);

            // Fetch from gRPC channel (fallback, placeholder until RIP-2)
            List<ClientInstance> grpcClients = grpcClientCollector.listClientInstances(topic, group);
            if (!grpcClients.isEmpty()) {
                allClients = mergeAndDedup(allClients, grpcClients);
            }

            log.info("[UNIFIED-CLIENT] Total clients after merge: {} (remoting={}, grpc={})",
                allClients.size(),
                remotingClients.size(),
                grpcClients.size());

        } catch (Exception e) {
            log.error("[UNIFIED-CLIENT] Failed to aggregate client instances", e);
        }

        return allClients;
    }

    /**
     * Get detailed information for a specific client instance.
     * Queries Remoting channel first, falls back to gRPC channel if not found there.
     */
    @Override
    public Optional<ClientInstance> describeClient(String clientId) {
        if (clientId == null || clientId.trim().isEmpty()) {
            log.warn("[UNIFIED-CLIENT] DescribeClient called with empty clientId");
            return Optional.empty();
        }

        // Try Remoting channel first (richer data)
        List<ClientInstance> remotingClients = remotingClientCollector.listClientInstances(Optional.empty(), Optional.empty());
        Optional<ClientInstance> remotingMatch = remotingClients.stream()
            .filter(c -> clientId.equals(c.getClientId()))
            .findFirst();

        if (remotingMatch.isPresent()) {
            log.debug("[UNIFIED-CLIENT] Client {} found via Remoting channel", clientId);
            return remotingMatch;
        }

        // Fall back to gRPC channel
        Optional<ClientInstance> grpcMatch = grpcClientCollector.getClientInstance(clientId);
        if (grpcMatch.isPresent()) {
            log.debug("[UNIFIED-CLIENT] Client {} found via gRPC channel", clientId);
            return grpcMatch;
        }

        log.debug("[UNIFIED-CLIENT] Client {} not found in any channel", clientId);
        return Optional.empty();
    }

    /**
     * Get subscription info for a specific client.
     * Resolves from the best available channel — Remoting first, then gRPC.
     */
    @Override
    public List<SubscriptionInfo> getClientSubscriptions(String clientId) {
        // Try Remoting channel first
        List<ClientInstance> remotingClients = remotingClientCollector.listClientInstances(Optional.empty(), Optional.empty());
        Optional<ClientInstance> client = remotingClients.stream()
            .filter(c -> clientId.equals(c.getClientId()))
            .findFirst();

        if (client.isPresent() && CollectionUtils.isNotEmpty(client.get().getSubscriptions())) {
            log.debug("[UNIFIED-CLIENT] Subscriptions for {} resolved from Remoting", clientId);
            return client.get().getSubscriptions();
        }

        // Fall back to gRPC channel
        List<SubscriptionInfo> grpcSubscriptions = grpcClientCollector.getClientSubscriptions(clientId);
        if (!grpcSubscriptions.isEmpty()) {
            log.debug("[UNIFIED-CLIENT] Subscriptions for {} resolved from gRPC", clientId);
            return grpcSubscriptions;
        }

        log.debug("[UNIFIED-CLIENT] No subscriptions found for {}", clientId);
        return Collections.emptyList();
    }

    /**
     * Check which channels are currently available for client collection.
     * Returns ["REMOTING"] always, plus "GRPC" when GrpcClientCollector.hasDataAvailable() is true.
     */
    @Override
    public List<String> getAvailableChannels() {
        List<String> channels = new ArrayList<>(Collections.singletonList(CHANNEL_REMOTING));

        if (grpcClientCollector != null && grpcClientCollector.hasDataAvailable()) {
            channels.add(CHANNEL_GRPC);
        }

        return channels;
    }

    /**
     * Merge two client lists and deduplicate by clientId.
     *
     * Dedup strategy: Remoting data takes priority (more complete fields). When the same clientId
     * exists in both lists, prefer the Remoting entry but enrich gRPC-only fields from the gRPC entry.
     *
     * @param primary   primary list (from Remoting channel)
     * @param secondary secondary list (from gRPC channel)
     * @return merged and deduplicated list
     */
    private List<ClientInstance> mergeAndDedup(List<ClientInstance> primary, List<ClientInstance> secondary) {
        Map<String, ClientInstance> mergedByClientId = new LinkedHashMap<>();

        // Insert primary entries first
        for (ClientInstance client : primary) {
            mergedByClientId.put(client.getClientId(), client);
        }

        // Merge secondary: only add new entries or enrich fields absent in primary
        for (ClientInstance grpcClient : secondary) {
            String clientId = grpcClient.getClientId();
            ClientInstance existing = mergedByClientId.get(clientId);

            if (existing == null) {
                // New client only in gRPC channel
                mergedByClientId.put(clientId, grpcClient);
            } else {
                // Existing in Remoting — enrich with gRPC-specific fields
                enrichWithGrpcFields(existing, grpcClient);
            }
        }

        return new ArrayList<>(mergedByClientId.values());
    }

    /**
     * Enrich an existing Remoting-based ClientInstance with gRPC-specific fields
     * that may be more complete or only available from the gRPC channel.
     *
     * Fields enriched from gRPC:
     * - endpoint: gRPC-connected clients often have a clear proxy endpoint
     * - settingsVersion: telemetry version tracking unique to gRPC protocol
     * - popEnabled: POP consume mode flag from gRPC telemetry
     * - longConnectionActive: long connection liveness from gRPC
     * - consumerProgress: consumption progress data richer in gRPC responses
     */
    private void enrichWithGrpcFields(ClientInstance remotingClient, ClientInstance grpcClient) {
        if (grpcClient.getEndpoint() != null && remotingClient.getEndpoint() == null) {
            remotingClient.setEndpoint(grpcClient.getEndpoint());
        }
        if (grpcClient.getSettingsVersion() != null && remotingClient.getSettingsVersion() == null) {
            remotingClient.setSettingsVersion(grpcClient.getSettingsVersion());
        }
        if (grpcClient.getPopEnabled() != null && remotingClient.getPopEnabled() == null) {
            remotingClient.setPopEnabled(grpcClient.getPopEnabled());
        }
        if (grpcClient.getLongConnectionActive() != null && remotingClient.getLongConnectionActive() == null) {
            remotingClient.setLongConnectionActive(grpcClient.getLongConnectionActive());
        }
        if (grpcClient.getConsumerProgress() != null && remotingClient.getConsumerProgress() == null) {
            remotingClient.setConsumerProgress(grpcClient.getConsumerProgress());
        }
        if (grpcClient.getLastHeartbeatTime() != null) {
            remotingClient.setLastHeartbeatTime(grpcClient.getLastHeartbeatTime());
        }
    }
}
