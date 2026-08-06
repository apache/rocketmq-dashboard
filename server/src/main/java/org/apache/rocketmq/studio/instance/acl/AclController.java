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
package org.apache.rocketmq.studio.instance.acl;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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
    public Result<AclRuleVO> createRule(@Valid @RequestBody(required = false) CreateAclRuleDTO rule) {
        return Result.ok(aclService.createRule(requireRequest(rule, "ACL rule request is required").toAclRuleVO()));
    }

    @PostMapping("/rules/update")
    public Result<AclRuleVO> updateRule(@Valid @RequestBody(required = false) UpdateAclRuleDTO rule) {
        return Result.ok(aclService.updateRule(requireRequest(rule, "ACL rule request is required").toAclRuleVO()));
    }

    @PostMapping("/rules/delete")
    public Result<Void> deleteRule(@Valid @RequestBody AclDeleteRequestDTO request) {
        aclService.deleteRule(request.getId());
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<List<AclUserVO>> listUsers() {
        return Result.ok(aclService.listUsers());
    }

    @GetMapping("/users/{id}/credentials")
    public Result<AclUserVO> getUserCredentials(@PathVariable String id) {
        return Result.ok(aclService.getUserCredentials(id));
    }

    @PostMapping("/users/create")
    public Result<AclUserVO> createUser(@Valid @RequestBody CreateAclUserDTO user) {
        return Result.ok(aclService.createUser(user.toAclUserVO()));
    }

    @PostMapping("/users/update")
    public Result<AclUserVO> updateUser(@Valid @RequestBody(required = false) UpdateAclUserDTO user) {
        return Result.ok(aclService.updateUser(requireRequest(user, "ACL user request is required")));
    }

    @PostMapping("/users/delete")
    public Result<Void> deleteUser(@Valid @RequestBody AclDeleteRequestDTO request) {
        aclService.deleteUser(request.getId());
        return Result.ok();
    }

    private <T> T requireRequest(T request, String message) {
        if (request == null) {
            throw new BusinessException(400, message);
        }
        return request;
    }
}
