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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/llm")
@RequiredArgsConstructor
public class LlmController {

    private final LlmConfigService llmConfigService;

    @GetMapping("/config")
    public Result<LlmConfigVO> getConfig() {
        return Result.ok(llmConfigService.getConfig());
    }

    @PostMapping("/config")
    public Result<LlmOperationResultVO> saveConfig(@RequestBody LlmConfigDTO config) {
        llmConfigService.saveConfig(config.toLlmConfigVO());
        return Result.ok(LlmOperationResultVO.success("saved"));
    }

    @PostMapping("/config/test")
    public Result<LlmOperationResultVO> testConfig(@RequestBody LlmConfigDTO config) {
        return Result.ok(llmConfigService.testConfig(config.toLlmConfigVO()));
    }

    @GetMapping("/models")
    public Result<LlmModelsResultVO> listModels() {
        return Result.ok(llmConfigService.listModels());
    }
}
