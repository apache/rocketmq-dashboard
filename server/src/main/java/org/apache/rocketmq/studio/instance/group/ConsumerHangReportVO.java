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
public class ConsumerHangReportVO {

    private String instanceId;
    private String groupName;
    private String clientId;
    private String overallHealth; // HEALTHY, WARNING, CRITICAL
    private int totalQueuesAnalyzed;
    private int stalledQueueCount;
    private long maxStallDurationMs;
    private int blockedThreadCount;
    private LocalDateTime analyzedAt;
    @Builder.Default
    private List<QueueHangFindingVO> queueFindings = new ArrayList<>();
    @Builder.Default
    private List<String> diagnosticSuggestions = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class QueueHangFindingVO {
        private String topic;
        private String brokerName;
        private int queueId;
        private int cachedMsgCount;
        private long cachedMsgSizeInMiB;
        private long lastPullTimestamp;
        private long lastConsumeTimestamp;
        private long stallDurationMs;
        private String rootCause; // BUSINESS_LISTENER_BLOCKED, PULL_FLOW_CONTROL, CLIENT_DROPPED_QUEUE
        private String severity; // CRITICAL, HIGH, MEDIUM
        private String recommendation;
    }
}
