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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AlertRuleRequestDTO {
    static final String PROMETHEUS_DURATION_REGEXP = "(?:[0-9]+(?:ms|s|m|h|d|w|y))+";

    private Long id;
    @NotBlank(message = "name is required")
    private String name;
    private String metric;
    @Pattern(regexp = ">|>=|<|<=|==|!=|UNAVAILABLE", message = "operator is invalid")
    private String operator;
    private double threshold;
    private String thresholdUnit;
    @Pattern(regexp = PROMETHEUS_DURATION_REGEXP, message = "duration is invalid")
    private String duration;
    @Pattern(regexp = "LAST|MAX|MIN|AVG|SUM", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "aggregation is invalid")
    private String aggregation;
    @Min(value = 0, message = "windowSeconds must not be negative")
    private Integer windowSeconds;
    private List<@NotBlank(message = "channel must not be blank")
            @Pattern(regexp = "dingtalk|sms|email", flags = Pattern.Flag.CASE_INSENSITIVE,
                    message = "channel is unsupported") String> channels;
    private boolean enabled;
    private String description;
    private String brokerName;
    private String clusterName;
    @Pattern(regexp = "critical|warning|info", flags = Pattern.Flag.CASE_INSENSITIVE,
            message = "severity is invalid")
    private String severity;
    private String instanceId;
    private String consumerGroup;
    private String topic;
    @Min(value = 1, message = "consecutiveSamples must be at least 1")
    private Integer consecutiveSamples;
    @Pattern(regexp = "(?:[0-9]+(?:ms|s|m|h|d|w|y))+", message = "reminderInterval is invalid")
    private String reminderInterval;
    @Size(max = 4000, message = "notificationTemplate must not exceed 4000 characters")
    private String notificationTemplate;

    public AlertRuleVO toAlertRuleVO() {
        String normalizedMetric = metric == null ? null : metric.trim();
        return AlertRuleVO.builder()
                .id(id)
                .name(name)
                .metric(normalizedMetric)
                .operator(operator)
                .threshold(threshold)
                .thresholdUnit(thresholdUnit)
                .duration(duration)
                .aggregation(aggregation == null ? "LAST" : aggregation)
                .windowSeconds(windowSeconds == null ? 0 : windowSeconds)
                .channels(normalizeChannels(channels))
                .enabled(enabled)
                .description(description)
                .brokerName(brokerName)
                .clusterName(clusterName)
                .severity(severity)
                .instanceId(instanceId)
                .consumerGroup(consumerGroup)
                .topic(topic)
                .consecutiveSamples(consecutiveSamples == null ? 1 : consecutiveSamples)
                .reminderInterval(reminderInterval == null ? "30m" : reminderInterval)
                .notificationTemplate(notificationTemplate)
                .build();
    }

    private static List<String> normalizeChannels(List<String> values) {
        if (values == null) {
            return null;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }
}
