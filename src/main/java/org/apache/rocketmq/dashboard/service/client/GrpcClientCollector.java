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

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * gRPC channel client collector for RIP-1 CLIENT-01 dual-protocol unified view.
 *
 * This collector gathers client instance data from the gRPC proxy layer via Proxy Admin APIs.
 * Currently implements placeholder logic returning empty results, pending RIP-2 Proxy Admin
 * interface integration (ListClients / DescribeClient RPC calls).
 *
 * After RIP-2 merge: replace placeholders with actual Proxy ClientManager ListClients /
 * DescribeClient calls against the configured proxy address.
 */
@Slf4j
@Service
public class GrpcClientCollector {

    @Value("${rocketmq.dashboard.proxyAddress:localhost:8080}")
    private String proxyAddress;

    /**
     * List online client instances via gRPC proxy.
     *
     * RIP-2 placeholder: returns empty list. After RIP-2 Proxy Admin integration, this will
     * call Proxy ClientManager's ListClients RPC to fetch gRPC-connected clients.
     *
     * @param topic optional topic filter
     * @param group optional consumer group filter
     * @return list of ClientInstance found via gRPC channel (empty until RIP-2 is merged)
     */
    public List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) {
        log.debug("[GRPC-CLIENT] No RIP-2 admin interface available, returning empty client list for proxy {}", proxyAddress);
        return Collections.emptyList();
    }

    /**
     * Query detailed information for a specific client instance via gRPC proxy.
     *
     * RIP-2 placeholder: returns Optional.empty(). After RIP-2 Proxy Admin integration, this will
     * call Proxy ClientManager's DescribeClient RPC with the given clientId.
     *
     * @param clientId the unique identifier of the client
     * @return Optional containing the ClientInstance if found, empty otherwise
     */
    public Optional<ClientInstance> getClientInstance(String clientId) {
        log.debug("[GRPC-CLIENT] No RIP-2 admin interface available for clientId {}", clientId);
        return Optional.empty();
    }

    /**
     * Retrieve subscription info for a specific client via gRPC proxy.
     *
     * RIP-2 placeholder: returns empty list. After RIP-2 Proxy Admin integration, this will
     * extract Subscription settings from the client's telemetry data.
     *
     * @param clientId the unique identifier of the client
     * @return list of subscription info entries (empty until RIP-2 is merged)
     */
    public List<SubscriptionInfo> getClientSubscriptions(String clientId) {
        log.debug("[GRPC-CLIENT] No RIP-2 admin interface available for subscriptions of clientId {}", clientId);
        return Collections.emptyList();
    }

    /**
     * Check whether this collector has usable data available.
     *
     * Returns false during the placeholder phase. After RIP-2 Proxy Admin interface is integrated,
     * return true when the proxy connection is healthy and capable of serving client queries.
     * Used by the frontend to conditionally show/hide gRPC channel indicators.
     *
     * @return false always in placeholder mode
     */
    public boolean hasDataAvailable() {
        return false;
    }
}
