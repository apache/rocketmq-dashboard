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
import org.apache.rocketmq.studio.cluster.config.BrokerConfigUpdateFailureVO;
import org.apache.rocketmq.studio.cluster.config.ClusterConfigUpdateResultVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrokerConfigUpdateOutput(
        String status,
        List<String> successfulBrokers,
        List<BrokerConfigUpdateFailure> failedBrokers) {

    public static BrokerConfigUpdateOutput from(ClusterConfigUpdateResultVO result) {
        return new BrokerConfigUpdateOutput(
                result.getStatus() != null ? result.getStatus().name() : null,
                result.getSuccessfulBrokers() != null ? result.getSuccessfulBrokers() : List.of(),
                result.getFailedBrokers() != null
                        ? result.getFailedBrokers().stream().map(BrokerConfigUpdateFailure::from).toList()
                        : List.of());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BrokerConfigUpdateFailure(
            String address,
            String message) {

        public static BrokerConfigUpdateFailure from(BrokerConfigUpdateFailureVO failure) {
            return new BrokerConfigUpdateFailure(failure.getAddress(), failure.getMessage());
        }
    }
}
