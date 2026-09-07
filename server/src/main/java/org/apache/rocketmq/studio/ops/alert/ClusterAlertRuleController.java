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

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Domain-limited rule API for the Cluster Alerts menu. */
@RestController
@RequestMapping("/api/cluster-alert-rules")
@RequiredArgsConstructor
public class ClusterAlertRuleController {

    private final AlertService alertService;
    private final NativeAlertRuleTestService nativeAlertRuleTestService;
    private final NativeAlertMetricCatalogService metricCatalogService;
    private final AlertRuleTransferService transferService;

    @GetMapping
    public Result<List<AlertRuleVO>> listRules() {
        return Result.ok(alertService.listRules(AlertDomain.CLUSTER));
    }

    @GetMapping("/page")
    public Result<PageResult<AlertRuleVO>> listRulesPage(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.ok(alertService.listRules(AlertDomain.CLUSTER, search, enabled, page, pageSize));
    }

    @GetMapping("/runtime")
    public Result<List<AlertRuleRuntimeVO>> listRuntime() {
        return Result.ok(alertService.listRuleRuntime(AlertDomain.CLUSTER));
    }

    @GetMapping("/transfer")
    public Result<AlertRuleTransferDTO> exportTransfer() {
        return Result.ok(transferService.exportRules(AlertDomain.CLUSTER));
    }

    @PostMapping("/import")
    public Result<List<AlertRuleVO>> importRules(@Valid @RequestBody(required = false) AlertRuleTransferDTO transfer) {
        return Result.ok(transferService.importRules(AlertDomain.CLUSTER, transfer));
    }

    @PostMapping("/create")
    public Result<AlertRuleVO> createRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleVO candidate = requireRule(rule).toAlertRuleVO();
        candidate.setDomain(AlertDomain.CLUSTER);
        metricCatalogService.validate(candidate);
        return Result.ok(alertService.createRule(AlertDomain.CLUSTER, candidate));
    }

    @PostMapping("/update")
    public Result<AlertRuleVO> updateRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleRequestDTO request = requireRule(rule);
        if (request.getId() == null) {
            throw new BusinessException(400, "id is required");
        }
        AlertRuleVO candidate = request.toAlertRuleVO();
        candidate.setDomain(AlertDomain.CLUSTER);
        metricCatalogService.validate(candidate);
        return Result.ok(alertService.updateRule(AlertDomain.CLUSTER, candidate));
    }

    @PostMapping("/test")
    public Result<AlertRuleTestResultVO> testRule(@Valid @RequestBody(required = false) AlertRuleRequestDTO rule) {
        AlertRuleVO candidate = requireRule(rule).toAlertRuleVO();
        candidate.setDomain(AlertDomain.CLUSTER);
        metricCatalogService.validate(candidate);
        return Result.ok(nativeAlertRuleTestService.test(candidate));
    }

    @PostMapping("/toggle")
    public Result<AlertRuleVO> toggleRule(@Valid @RequestBody ToggleAlertRuleDTO request) {
        return Result.ok(alertService.toggleRule(AlertDomain.CLUSTER, request.getId(), request.getEnabled()));
    }

    @PostMapping("/delete")
    public Result<Void> deleteRule(@Valid @RequestBody DeleteAlertRuleDTO request) {
        alertService.deleteRule(AlertDomain.CLUSTER, request.getId());
        return Result.ok();
    }

    @PostMapping("/bulk-toggle")
    public Result<AlertRuleBulkResultVO> bulkToggle(@Valid @RequestBody BulkToggleAlertRulesDTO request) {
        return Result.ok(alertService.bulkToggleRules(AlertDomain.CLUSTER, request.getIds(), request.getEnabled()));
    }

    @PostMapping("/bulk-delete")
    public Result<AlertRuleBulkResultVO> bulkDelete(@Valid @RequestBody BulkDeleteAlertRulesDTO request) {
        return Result.ok(alertService.bulkDeleteRules(AlertDomain.CLUSTER, request.getIds()));
    }

    private AlertRuleRequestDTO requireRule(AlertRuleRequestDTO rule) {
        if (rule == null) {
            throw new BusinessException(400, "Alert rule request is required");
        }
        return rule;
    }
}
