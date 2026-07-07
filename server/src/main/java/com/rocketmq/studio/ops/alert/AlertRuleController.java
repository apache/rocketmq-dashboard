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
package com.rocketmq.studio.ops.alert;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertService alertService;

    @GetMapping
    public Result<List<AlertRuleVO>> listRules() {
        return Result.ok(alertService.listRules());
    }

    @PostMapping("/create")
    public Result<AlertRuleVO> createRule(@RequestBody AlertRuleVO rule) {
        return Result.ok(alertService.createRule(rule));
    }

    @PostMapping("/update")
    public Result<AlertRuleVO> updateRule(@RequestBody AlertRuleVO rule) {
        return Result.ok(alertService.updateRule(rule));
    }

    @PostMapping("/toggle")
    public Result<AlertRuleVO> toggleRule(@RequestBody Map<String, Object> request) {
        String id = (String) request.get("id");
        boolean enabled = (Boolean) request.get("enabled");
        return Result.ok(alertService.toggleRule(id, enabled));
    }

    @PostMapping("/delete")
    public Result<Void> deleteRule(@RequestBody Map<String, String> request) {
        alertService.deleteRule(request.get("id"));
        return Result.ok();
    }
}
