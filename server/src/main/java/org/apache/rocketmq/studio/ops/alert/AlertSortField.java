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

import org.apache.rocketmq.studio.common.exception.BusinessException;

/**
 * Sort contract for the paged alert-rule feed. {@code AlertSortField} is a caller-facing name
 * mapped through a fixed allow-list onto the {@code rmq_alert_rule} columns; anything outside the
 * allow-list is rejected with 400 instead of being interpolated into the query.
 */
public enum AlertSortField {
    NAME("name"),
    METRIC("metric"),
    THRESHOLD("threshold"),
    DURATION("duration"),
    ENABLED("enabled"),
    LAST_TRIGGERED("last_triggered");

    private final String column;

    AlertSortField(String column) {
        this.column = column;
    }

    public String column() {
        return column;
    }

    /** Maps a caller-supplied sort field to its column; blank maps to null (the default order). */
    public static AlertSortField parse(String sortField) {
        if (sortField == null || sortField.isBlank()) {
            return null;
        }
        try {
            return valueOf(sortField.trim().toUpperCase());
        } catch (IllegalArgumentException ignored) {
            throw new BusinessException(400, "Unsupported alert rule sort field: " + sortField);
        }
    }
}
