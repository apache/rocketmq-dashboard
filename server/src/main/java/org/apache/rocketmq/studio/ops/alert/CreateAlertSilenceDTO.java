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

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;

@Data
public class CreateAlertSilenceDTO {
    private AlertDomain domain;
    private Long ruleId;
    private String instanceId;
    /** Optional exact-match labels, such as brokerName, topic, or consumerGroup. */
    private Map<String, String> labels;
    @NotNull(message = "startsAt is required")
    private OffsetDateTime startsAt;
    @NotNull(message = "endsAt is required")
    private OffsetDateTime endsAt;
    private AlertSilenceRecurrence recurrence;
    private String timeZone;
    private Set<Integer> recurrenceDays;
    private OffsetDateTime recurrenceUntil;
    private String reason;
}
