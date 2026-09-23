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
package org.apache.rocketmq.studio.cluster.broker;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrokerDiskForecasterReportVO {

    private String clusterId;
    private int totalBrokers;
    private int criticalWatermarkBrokerCount;
    private boolean anyBrokerBlocked;
    private LocalDateTime forecastTimestamp;
    @Builder.Default
    private List<BrokerDiskAssessmentVO> brokerAssessments = new ArrayList<>();
    @Builder.Default
    private List<String> operationalAlerts = new ArrayList<>();
    @Builder.Default
    private List<CapacityPlanActionVO> capacityPlanActions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerDiskAssessmentVO {
        private String brokerName;
        private String brokerAddress;
        private double currentDiskUsageRatio; // 0.0 ~ 1.0 (e.g. 0.85 = 85%)
        private String watermarkLevel; // NORMAL, CLEAN_RESOURCE, WARN, DANGEROUS, SHUTDOWN
        private boolean writeBlocked;
        private long estimatedHoursToShutdown;
        private long dailyMessageIngestGrowth;
        private String recommendation;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CapacityPlanActionVO {
        private String brokerName;
        private String targetAction; // EXPAND_STORAGE_VOLUME, PURGE_EXPIRED_COMMITLOG, TIGHTEN_RETENTION
        private String urgency; // IMMEDIATE, WITHIN_24_HOURS, ROUTINE
        private String actionDetail;
    }
}
