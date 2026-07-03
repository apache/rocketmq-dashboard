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

import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.service.ArchitectureBasedService;
import org.apache.rocketmq.dashboard.service.LiteTopicService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * LiteTopic management service implementation.
 *
 * <p>Delegates to {@link org.apache.rocketmq.dashboard.architecture.MetadataProvider}
 * for actual LiteTopic operations. In V4 architecture, LiteTopic is not supported
 * and all operations will either return empty results or throw
 * {@link UnsupportedOperationException}.</p>
 *
 * <h3>Graceful Degradation Strategy</h3>
 * <ul>
 *   <li><b>listLiteTopics:</b> V4 returns empty list (not an error)</li>
 *   <li><b>getLiteTopicSession:</b> V4 throws UnsupportedOperationException</li>
 *   <li><b>extendLiteTopicTTL:</b> V4 throws UnsupportedOperationException</li>
 *   <li><b>getLiteTopicQuota:</b> V4 throws UnsupportedOperationException</li>
 * </ul>
 *
 * <p>Note: V5 Proxy implementation currently returns UnsupportedOperationException
 * for most LiteTopic methods as they require the RIP-2 gRPC interface. Once
 * RIP-2 is merged, these methods will be fully functional.</p>
 */
@Service
public class LiteTopicServiceImpl extends ArchitectureBasedService implements LiteTopicService {

    private static final Logger log = LoggerFactory.getLogger(LiteTopicServiceImpl.class);

    /**
     * List LiteTopics with optional pattern filter and namespace scope.
     *
     * <p>In V4 architecture, returns an empty list gracefully.
     * In V5 architecture, delegates to metadataProvider.listLiteTopics().</p>
     *
     * @param pattern   topic pattern filter (may be null for all)
     * @param namespace optional namespace scope
     * @return list of LiteTopic summaries; empty list if not supported
     */
    @Override
    public List<LiteTopicSummary> listLiteTopics(String pattern, Optional<String> namespace) throws Exception {
        if (!supportsLiteTopic()) {
            log.debug("LiteTopic not supported in current architecture, returning empty list");
            return Collections.emptyList();
        }

        try {
            return metadataProvider.listLiteTopics(pattern, namespace);
        } catch (UnsupportedOperationException e) {
            log.warn("LiteTopic listing not supported by metadata provider: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Get a specific LiteTopic session by its session ID.
     *
     * <p>Requires V5 architecture with RIP-2 gRPC interface.</p>
     *
     * @param sessionId the session identifier
     * @return LiteTopic session details
     * @throws UnsupportedOperationException if not supported
     */
    @Override
    public LiteTopicSession getLiteTopicSession(String sessionId) throws Exception {
        if (!supportsLiteTopic()) {
            handleUnsupportedOperation("getLiteTopicSession");
        }

        try {
            return metadataProvider.getLiteTopicSession(sessionId);
        } catch (UnsupportedOperationException e) {
            log.error("LiteTopic session retrieval not supported: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Extend the TTL of LiteTopics matching the given pattern.
     *
     * <p>Requires V5 architecture with RIP-2 gRPC interface.</p>
     *
     * @param topicPattern the topic pattern to extend
     * @param newTTL       the new TTL in milliseconds
     * @throws UnsupportedOperationException if not supported
     */
    @Override
    public void extendLiteTopicTTL(String topicPattern, long newTTL) throws Exception {
        if (!supportsLiteTopic()) {
            handleUnsupportedOperation("extendLiteTopicTTL");
        }

        if (topicPattern == null || topicPattern.trim().isEmpty()) {
            throw new IllegalArgumentException("Topic pattern cannot be empty");
        }
        if (newTTL <= 0) {
            throw new IllegalArgumentException("New TTL must be positive, got: " + newTTL);
        }

        try {
            metadataProvider.extendLiteTopicTTL(topicPattern, newTTL);
        } catch (UnsupportedOperationException e) {
            log.error("LiteTopic TTL extension not supported: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Get LiteTopic quota information for a namespace.
     *
     * <p>Requires V5 architecture with RIP-2 gRPC interface.</p>
     *
     * @param namespace optional namespace scope
     * @return LiteTopic quota information
     * @throws UnsupportedOperationException if not supported
     */
    @Override
    public LiteTopicQuota getLiteTopicQuota(Optional<String> namespace) throws Exception {
        if (!supportsLiteTopic()) {
            handleUnsupportedOperation("getLiteTopicQuota");
        }

        try {
            return metadataProvider.getLiteTopicQuota(namespace);
        } catch (UnsupportedOperationException e) {
            log.error("LiteTopic quota query not supported: {}", e.getMessage());
            throw e;
        }
    }

    /**
     * Check if LiteTopic management is supported in the current cluster architecture.
     *
     * @return true if LiteTopic is supported (V5 Proxy with capability), false otherwise
     */
    @Override
    public boolean isLiteTopicSupported() {
        return supportsLiteTopic();
    }
}