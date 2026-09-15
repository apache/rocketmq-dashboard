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
import org.apache.rocketmq.studio.cluster.config.BrokerConfigDiffVO;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BrokerConfigOutput(
        String cluster,
        boolean complete,
        boolean driftDetected,
        int brokerCount,
        int reachableBrokerCount,
        List<String> comparedFields,
        List<BrokerStatus> brokers,
        List<ConfigDifference> differences) {

    public static BrokerConfigOutput from(BrokerConfigDiffVO diff, String instanceId) {
        return new BrokerConfigOutput(
                instanceId,
                diff.isComplete(),
                diff.isDriftDetected(),
                diff.getBrokerCount(),
                diff.getReachableBrokerCount(),
                diff.getComparedFields(),
                diff.getBrokers() != null
                        ? diff.getBrokers().stream().map(BrokerStatus::from).toList()
                        : List.of(),
                diff.getDifferences() != null
                        ? diff.getDifferences().stream().map(ConfigDifference::from).toList()
                        : List.of());
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record BrokerStatus(
            String name,
            String address,
            boolean reachable,
            String message) {

        public static BrokerStatus from(BrokerConfigDiffVO.BrokerStatusVO broker) {
            return new BrokerStatus(
                    broker.getName(),
                    broker.getAddress(),
                    broker.isReachable(),
                    broker.getMessage());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfigDifference(
            String field,
            String brokerProperty,
            List<ConfigValue> values) {

        public static ConfigDifference from(BrokerConfigDiffVO.ConfigDifferenceVO diff) {
            return new ConfigDifference(
                    diff.getField(),
                    diff.getBrokerProperty(),
                    diff.getValues() != null
                            ? diff.getValues().stream().map(ConfigValue::from).toList()
                            : List.of());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ConfigValue(
            String brokerName,
            String address,
            boolean configured,
            String value) {

        public static ConfigValue from(BrokerConfigDiffVO.ConfigValueVO value) {
            return new ConfigValue(
                    value.getBrokerName(),
                    value.getAddress(),
                    value.isConfigured(),
                    value.getValue());
        }
    }
}
