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

import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.ops.ai.tool.ToolCatalogMetadata;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatDTO request) {
        return aiService.chat(request);
    }

    @PostMapping("/execute")
    public Result<AiExecuteResultVO> execute(@RequestBody AiCommandDTO command) {
        return Result.ok(aiService.execute(command));
    }

    @GetMapping("/tools")
    public ResponseEntity<Result<List<AiToolVO>>> listTools(
            @RequestParam(required = false) String cluster) {
        List<AiToolVO> tools = cluster == null
                ? aiService.listTools()
                : aiService.listTools(cluster);
        return withCatalogHeaders(Result.ok(tools));
    }

    @PostMapping("/tools/call")
    public ResponseEntity<Result<AiToolExecutionResultVO>> callTool(
            @RequestBody AiToolCallDTO call) {
        if (call.getSource() == null || call.getSource().isBlank()) {
            call.setSource("HTTP");
        }
        return withCatalogHeaders(Result.ok(aiService.callTool(call)));
    }

    @PostMapping("/tools/{name}/execute")
    public Result<Object> executeTool(
            @PathVariable String name,
            @RequestBody(required = false) Map<String, Object> input) {
        Map<String, Object> normalizedInput = input == null
                ? Collections.emptyMap()
                : input;
        return Result.ok(aiService.executeTool(name, normalizedInput));
    }

    private <T> ResponseEntity<Result<T>> withCatalogHeaders(Result<T> body) {
        ToolCatalogMetadata metadata = aiService.catalogMetadata();
        return ResponseEntity.ok()
                .header("X-RMQ-Catalog-Version", metadata.version())
                .header("X-RMQ-Catalog-Digest", metadata.digest())
                .header("X-RMQ-Minimum-Client-Version", metadata.minimumClientVersion())
                .body(body);
    }
}
