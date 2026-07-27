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
 * Result object returned by the metrics subsystem self-check.
 * Contains individual check items and an aggregate health status.
 */
@Data
public class MetricsSelfCheckResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Timestamp when the self-check was performed. */
    private long timestamp;

    /** Individual check items. */
    private List<CheckItem> checks;

    /** Total number of checks executed. */
    private int totalChecks;

    /** Number of checks that passed. */
    private int passedChecks;

    /** Number of checks that failed. */
    private int failedChecks;

    /** Whether the overall subsystem is healthy (no ERROR-level failures). */
    private boolean healthy;

    /** Human-readable summary of the self-check result. */
    private String summary;
}