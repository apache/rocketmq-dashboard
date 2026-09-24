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
package org.apache.rocketmq.studio.instance.dlq;

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
public class DLQClusteringReportVO {

    private String groupName;
    private int totalSampledMessages;
    private int clusterCount;
    @Builder.Default
    private List<DLQMessageClusterVO> clusters = new ArrayList<>();
    @Builder.Default
    private List<String> replayRecommendations = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DLQMessageClusterVO {
        private String clusterKey;
        private String originTopic;
        private String detectedExceptionClass;
        private int messageCount;
        private double percentage;
        private int averageReconsumeTimes;
        private boolean recommendedForReplay;
        private String riskAssessment; // SAFE_TO_REPLAY, CAUTION_IDEMPOTENCY_RISK, PERMANENT_SCHEMA_FAILURE
        @Builder.Default
        private List<String> sampleMsgIds = new ArrayList<>();
    }
}
