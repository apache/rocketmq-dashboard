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

import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;

import java.util.List;
import java.util.Optional;

/**
 * LiteTopic management service interface.
 *
 * <p>LiteTopic is a RocketMQ 5.0 feature that provides lightweight, auto-expiring
 * topics with TTL management. This service provides CRUD operations and
 * quota management for LiteTopic sessions.</p>
 *
 * <h3>Architecture Compatibility</h3>
 * <ul>
 *   <li><b>V5 Proxy:</b> Full support for LiteTopic listing, session management,
 *       TTL extension, and quota queries (requires RIP-2 gRPC interface)</li>
 *   <li><b>V4 Direct:</b> Not supported — all operations throw UnsupportedOperationException</li>
 * </ul>
 *
 * <h3>API Overview</h3>
 * <table border="1">
 *   <tr><th>Method</th><th>Description</th><th>V4</th><th>V5</th></tr>
 *   <tr><td>listLiteTopics</td><td>List LiteTopics with optional pattern filter</td><td>Empty</td><td>Yes</td></tr>
 *   <tr><td>getLiteTopicSession</td><td>Get session details by ID</td><td>No</td><td>Yes</td></tr>
 *   <tr><td>extendLiteTopicTTL</td><td>Extend TTL for a topic pattern</td><td>No</td><td>Yes</td></tr>
 *   <tr><td>getLiteTopicQuota</td><td>Get quota usage for namespace</td><td>No</td><td>Yes</td></tr>
 *   <tr><td>isLiteTopicSupported</td><td>Check if LiteTopic is supported</td><td>false</td><td>true</td></tr>
 * </table>
 */
public interface LiteTopicService {

    /**
     * List LiteTopics with optional pattern filter and namespace scope.
     *
     * @param pattern   topic pattern filter (may be null for all topics)
     * @param namespace optional namespace scope
     * @return list of LiteTopic summaries
     * @throws UnsupportedOperationException if not supported in current architecture
     */
    List<LiteTopicSummary> listLiteTopics(String pattern, Optional<String> namespace) throws Exception;

    /**
     * Get a specific LiteTopic session by its session ID.
     *
     * @param sessionId the session identifier
     * @return LiteTopic session details
     * @throws UnsupportedOperationException if not supported in current architecture
     */
    LiteTopicSession getLiteTopicSession(String sessionId) throws Exception;

    /**
     * Extend the TTL of LiteTopics matching the given pattern.
     *
     * @param topicPattern the topic pattern to extend
     * @param newTTL       the new TTL in milliseconds
     * @throws UnsupportedOperationException if not supported in current architecture
     */
    void extendLiteTopicTTL(String topicPattern, long newTTL) throws Exception;

    /**
     * Get LiteTopic quota information for a namespace.
     *
     * @param namespace optional namespace scope
     * @return LiteTopic quota information
     * @throws UnsupportedOperationException if not supported in current architecture
     */
    LiteTopicQuota getLiteTopicQuota(Optional<String> namespace) throws Exception;

    /**
     * Check if LiteTopic management is supported in the current cluster architecture.
     *
     * @return true if LiteTopic is supported, false otherwise
     */
    boolean isLiteTopicSupported();
}