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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.common.exception.BusinessException;

import java.util.List;

public final class SubscriptionModeFilters {

    private SubscriptionModeFilters() {
    }

    public static String normalize(String subscriptionMode) {
        if (subscriptionMode == null || subscriptionMode.isBlank()
                || "ALL".equalsIgnoreCase(subscriptionMode.trim())) {
            return null;
        }
        String mode = subscriptionMode.trim();
        for (SubscriptionMode candidate : SubscriptionMode.values()) {
            if (candidate.name().equalsIgnoreCase(mode)) {
                return candidate.name();
            }
        }
        throw new BusinessException(400, "subscriptionMode must be Push, Pop, or ALL");
    }

    public static List<ConsumerGroupVO> filter(List<ConsumerGroupVO> groups, String subscriptionMode) {
        String mode = normalize(subscriptionMode);
        if (mode == null) {
            return groups;
        }
        return groups.stream()
                .filter(group -> group != null && mode.equals(subscriptionModeOf(group).name()))
                .toList();
    }

    private static SubscriptionMode subscriptionModeOf(ConsumerGroupVO group) {
        return group.getSubscriptionMode() == null ? SubscriptionMode.Push : group.getSubscriptionMode();
    }
}
