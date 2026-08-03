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

@RestController
@RequestMapping("/api/alert-rules/assets")
@RequiredArgsConstructor
public class AlertRuleAssetController {

    private final AlertRuleAssetService alertRuleAssetService;

    @Operation(summary = "List bundled alert rule assets",
            description = "Returns metadata for every RocketMQ Prometheus alert rule YAML shipped with the dashboard")
    @ApiResponse(responseCode = "200", description = "Assets listed successfully", useReturnTypeSchema = true)
    @GetMapping
    public Result<List<AlertRuleAssetInfo>> listAssets() {
        return Result.ok(alertRuleAssetService.listAssets());
    }

    @Operation(summary = "Get an alert rule asset",
            description = "Returns the raw Prometheus alert rule YAML for the given asset name")
    @ApiResponse(responseCode = "200", description = "Asset returned successfully", useReturnTypeSchema = true)
    @ApiResponse(responseCode = "404", description = "Asset name is unknown")
    @GetMapping("/{name}")
    public Result<String> getAsset(@PathVariable("name") String name) {
        return Result.ok(alertRuleAssetService.getAssetYaml(name));
    }

    @Operation(summary = "Export an alert rule asset",
            description = "Returns the raw Prometheus alert rule YAML as a downloadable attachment")
    @ApiResponse(responseCode = "200", description = "Asset YAML returned")
    @ApiResponse(responseCode = "404", description = "Asset name is unknown")
    @GetMapping("/{name}/export")
    public ResponseEntity<byte[]> exportAsset(@PathVariable("name") String name) {
        String yaml = alertRuleAssetService.getAssetYaml(name);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("application/x-yaml"));
        headers.setContentDispositionFormData("attachment", name + ".yaml");
        return new ResponseEntity<>(yaml.getBytes(StandardCharsets.UTF_8), headers, HttpStatus.OK);
    }
}
