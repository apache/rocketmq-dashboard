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
package org.apache.rocketmq.studio.proxyadmin;

import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * View objects for the RIP-2 ProxyAdminService surfaces exposed by the studio
 * (route observation + POP/batch consume diagnostics). Kept protocol-pure: the
 * gRPC/proto types never leak into the REST layer.
 */
public final class ProxyAdminDiagnosticsVO {

    private ProxyAdminDiagnosticsVO() {
    }

    /** DescribeRouteTopology: proxy -> broker links and per-broker queue load. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteTopology {
        private String topic;
        private List<Link> links;
        private List<Load> load;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Link {
            private String proxyEndpoint;
            private String brokerName;
            private String brokerAddress;
            private boolean healthy;
            private int activeConnections;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Load {
            private String brokerName;
            private int readQueueNums;
            private int writeQueueNums;
            private long currentLoad;
            private boolean regionAffinity;
        }
    }

    /** DescribePopReceiptHandles: in-flight POP receipt handles of a consumer group. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PopReceiptHandles {
        private Summary summary;
        private List<Handle> handles;
        private int total;
        private int pageNum;
        private int pageSize;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Summary {
            private String group;
            private int totalHandles;
            private int totalMessages;
            private long totalRenewTimes;
            private long totalRenewRetryTimes;
            private int expiredHandles;
            private long totalAckCount;
            private long totalNackCount;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Handle {
            private String group;
            private String topic;
            private int queueId;
            private String messageId;
            private long queueOffset;
            private int reconsumeTimes;
            private int renewTimes;
            private int renewRetryTimes;
            private long consumeTimestampMillis;
            private String receiptHandle;
            private long nextVisibleTimeMillis;
            private long invisibleTimeMillis;
            private String brokerName;
            private boolean expired;
            private String lockOwner;
        }
    }

    /** DescribeBatchConsumeDiagnostics: per-client unacked/renew aggregates of a group. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BatchConsumeDiagnostics {
        private Summary summary;
        private List<ClientDiagnostics> diagnostics;
        private int total;
        private int pageNum;
        private int pageSize;

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class Summary {
            private String group;
            private int totalClients;
            private long totalUnackedMessages;
            private long totalUnackedHandles;
            private long totalExpiredHandles;
            private long totalRenewTimes;
            private long totalRenewRetryTimes;
        }

        @Data
        @Builder
        @NoArgsConstructor
        @AllArgsConstructor
        public static class ClientDiagnostics {
            private String clientId;
            private String channelId;
            private String consumeType;
            private String messageModel;
            private int unackedMessageCount;
            private int unackedHandleCount;
            private int expiredHandleCount;
            private long totalRenewTimes;
            private long totalRenewRetryTimes;
            private long connectTimeMillis;
            private long lastRttMillis;
            private int receiveBatchSize;
            private Map<String, Integer> topicDistribution;
        }
    }

    /** SubscribeRouteEvents: route change events collected over a bounded window. */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class RouteEvent {
        private String eventType;
        private String topic;
        private String cluster;
        private long timestampMillis;
        private String brokerName;
        private String brokerAddress;
        private int previousReadQueueNums;
        private int currentReadQueueNums;
        private int previousWriteQueueNums;
        private int currentWriteQueueNums;
        private boolean hasSnapshot;
        private int snapshotBrokerCount;
        private int snapshotQueueCount;
    }
}
