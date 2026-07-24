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

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Result object returned by data-source health checks.
 * Contains connection status, discovered metric families, and latency.
 */
@Data
public class MetricsHealthResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Whether the backend was reachable and responded successfully. */
    private boolean connected;

    /** Human-readable status message (e.g., error description). */
    private String statusMessage;

    /** Metric-family names that are expected but NOT found in the data source. */
    private List<String> missingMetricFamilies;

    /** Metric-family names actually discovered via /api/v1/labels or /api/v1/targets. */
    private List<String> availableMetricFamilies;

    /** Round-trip query latency in milliseconds during the health probe. */
    private long queryLatencyMs;
}
