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
package org.apache.rocketmq.dashboard.controller;

import jakarta.annotation.Resource;
import org.apache.rocketmq.dashboard.model.AlertRuleVO;
import org.apache.rocketmq.dashboard.service.AlertRuleService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alert rule CRUD endpoints, aligned with the frontend remoteApi calls:
 * <table border="1">
 *   <tr><th>Method</th><th>Path</th><th>Description</th></tr>
 *   <tr><td>GET</td><td>/api/alert/rules</td><td>List all alert rules</td></tr>
 *   <tr><td>POST</td><td>/api/alert/rules</td><td>Create an alert rule</td></tr>
 *   <tr><td>PUT</td><td>/api/alert/rules/{id}</td><td>Update an alert rule</td></tr>
 *   <tr><td>POST</td><td>/api/alert/rules/{id}/enable</td><td>Enable/disable an alert rule</td></tr>
 *   <tr><td>DELETE</td><td>/api/alert/rules/{id}</td><td>Delete an alert rule</td></tr>
 * </table>
 * Responses are wrapped into {@code {status, data, errMsg}} by
 * {@link org.apache.rocketmq.dashboard.support.GlobalRestfulResponseBodyAdvice}.
 */
@RestController
@RequestMapping("/api/alert/rules")
public class AlertRuleController {

    @Resource
    private AlertRuleService alertRuleService;

    @GetMapping
    public Object listRules() {
        return alertRuleService.listRules();
    }

    @PostMapping
    public Object createRule(@RequestBody AlertRuleVO rule) {
        return alertRuleService.createRule(rule);
    }

    @PutMapping("/{id}")
    public Object updateRule(@PathVariable String id, @RequestBody AlertRuleVO rule) {
        rule.setId(id);
        return alertRuleService.updateRule(rule);
    }

    @PostMapping("/{id}/enable")
    public Object toggleRule(@PathVariable String id, @RequestParam boolean enabled) {
        return alertRuleService.toggleRule(id, enabled);
    }

    @DeleteMapping("/{id}")
    public Object deleteRule(@PathVariable String id) {
        alertRuleService.deleteRule(id);
        return true;
    }
}
