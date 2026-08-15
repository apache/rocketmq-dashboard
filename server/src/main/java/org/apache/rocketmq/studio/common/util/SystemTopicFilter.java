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
package org.apache.rocketmq.studio.common.util;

import java.util.Set;
import org.apache.rocketmq.common.topic.TopicValidator;

/**
 * Shared utility for identifying RocketMQ system topics.
 *
 * <p>Consolidates the system topic prefix and exact-match lists that were previously
 * duplicated (with inconsistencies) across {@code RocketMQMetadataProvider}
 * and {@code RocketMQDashboardProvider}.
 */
public final class SystemTopicFilter {

    private static final String RETRY_TOPIC_PREFIX = "%RETRY%";
    private static final String DEAD_LETTER_TOPIC_PREFIX = "%DLQ%";

    private SystemTopicFilter() {
    }

    /**
     * Returns {@code true} if the given topic name is a RocketMQ system topic
     * that should be hidden from user-facing lists and excluded from dashboard counts.
     *
     * @param topicName   the topic name, may be {@code null}
     * @param brokerNames optional set of broker names; topics matching a broker name
     *                    are also considered system topics (may be empty)
     * @return {@code true} if the topic is a system topic
     */
    public static boolean isSystem(String topicName, Set<String> brokerNames) {
        if (topicName == null || topicName.isEmpty()) {
            return true;
        }
        return TopicValidator.isSystemTopic(topicName)
                || topicName.startsWith(RETRY_TOPIC_PREFIX)
                || topicName.startsWith(DEAD_LETTER_TOPIC_PREFIX)
                || brokerNames != null && brokerNames.contains(topicName);
    }

    /**
     * Convenience overload without broker names.
     */
    public static boolean isSystem(String topicName) {
        return isSystem(topicName, Set.of());
    }
}
