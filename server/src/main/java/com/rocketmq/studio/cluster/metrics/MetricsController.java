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

import com.rocketmq.studio.common.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metrics")
@RequiredArgsConstructor
public class MetricsController {

    private final MetricsService metricsService;
    private final MetricProfileService metricProfileService;

    @Operation(summary = "List RocketMQ metric profiles",
            description = "Returns version-aware semantic PromQL mappings for RocketMQ metrics")
    @ApiResponse(responseCode = "200", description = "Metric profiles listed successfully",
            useReturnTypeSchema = true)
    @GetMapping("/profiles")
    public Result<List<MetricProfileVO>> listProfiles() {
        return Result.ok(metricProfileService.listProfiles());
    }

    @Operation(summary = "Query Prometheus range metrics",
            description = "Executes a PromQL range query against the configured Prometheus server")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Range query completed successfully",
                useReturnTypeSchema = true),
        @ApiResponse(responseCode = "400", description = "Invalid request or PromQL expression"),
        @ApiResponse(responseCode = "422", description = "Prometheus could not execute the expression"),
        @ApiResponse(responseCode = "502", description = "Prometheus connection or response failure"),
        @ApiResponse(responseCode = "503", description = "Prometheus is unavailable or not configured"),
        @ApiResponse(responseCode = "504", description = "Prometheus query timed out")
    })
    @PostMapping("/query")
    public Result<MetricDataVO> query(@Valid @RequestBody MetricQueryDTO query) {
        return Result.ok(metricsService.query(query));
    }
}
