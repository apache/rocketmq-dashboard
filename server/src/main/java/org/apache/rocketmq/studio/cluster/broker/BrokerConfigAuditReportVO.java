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
public class BrokerConfigAuditReportVO {

    private String clusterId;
    private String instanceId;
    private int totalBrokers;
    private int onlineBrokers;
    private int driftedFieldCount;
    private double clusterConsistencyScore; // 0.0 ~ 100.0
    private String baselineBrokerAddress;
    private LocalDateTime auditTime;
    @Builder.Default
    private List<DriftedPropertySummaryVO> driftedProperties = new ArrayList<>();
    @Builder.Default
    private List<String> operationalRecommendations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DriftedPropertySummaryVO {
        private String propertyName;
        private String baselineValue;
        private int deviantBrokerCount;
        private String severity; // HIGH, MEDIUM, LOW
        private String impactDescription;
        @Builder.Default
        private List<BrokerPropertyValueVO> brokerValues = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerPropertyValueVO {
        private String brokerName;
        private String brokerAddress;
        private String actualValue;
        private boolean matchesBaseline;
    }
}
