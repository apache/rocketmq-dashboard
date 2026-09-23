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
public class BrokerHaReportVO {

    private String clusterId;
    private int totalMasterBrokers;
    private int totalSlaveBrokers;
    private boolean allReplicasHealthy;
    private LocalDateTime probeTimestamp;
    @Builder.Default
    private List<BrokerHaPairVO> brokerPairs = new ArrayList<>();
    @Builder.Default
    private List<String> haWarnings = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BrokerHaPairVO {
        private String brokerName;
        private String masterAddress;
        private long masterMaxOffset;
        private boolean masterOnline;
        @Builder.Default
        private List<SlaveReplicationStatusVO> slaves = new ArrayList<>();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SlaveReplicationStatusVO {
        private long brokerId;
        private String slaveAddress;
        private long slaveMaxOffset;
        private long replicationLagBytes;
        private boolean inSync;
        private String healthStatus; // IN_SYNC, MINOR_LAG, CRITICAL_LAG, OFFLINE
        private String message;
    }
}
