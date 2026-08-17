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
package org.apache.rocketmq.studio.ops.alert;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.util.List;

@Data
public class AlertRuleRequestDTO {
    private Long id;
    @NotBlank(message = "name is required")
    private String name;
    private String metric;
    @Pattern(regexp = ">|>=|<|<=|==|!=", message = "operator is invalid")
    private String operator;
    private double threshold;
    private String thresholdUnit;
    @Pattern(regexp = "(?:[0-9]+(?:ms|s|m|h|d|w|y))+", message = "duration is invalid")
    private String duration;
    private List<String> channels;
    private boolean enabled;
    private String description;
    private String brokerName;
    private String clusterName;
    @Pattern(regexp = "critical|warning|info", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "severity is invalid")
    private String severity;

    public AlertRuleVO toAlertRuleVO() {
        return AlertRuleVO.builder()
                .id(id)
                .name(name)
                .metric(metric)
                .operator(operator)
                .threshold(threshold)
                .thresholdUnit(thresholdUnit)
                .duration(duration)
                .channels(channels)
                .enabled(enabled)
                .description(description)
                .brokerName(brokerName)
                .clusterName(clusterName)
                .severity(severity)
                .build();
    }
}
