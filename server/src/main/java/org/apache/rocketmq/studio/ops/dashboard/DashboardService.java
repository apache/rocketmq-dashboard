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

package org.apache.rocketmq.studio.ops.dashboard;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.util.TtlCache;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardService {

    /**
     * Dashboard aggregates fan out across every topic/broker/group on each read; the
     * frontend polls this endpoint, so cache short-lived results per instance to avoid
     * recomputing the whole graph on every poll.
     */
    private static final long DASHBOARD_CACHE_TTL_MILLIS = 10_000L;

    private final DashboardProvider dashboardProvider;
    private final TtlCache<String, DashboardDataVO> dashboardCache =
            new TtlCache<>(DASHBOARD_CACHE_TTL_MILLIS);

    public DashboardDataVO getDashboard() {
        return getDashboard(null);
    }

    public DashboardDataVO getDashboard(String instanceId) {
        String key = StringUtils.hasText(instanceId) ? instanceId.trim() : "";
        log.debug("Fetching dashboard data for instance={}", key.isEmpty() ? "all" : key);
        return dashboardCache.get(key,
                () -> key.isEmpty()
                        ? dashboardProvider.getDashboardData()
                        : dashboardProvider.getDashboardData(key));
    }
}
