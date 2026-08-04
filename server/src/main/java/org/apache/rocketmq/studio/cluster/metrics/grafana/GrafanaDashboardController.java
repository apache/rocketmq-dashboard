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
package org.apache.rocketmq.studio.cluster.metrics.grafana;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/metrics/grafana")
@RequiredArgsConstructor
public class GrafanaDashboardController {

    private final GrafanaDashboardService grafanaDashboardService;

    @Operation(summary = "List bundled Grafana dashboards",
            description = "Returns metadata for every RocketMQ Grafana dashboard shipped with the dashboard")
    @ApiResponse(responseCode = "200", description = "Dashboards listed successfully", useReturnTypeSchema = true)
    @GetMapping("/dashboards")
    public Result<List<GrafanaDashboardInfo>> listDashboards() {
        return Result.ok(grafanaDashboardService.listDashboards());
    }

    @Operation(summary = "Get a Grafana dashboard model",
            description = "Returns the parsed Grafana dashboard JSON for the given uid")
    @ApiResponse(responseCode = "200", description = "Dashboard returned successfully", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "Dashboard uid is unknown")
    @GetMapping("/dashboards/{uid}")
    public Result<Map<String, Object>> getDashboard(@PathVariable("uid") String uid) {
        return Result.ok(grafanaDashboardService.getDashboard(uid));
    }

    @Operation(summary = "Export a Grafana dashboard JSON",
            description = "Returns the raw Grafana dashboard JSON as a downloadable attachment")
    @ApiResponse(responseCode = "200", description = "Dashboard JSON returned")
    @ApiResponse(responseCode = "404", description = "Dashboard uid is unknown")
    @GetMapping("/dashboards/{uid}/export")
    public ResponseEntity<byte[]> exportDashboard(@PathVariable("uid") String uid) {
        String json = grafanaDashboardService.getDashboardJson(uid);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setContentDispositionFormData("attachment", uid + ".json");
        return new ResponseEntity<>(json.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }
}
