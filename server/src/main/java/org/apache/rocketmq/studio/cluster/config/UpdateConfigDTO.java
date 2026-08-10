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
package org.apache.rocketmq.studio.cluster.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateConfigDTO {
    @NotBlank(message = "id is required")
    private String id;

    private String instanceId;

    private String flushDiskType;
    private Boolean autoCreateTopicEnable;
    private Boolean autoCreateSubscriptionGroup;

    @Min(value = 1_048_576, message = "maxMessageSize must be between 1048576 and 134217728")
    @Max(value = 134_217_728, message = "maxMessageSize must be between 1048576 and 134217728")
    private Integer maxMessageSize;

    @Min(value = 1, message = "fileReservedTime must be between 1 and 720")
    @Max(value = 720, message = "fileReservedTime must be between 1 and 720")
    private Integer fileReservedTime;

    @Min(value = 1, message = "writeQueueNums must be between 1 and 256")
    @Max(value = 256, message = "writeQueueNums must be between 1 and 256")
    private Integer writeQueueNums;

    @Min(value = 1, message = "readQueueNums must be between 1 and 256")
    @Max(value = 256, message = "readQueueNums must be between 1 and 256")
    private Integer readQueueNums;

    @Min(value = 0, message = "brokerPermission must be between 0 and 7")
    @Max(value = 7, message = "brokerPermission must be between 0 and 7")
    private Integer brokerPermission;
}
