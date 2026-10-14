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
package org.apache.rocketmq.studio.instance.topic;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;
import org.apache.rocketmq.studio.provider.LiteTopicProvider;

/**
 * Creates a Lite parent topic in a single write: the LITE message type and the
 * {@code lite.topic.expiration} TTL attribute are committed together so no LITE parent can be left
 * with the never-expiring TTL default.
 */
@Data
public class CreateLiteTopicDTO {
    @NotBlank(message = "instanceId is required")
    private String instanceId;
    @NotBlank(message = "topicPattern is required")
    private String topicPattern;
    @NotNull(message = "ttlMinutes is required")
    @Min(value = 1, message = "ttlMinutes must be between 1 and 43200 minutes")
    @Max(value = LiteTopicProvider.MAX_TTL_MINUTES, message = "ttlMinutes must be between 1 and 43200 minutes")
    private Long ttlMinutes;
    @PositiveOrZero(message = "writeQueues must be zero or positive")
    private Integer writeQueues;
    @PositiveOrZero(message = "readQueues must be zero or positive")
    private Integer readQueues;
    private String remark;
}
