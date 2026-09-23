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
package org.apache.rocketmq.studio.instance.topic;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicTrafficSkewReportVO {

    private String topic;
    private int totalQueues;
    private long totalMessagesAcrossQueues;
    private double giniCoefficient; // 0.0 (perfect equality) to 1.0 (maximal skew)
    private double standardDeviation;
    private boolean skewDetected;
    private String skewSeverity; // NORMAL, MODERATE_SKEW, SEVERE_HOTSPOT
    @Builder.Default
    private List<QueueTrafficDistributionVO> queueDistributions = new ArrayList<>();
    @Builder.Default
    private List<String> diagnosticAlerts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueTrafficDistributionVO {
        private String brokerName;
        private int queueId;
        private long minOffset;
        private long maxOffset;
        private long messageVolume;
        private double trafficSharePercent;
        private boolean isHotspot;
    }
}
