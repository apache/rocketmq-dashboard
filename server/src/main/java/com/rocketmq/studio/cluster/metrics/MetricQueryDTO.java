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

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Prometheus range query")
public class MetricQueryDTO {
    @Schema(description = "PromQL expression evaluated by Prometheus",
            example = "sum(rate(rocketmq_messages_in_total[1m])) by (node_id)", minLength = 1,
            requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Metric query is required")
    @Size(max = 4096, message = "Metric query must not exceed 4096 characters")
    private String metric;

    @Schema(description = "Range start as a Unix timestamp in seconds", example = "1784112606",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Positive(message = "Metric query start must be positive")
    private long start;

    @Schema(description = "Range end as a Unix timestamp in seconds", example = "1784114406",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @Positive(message = "Metric query end must be positive")
    private long end;

    @Schema(description = "Prometheus query resolution step as a duration or number of seconds", example = "30s",
            minLength = 1, requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "Metric query step is required")
    @Size(max = 32, message = "Metric query step must not exceed 32 characters")
    private String step;
}
