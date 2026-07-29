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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Value object representing a branch compensation alert rule.
 * Branch compensation monitors the HA replication lag between
 * master and slave brokers, alerting when slaves fall behind the master
 * beyond configured thresholds.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BranchCompensateAlertRuleVO {
    private String id;
    /** Rule name */
    private String name;
    /** Target broker name pattern (e.g. "broker-a", or "*" for all) */
    private String brokerName;
    /** Target cluster name pattern (e.g. "DefaultCluster", or "*" for all) */
    private String clusterName;
    /** Replication lag threshold in bytes that triggers the alert */
    private long lagThreshold;
    /** Lag threshold unit: B, KB, MB, GB */
    private String lagThresholdUnit;
    /** Duration the lag must persist before alerting (e.g. "5m", "10m") */
    private String duration;
    /** Alert severity: critical, warning, info */
    private String severity;
    /** Notification channels */
    private List<String> channels;
    /** Whether the rule is enabled */
    private boolean enabled;
    /** Brief description of the rule */
    private String description;
    /** Creation time (ISO format) */
    private String createdAt;
    /** Last update time (ISO format) */
    private String updatedAt;
}