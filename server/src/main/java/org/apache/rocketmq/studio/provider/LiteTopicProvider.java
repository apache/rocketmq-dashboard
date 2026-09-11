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
package org.apache.rocketmq.studio.provider;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.LiteTopicQuota;
import org.apache.rocketmq.studio.model.LiteTopicSession;
import org.apache.rocketmq.studio.model.LiteTopicSummary;

import java.util.List;

/**
 * LiteTopic (LMQ-backed light message queue) inspection SPI.
 *
 * <p>LiteTopic is a broker-side feature: a parent topic declared with
 * {@code TopicMessageType.LITE} stores each "lite topic" in its own LMQ whose name is
 * {@code %LMQ%$parentTopic$liteTopic}. The studio queries the broker directly through the
 * RocketMQ lite admin RPCs ({@code GET_BROKER_LITE_INFO}, {@code GET_PARENT_TOPIC_INFO},
 * {@code GET_LITE_CLIENT_INFO}, {@code GET_LITE_GROUP_INFO}).
 *
 * <p>Implementations that cannot reach a lite-capable broker must report
 * {@link #isSupported()} as {@code false} and leave the default methods untouched, so the
 * console degrades to an informational "not supported" state instead of failing.
 */
public interface LiteTopicProvider {

    String UNSUPPORTED = "LiteTopic is not supported by this provider";
    int NOT_IMPLEMENTED = 501;

    /**
     * Whether the active cluster exposes the LiteTopic admin surface. Detected by probing the
     * broker; a cluster running a RocketMQ build without LiteTopic answers the probe with an
     * unsupported-code error.
     */
    default boolean isSupported() {
        return false;
    }

    /**
     * Aggregates every lite parent topic visible on the cluster into one summary per parent topic.
     *
     * @param pattern   optional substring filter applied to the parent topic name
     * @param namespace optional namespace filter; {@code null} or blank returns every namespace
     */
    default List<LiteTopicSummary> listLiteTopics(String pattern, String namespace) {
        throw new BusinessException(NOT_IMPLEMENTED, UNSUPPORTED);
    }

    /**
     * Resolves one "session" — a single client's lite topic set for one parent topic and group.
     * The session id is an opaque token produced by {@link #listLiteTopics}.
     */
    default LiteTopicSession getSession(String sessionId) {
        throw new BusinessException(NOT_IMPLEMENTED, UNSUPPORTED);
    }

    /**
     * Rewrites the parent topic's {@code lite.topic.expiration} attribute (the single TTL policy
     * shared by every lite topic under that parent).
     *
     * @param topicPattern the parent topic name
     * @param ttlMillis    the new TTL in milliseconds
     */
    default void extendTTL(String topicPattern, long ttlMillis) {
        throw new BusinessException(NOT_IMPLEMENTED, UNSUPPORTED);
    }

    /** Cluster-wide LiteTopic quota, aggregated across broker masters. */
    default LiteTopicQuota getQuota(String namespace) {
        throw new BusinessException(NOT_IMPLEMENTED, UNSUPPORTED);
    }
}
