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
package com.rocketmq.studio.cluster.metrics;

public enum MetricProfile {
    ROCKETMQ_4_EXPORTER(
            "rocketmq4-exporter",
            "RocketMQ 4.x Exporter",
            "RocketMQ 4.x clusters scraped through the standalone rocketmq-exporter"),
    ROCKETMQ_5_NATIVE(
            "rocketmq5-native",
            "RocketMQ 5.x Native",
            "RocketMQ 5.1+ native OpenTelemetry and Prometheus metrics");

    private final String id;
    private final String displayName;
    private final String description;

    MetricProfile(String id, String displayName, String description) {
        this.id = id;
        this.displayName = displayName;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
