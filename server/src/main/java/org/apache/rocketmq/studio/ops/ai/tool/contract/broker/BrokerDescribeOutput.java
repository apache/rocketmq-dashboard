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
package org.apache.rocketmq.studio.ops.ai.tool.contract.broker;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.apache.rocketmq.studio.cluster.broker.BrokerVO;
import org.apache.rocketmq.studio.common.domain.enums.BrokerStatus;

/**
 * Single-broker full view: replica-group detail merged with its runtime statistics
 * (tpsIn=putTps, tpsOut=getTransferredTps, diskUsage=commitLogDiskRatio, daily put/get
 * counters, version). Absorbs the former rmq.broker.runtime_info single-broker fields
 * (decision 20).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrokerDescribeOutput(
        String brokerName,
        String addr,
        String version,
        BrokerStatus status,
        double diskUsage,
        long tpsIn,
        long tpsOut,
        long putMessagesToday,
        long putMessagesYesterday,
        long getMessagesToday,
        long getMessagesYesterday,
        boolean runtimeStatsAvailable) {

    public static BrokerDescribeOutput from(BrokerVO broker) {
        return new BrokerDescribeOutput(
                broker.getName(),
                broker.getAddr(),
                broker.getVersion(),
                broker.getStatus(),
                broker.getDiskUsage(),
                broker.getTpsIn(),
                broker.getTpsOut(),
                broker.getPutMessagesToday(),
                broker.getPutMessagesYesterday(),
                broker.getGetMessagesToday(),
                broker.getGetMessagesYesterday(),
                broker.isRuntimeStatsAvailable());
    }
}
