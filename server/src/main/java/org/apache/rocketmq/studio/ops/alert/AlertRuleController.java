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

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/alert-rules")
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertService alertService;

    @GetMapping
    public Result<List<AlertRuleVO>> listRules() {
        return Result.ok(alertService.listRules());
    }

    @GetMapping("/export")
    public Result<AlertRulesYamlVO> exportRules(@RequestParam(name = "ids", required = false) String ids) {
        String rules = ids == null
                ? alertService.exportPrometheusRulesYaml()
                : alertService.exportPrometheusRulesYaml(parseRuleIds(ids));
        return Result.ok(new AlertRulesYamlVO(rules));
    }

    @PostMapping("/create")
    public Result<AlertRuleVO> createRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        return Result.ok(alertService.createRule(requireAlertRule(rule).toAlertRuleVO()));
    }

    @PostMapping("/update")
    public Result<AlertRuleVO> updateRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleRequestDTO request = requireAlertRule(rule);
        if (request.getId() == null || request.getId().isBlank()) {
            throw new BusinessException(400, "id is required");
        }
        return Result.ok(alertService.updateRule(request.toAlertRuleVO()));
    }

    @PostMapping("/toggle")
    public Result<AlertRuleVO> toggleRule(@Valid @RequestBody ToggleAlertRuleDTO request) {
        return Result.ok(alertService.toggleRule(request.getId(), request.getEnabled()));
    }

    @PostMapping("/delete")
    public Result<Void> deleteRule(@Valid @RequestBody DeleteAlertRuleDTO request) {
        alertService.deleteRule(request.getId());
        return Result.ok();
    }

    @PostMapping("/bulk-toggle")
    public Result<AlertRuleBulkResultVO> bulkToggle(
            @Valid @RequestBody BulkToggleAlertRulesDTO request) {
        return Result.ok(alertService.bulkToggleRules(request.getIds(), request.getEnabled()));
    }

    @PostMapping("/bulk-delete")
    public Result<AlertRuleBulkResultVO> bulkDelete(
            @Valid @RequestBody BulkDeleteAlertRulesDTO request) {
        return Result.ok(alertService.bulkDeleteRules(request.getIds()));
    }

    private AlertRuleRequestDTO requireAlertRule(AlertRuleRequestDTO rule) {
        if (rule == null) {
            throw new BusinessException(400, "Alert rule request is required");
        }
        return rule;
    }

    private List<String> parseRuleIds(String ids) {
        if (ids.isBlank()) {
            throw new BusinessException(400, "Selected alert rule IDs must not be blank");
        }
        List<String> ruleIds = Arrays.stream(ids.split(",", -1))
                .map(String::trim)
                .toList();
        if (ruleIds.stream().anyMatch(String::isBlank)) {
            throw new BusinessException(400, "Selected alert rule IDs must not be blank");
        }
        return ruleIds;
    }
}
