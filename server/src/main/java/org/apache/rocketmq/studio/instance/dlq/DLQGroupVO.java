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
package org.apache.rocketmq.studio.instance.dlq;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQGroupVO {
    private String groupName;
    private String dlqTopic;  // format: %DLQ%{groupName}
    private long messageCount;
    /**
     * ISO-8601 instant with an explicit UTC offset (e.g. {@code 2026-09-22T02:15:30.123Z}), so a
     * browser parses the same point in time regardless of its timezone. A zoneless value (the
     * shape a {@link java.time.LocalDateTime} serializes to) would be read as browser-local time
     * and mislabel the instant whenever the server timezone differs.
     */
    private String lastEnqueueTime;
    private int retryCount;
    private String status;
    @Builder.Default
    private boolean statsAvailable = true;
}
