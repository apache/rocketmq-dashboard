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
package org.apache.rocketmq.studio.ops.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolDiscoveryService;
import org.apache.rocketmq.studio.ops.ai.tool.service.ToolExecutionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/ai/tools")
@RequiredArgsConstructor
public class ToolController {

    private final ToolDiscoveryService toolDiscoveryService;
    private final ToolExecutionService toolExecutor;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<List<AiToolVO>> listTools(
            @RequestParam String cluster) {
        return Result.ok(toolDiscoveryService.listTools(cluster));
    }

    @PostMapping("/{name}/execute")
    public Result<Object> executeTool(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> input) {
        Map<String, Object> normalizedInput = input == null
                ? Collections.emptyMap()
                : input;
        AiPayloadGuard.validateToolInvocation(name, normalizedInput, objectMapper);
        log.info("Executing registered AI tool: {}", name);
        return Result.ok(toolExecutor.execute(name, normalizedInput));
    }

}
