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
import jakarta.servlet.http.HttpServletRequest;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/alert-rules", "/api/business-alert-rules"})
@RequiredArgsConstructor
public class AlertRuleController {

    private final AlertService alertService;
    private final NativeAlertRuleTestService nativeAlertRuleTestService;
    private final NativeAlertMetricCatalogService metricCatalogService;
    private final AlertRuleTransferService transferService;

    @GetMapping
    public Result<List<AlertRuleVO>> listRules(HttpServletRequest request) {
        // Keep the original read endpoint complete for existing API clients while the
        // domain-specific route powers the Business Alerts page.
        if (request.getRequestURI().endsWith("/api/alert-rules")) {
            return Result.ok(alertService.listRules());
        }
        return Result.ok(alertService.listRules(AlertDomain.BUSINESS));
    }

    @GetMapping("/page")
    public Result<PageResult<AlertRuleVO>> listRulesPage(
            HttpServletRequest request,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        // The legacy route keeps the cross-domain pagination contract; the business route
        // restricts the page to business rules.
        if (request.getRequestURI().endsWith("/api/alert-rules/page")) {
            return Result.ok(alertService.listRules(search, enabled, page, pageSize));
        }
        return Result.ok(alertService.listRules(AlertDomain.BUSINESS, search, enabled, page, pageSize));
    }

    @GetMapping("/runtime")
    public Result<List<AlertRuleRuntimeVO>> listRuntime() {
        return Result.ok(alertService.listRuleRuntime(AlertDomain.BUSINESS));
    }

    @GetMapping("/export")
    public Result<AlertRulesYamlVO> exportRules() {
        return Result.ok(new AlertRulesYamlVO(alertService.exportPrometheusRulesYaml()));
    }

    @GetMapping("/transfer")
    public Result<AlertRuleTransferDTO> exportTransfer() {
        return Result.ok(transferService.exportRules(AlertDomain.BUSINESS));
    }

    @PostMapping("/import")
    public Result<List<AlertRuleVO>> importRules(@Valid @RequestBody(required = false) AlertRuleTransferDTO transfer) {
        return Result.ok(transferService.importRules(AlertDomain.BUSINESS, transfer));
    }

    @PostMapping("/create")
    public Result<AlertRuleVO> createRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleVO candidate = requireAlertRule(rule).toAlertRuleVO();
        candidate.setDomain(AlertDomain.BUSINESS);
        metricCatalogService.validate(candidate);
        return Result.ok(alertService.createRule(AlertDomain.BUSINESS, candidate));
    }

    @PostMapping("/update")
    public Result<AlertRuleVO> updateRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleRequestDTO request = requireAlertRule(rule);
        if (request.getId() == null) {
            throw new BusinessException(400, "id is required");
        }
        AlertRuleVO candidate = request.toAlertRuleVO();
        candidate.setDomain(AlertDomain.BUSINESS);
        metricCatalogService.validate(candidate);
        return Result.ok(alertService.updateRule(AlertDomain.BUSINESS, candidate));
    }

    @PostMapping("/test")
    public Result<AlertRuleTestResultVO> testRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleVO candidate = requireAlertRule(rule).toAlertRuleVO();
        candidate.setDomain(AlertDomain.BUSINESS);
        metricCatalogService.validate(candidate);
        return Result.ok(nativeAlertRuleTestService.test(candidate));
    }

    @PostMapping("/toggle")
    public Result<AlertRuleVO> toggleRule(@Valid @RequestBody ToggleAlertRuleDTO request) {
        return Result.ok(alertService.toggleRule(AlertDomain.BUSINESS, request.getId(), request.getEnabled()));
    }

    @PostMapping("/delete")
    public Result<Void> deleteRule(@Valid @RequestBody DeleteAlertRuleDTO request) {
        alertService.deleteRule(AlertDomain.BUSINESS, request.getId());
        return Result.ok();
    }

    @PostMapping("/bulk-toggle")
    public Result<AlertRuleBulkResultVO> bulkToggle(
            @Valid @RequestBody BulkToggleAlertRulesDTO request) {
        return Result.ok(alertService.bulkToggleRules(AlertDomain.BUSINESS, request.getIds(), request.getEnabled()));
    }

    @PostMapping("/bulk-delete")
    public Result<AlertRuleBulkResultVO> bulkDelete(
            @Valid @RequestBody BulkDeleteAlertRulesDTO request) {
        return Result.ok(alertService.bulkDeleteRules(AlertDomain.BUSINESS, request.getIds()));
    }

    private AlertRuleRequestDTO requireAlertRule(AlertRuleRequestDTO rule) {
        if (rule == null) {
            throw new BusinessException(400, "Alert rule request is required");
        }
        return rule;
    }
}
