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
package org.apache.rocketmq.studio.cluster.metrics;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
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
    @Schema(description = "Raw PromQL expression evaluated by Prometheus. Mutually exclusive with profileId and "
            + "semanticMetric", example = "sum(rate(rocketmq_messages_in_total[1m])) by (node_id)", minLength = 1)
    @Size(max = 4096, message = "Metric query must not exceed 4096 characters")
    private String metric;

    @Schema(description = "Metric profile used to resolve a semantic metric", example = "rocketmq5-native",
            minLength = 1)
    @Size(max = 128, message = "Metric profile ID must not exceed 128 characters")
    private String profileId;

    @Schema(description = "Semantic metric key resolved through the selected profile",
            example = "consumer_lag_messages", minLength = 1)
    @Size(max = 128, message = "Semantic metric must not exceed 128 characters")
    private String semanticMetric;

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

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Metric query is required")
    public boolean isMetricSelectionPresent() {
        return hasText(metric) || hasText(profileId) || hasText(semanticMetric);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Metric profile and semantic metric are required together")
    public boolean isSemanticMetricSelectionComplete() {
        return hasText(metric) || hasText(profileId) == hasText(semanticMetric);
    }

    @JsonIgnore
    @Schema(hidden = true)
    @AssertTrue(message = "Metric query cannot be combined with a semantic metric selection")
    public boolean isMetricSelectionExclusive() {
        return !hasText(metric) || !hasText(profileId) && !hasText(semanticMetric);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
