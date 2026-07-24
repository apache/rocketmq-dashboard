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
package com.rocketmq.studio.instance.acl;

import com.rocketmq.studio.common.domain.Result;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/acl")
@RequiredArgsConstructor
public class AclController {

    private final AclService aclService;

    @GetMapping("/rules")
    public Result<List<AclRuleVO>> listRules(
            @RequestParam(required = false) String clusterId,
            @RequestParam(required = false) String principal) {
        return Result.ok(aclService.listRules(clusterId, principal));
    }

    @PostMapping("/rules/create")
    public Result<AclRuleVO> createRule(@RequestBody AclRuleVO rule) {
        return Result.ok(aclService.createRule(rule));
    }

    @PostMapping("/rules/update")
    public Result<AclRuleVO> updateRule(@RequestBody AclRuleVO rule) {
        return Result.ok(aclService.updateRule(rule));
    }

    @PostMapping("/rules/delete")
    public Result<Void> deleteRule(@RequestBody Map<String, String> request) {
        aclService.deleteRule(request.get("id"));
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<List<AclUserVO>> listUsers() {
        return Result.ok(aclService.listUsers());
    }

    @PostMapping("/users/create")
    public Result<AclUserVO> createUser(@RequestBody AclUserVO user) {
        return Result.ok(aclService.createUser(user));
    }

    @PostMapping("/users/update")
    public Result<AclUserVO> updateUser(@RequestBody AclUserVO user) {
        return Result.ok(aclService.updateUser(user));
    }

    @PostMapping("/users/delete")
    public Result<Void> deleteUser(@RequestBody Map<String, String> request) {
        aclService.deleteUser(request.get("id"));
        return Result.ok();
    }
}
