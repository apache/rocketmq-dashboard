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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicShrinkPrecheckVO {

    private String topic;
    private int currentQueueNum;
    private int targetQueueNum;
    private boolean safeToShrink;
    private long totalUnconsumedMessagesOnTruncatedQueues;
    private LocalDateTime precheckTimestamp;
    @Builder.Default
    private List<TruncatedQueueRiskVO> truncatedQueues = new ArrayList<>();
    @Builder.Default
    private List<String> warnings = new ArrayList<>();
    @Builder.Default
    private List<GracefulDrainStepVO> recommendedSteps = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TruncatedQueueRiskVO {
        private String brokerName;
        private int queueId;
        private long minOffset;
        private long maxOffset;
        private long unconsumedLag;
        private String riskLevel; // CRITICAL, HIGH, LOW
        private String consumerGroupWithMaxLag;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GracefulDrainStepVO {
        private int stepOrder;
        private String action; // SET_READ_ONLY_ON_TARGET_QUEUES, WAIT_CONSUMER_CATCHUP, UPDATE_QUEUE_NUM
        private String description;
        private long estimatedTimeSeconds;
    }
}
