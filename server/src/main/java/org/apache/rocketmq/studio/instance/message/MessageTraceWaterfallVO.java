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
package org.apache.rocketmq.studio.instance.message;

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
public class MessageTraceWaterfallVO {

    private String msgId;
    private String topic;
    private long totalDurationMs;
    private long producerSendDurationMs;
    private long brokerTransitDurationMs;
    private long consumerProcessingDurationMs;
    private boolean completed;
    private String bottleneckStage; // PRODUCER_SEND, BROKER_STORAGE_TRANSIT, CONSUMER_EXECUTION, NONE
    @Builder.Default
    private List<TraceStageSpanVO> stages = new ArrayList<>();
    @Builder.Default
    private List<String> diagnosticAlerts = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TraceStageSpanVO {
        private String stageName; // SEND, BROKER_TRANSIT, CONSUME
        private String status; // SUCCESS, FAILED, TIMEOUT
        private long startTimestamp;
        private long endTimestamp;
        private long durationMs;
        private double durationPercent;
        private String executorNode;
        private String details;
    }
}
