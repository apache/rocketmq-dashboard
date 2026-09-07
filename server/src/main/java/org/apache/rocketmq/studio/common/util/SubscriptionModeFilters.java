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

import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;

import java.util.List;

/**
 * Shared handling for the optional Push/Pop subscription-mode filter accepted by
 * {@code /api/groups/page} and the group CSV export.
 *
 * <p>Providers implement the filter differently: the Apache metadata provider pushes it into
 * the database query while cloud providers keep the in-memory pagination fallback. This class
 * keeps both paths (and the legacy null/blank/ALL values) in agreement.
 */
public final class SubscriptionModeFilters {

    private SubscriptionModeFilters() {
    }

    /**
     * Normalizes a client-supplied mode filter to the canonical {@link SubscriptionMode} name
     * ({@code "Push"} or {@code "Pop"}). Blank values and {@code "ALL"} (the UI's no-filter
     * sentinel) map to {@code null}; unknown values are also treated as no-filter so the
     * endpoint stays backward compatible with the export path.
     *
     * @param subscriptionMode the raw filter value, may be {@code null}
     * @return the canonical mode name, or {@code null} when no filter applies
     */
    public static String normalize(String subscriptionMode) {
        if (subscriptionMode == null) {
            return null;
        }
        String mode = subscriptionMode.trim();
        if (mode.isEmpty() || "ALL".equalsIgnoreCase(mode)) {
            return null;
        }
        if (SubscriptionMode.Push.name().equalsIgnoreCase(mode)) {
            return SubscriptionMode.Push.name();
        }
        if (SubscriptionMode.Pop.name().equalsIgnoreCase(mode)) {
            return SubscriptionMode.Pop.name();
        }
        return null;
    }

    /**
     * Applies the normalized mode filter to an in-memory group list.
     *
     * @param groups           the unfiltered groups, may contain {@code null} entries
     * @param subscriptionMode the raw filter value, may be {@code null}
     * @return the groups matching the requested mode, or the input list when no filter applies
     */
    public static List<ConsumerGroupVO> filter(List<ConsumerGroupVO> groups, String subscriptionMode) {
        String mode = normalize(subscriptionMode);
        if (mode == null) {
            return groups;
        }
        return groups.stream()
                .filter(group -> group != null && mode.equalsIgnoreCase(toModeName(group)))
                .toList();
    }

    /** A missing mode on legacy rows is surfaced as Push by every provider. */
    private static String toModeName(ConsumerGroupVO group) {
        return group.getSubscriptionMode() == null
                ? SubscriptionMode.Push.name()
                : group.getSubscriptionMode().name();
    }
}
