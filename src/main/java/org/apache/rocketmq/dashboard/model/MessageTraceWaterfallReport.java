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

package org.apache.rocketmq.dashboard.model;

import java.util.ArrayList;
import java.util.List;

public class MessageTraceWaterfallReport {

    private String msgId;
    private String topic;
    private String tags;
    private String keys;
    private long totalE2eLatencyMs;
    private boolean isTimeout;
    private String bottleneckPhase;
    private List<TraceSpanNode> spanNodes = new ArrayList<>();

    public static class TraceSpanNode {
        private String spanId;
        private String stage; // PRODUCER_SEND, BROKER_STORE, NETWORK_DELIVERY, CONSUMER_PROCESS
        private String clientHost;
        private String targetHost;
        private String groupName;
        private long startTime;
        private long endTime;
        private long durationMs;
        private String status; // SUCCESS, FAILED, TIMEOUT
        private boolean isBottleneck;
        private String details;

        public String getSpanId() {
            return spanId;
        }

        public void setSpanId(String spanId) {
            this.spanId = spanId;
        }

        public String getStage() {
            return stage;
        }

        public void setStage(String stage) {
            this.stage = stage;
        }

        public String getClientHost() {
            return clientHost;
        }

        public void setClientHost(String clientHost) {
            this.clientHost = clientHost;
        }

        public String getTargetHost() {
            return targetHost;
        }

        public void setTargetHost(String targetHost) {
            this.targetHost = targetHost;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public long getStartTime() {
            return startTime;
        }

        public void setStartTime(long startTime) {
            this.startTime = startTime;
        }

        public long getEndTime() {
            return endTime;
        }

        public void setEndTime(long endTime) {
            this.endTime = endTime;
        }

        public long getDurationMs() {
            return durationMs;
        }

        public void setDurationMs(long durationMs) {
            this.durationMs = durationMs;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isBottleneck() {
            return isBottleneck;
        }

        public void setBottleneck(boolean bottleneck) {
            isBottleneck = bottleneck;
        }

        public String getDetails() {
            return details;
        }

        public void setDetails(String details) {
            this.details = details;
        }
    }

    public String getMsgId() {
        return msgId;
    }

    public void setMsgId(String msgId) {
        this.msgId = msgId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTags() {
        return tags;
    }

    public void setTags(String tags) {
        this.tags = tags;
    }

    public String getKeys() {
        return keys;
    }

    public void setKeys(String keys) {
        this.keys = keys;
    }

    public long getTotalE2eLatencyMs() {
        return totalE2eLatencyMs;
    }

    public void setTotalE2eLatencyMs(long totalE2eLatencyMs) {
        this.totalE2eLatencyMs = totalE2eLatencyMs;
    }

    public boolean isTimeout() {
        return isTimeout;
    }

    public void setTimeout(boolean timeout) {
        isTimeout = timeout;
    }

    public String getBottleneckPhase() {
        return bottleneckPhase;
    }

    public void setBottleneckPhase(String bottleneckPhase) {
        this.bottleneckPhase = bottleneckPhase;
    }

    public List<TraceSpanNode> getSpanNodes() {
        return spanNodes;
    }

    public void setSpanNodes(List<TraceSpanNode> spanNodes) {
        this.spanNodes = spanNodes;
    }
}
