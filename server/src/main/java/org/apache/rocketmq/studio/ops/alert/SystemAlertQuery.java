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

import java.time.LocalDateTime;

/** Server-side filters for the paged alert-event feed. */
public record SystemAlertQuery(String level, AlertDomain domain, String instanceId, String transition,
        String labelKey, String labelValue, LocalDateTime from, LocalDateTime to, int page, int pageSize,
        Boolean notificationSuppressed) {
    public SystemAlertQuery(String level, AlertDomain domain, String instanceId, String transition,
            String labelKey, String labelValue, LocalDateTime from, LocalDateTime to, int page, int pageSize) {
        this(level, domain, instanceId, transition, labelKey, labelValue, from, to, page, pageSize, null);
    }
}
