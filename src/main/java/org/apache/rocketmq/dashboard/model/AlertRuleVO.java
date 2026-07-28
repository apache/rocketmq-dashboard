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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Alert rule value object for the alert CRUD API ({@code /api/alert/rules}).
 * Field layout follows Prometheus alerting rule conventions.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AlertRuleVO {
    private String id;
    /** Alert rule name (e.g. "BrokerDownAlert") */
    private String alert;
    /** Grouping label (e.g. "broker", "topic", "consumer") */
    private String group;
    /** Prometheus-style expression (e.g. "up{job=\"broker\"} == 0") */
    private String expr;
    /** Duration before alert fires (e.g. "5m") - mapped from JSON key "for" */
    @JsonProperty("for")
    private String forDuration;
    /** Severity level: critical, warning, info */
    private String severity;
    /** Responsible team: broker, topic, consumer, client, proxy, security, reliability */
    private String team;
    /** Brief summary of the alert */
    private String summary;
    /** Detailed description */
    private String description;
    /** Notification channels */
    private List<String> channels;
    /** Whether the rule is enabled */
    private boolean enabled;
    /** Last triggered timestamp */
    private String lastTriggered;
    /** Creation time (formatted yyyy-MM-dd HH:mm:ss) */
    private String createdAt;
    /** Last update time (formatted yyyy-MM-dd HH:mm:ss) */
    private String updatedAt;

    // Legacy fields kept for backward compatibility
    private String name;
    private String metric;
    private String operator;
    private double threshold;
    private String thresholdUnit;
    private String duration;
}
