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
package org.apache.rocketmq.dashboard.service;

import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;

import java.util.List;
import java.util.Optional;

/**
 * Unified client service that aggregates data from both Remoting and gRPC channels.
 * This is part of RIP-1 CLIENT-01 dual-protocol client unified view.
 *
 * Provides a single consistent API for listing, describing, and querying subscription
 * information across all client protocols, handling deduplication when the same client
 * appears on multiple channels.
 */
public interface UnifiedClientService {

    /**
     * Get unified client list from all available channels.
     * Deduplication is performed by clientId.
     *
     * @param topic optional topic filter — only clients subscribed to or publishing this topic are returned
     * @param group optional consumer group filter — only clients belonging to this group are returned
     * @return combined and deduplicated list of ClientInstance
     */
    List<ClientInstance> listAllClients(Optional<String> topic, Optional<String> group);

    /**
     * Get detailed information for a specific client instance.
     * Queries Remoting channel first; falls back to gRPC channel if not found.
     *
     * @param clientId the unique identifier of the client
     * @return Optional containing the ClientInstance if found, empty otherwise
     */
    Optional<ClientInstance> describeClient(String clientId);

    /**
     * Get subscription info for a specific client.
     * Resolves subscriptions from the best-available channel (Remoting first, then gRPC).
     *
     * @param clientId the unique identifier of the client
     * @return list of SubscriptionInfo entries for this client
     */
    List<SubscriptionInfo> getClientSubscriptions(String clientId);

    /**
     * Check which channels are currently available for client collection.
     * Useful for frontend to conditionally show/hide protocol indicators.
     *
     * @return list of available channel names, e.g. ["REMOTING"] or ["REMOTING", "GRPC"]
     */
    List<String> getAvailableChannels();
}
