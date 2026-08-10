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

/**
 * Shared utility for identifying RocketMQ system consumer groups.
 *
 * <p>Consolidates the system group prefix list that was previously duplicated
 * (with inconsistencies) across {@code RocketMQClientProvider},
 * {@code RocketMQMetadataProvider}, and {@code RocketMQDashboardProvider}.
 */
public final class SystemGroupFilter {

    private SystemGroupFilter() {
    }

    /**
     * Returns {@code true} if the given group name is a RocketMQ system consumer group
     * that should be hidden from user-facing lists and excluded from dashboard counts.
     *
     * @param group the consumer group name, may be {@code null}
     * @return {@code true} if the group is a system group
     */
    public static boolean isSystem(String group) {
        if (group == null || group.isEmpty()) {
            return true;
        }
        return group.startsWith("CID_RMQ_SYS_")
                || group.startsWith("CID_ONSAPI_")
                || group.startsWith("CID_SYS_")
                || group.startsWith("CID_HOUSEKEEPING")
                || group.startsWith("rmq_sys_")
                || group.startsWith("%RETRY%")
                || group.startsWith("%DLQ%")
                || group.startsWith("TOOLS_CONSUMER")
                || group.startsWith("FILTERSRV_CONSUMER")
                || group.startsWith("SELF_TEST_");
    }
}
