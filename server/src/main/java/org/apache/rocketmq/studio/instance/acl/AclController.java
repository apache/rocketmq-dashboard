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

import org.apache.rocketmq.studio.common.domain.DeleteRequestDTO;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
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
    private final ApacheAclReadService apacheAclReadService;

    @GetMapping("/remote/rules")
    public Result<RemoteAclReadResult> listRemoteRules(
            @RequestParam String instanceId,
            @RequestParam(required = false) String subject,
            @RequestParam(required = false) String resource) {
        return Result.ok(apacheAclReadService.listRules(instanceId, subject, resource));
    }

    @GetMapping("/capabilities")
    public Result<AclCapabilitiesVO> capabilities(@RequestParam String instanceId) {
        return Result.ok(aclService.capabilities(instanceId));
    }

    @GetMapping("/rules")
    public Result<PageResult<AclRuleVO>> listRules(
            @RequestParam(required = false) String principal,
            @RequestParam(required = false) String resource,
            @RequestParam(required = false) String scope,
            @RequestParam(required = false) String decision,
            @RequestParam(required = false) String aclVersion,
            @RequestParam(required = false) String instanceId,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "20") Integer pageSize) {
        return Result.ok(aclService.listRules(principal, resource, scope, decision, aclVersion,
                instanceId, page, pageSize));
    }

    @PostMapping("/rules/create")
    public Result<AclRuleVO> createRule(@Valid @RequestBody(required = false) CreateAclRuleDTO rule) {
        CreateAclRuleDTO request = requireRequest(rule, "ACL rule request is required");
        return Result.ok(aclService.createRule(request.toAclRuleVO(), request.getInstanceId()));
    }

    @PostMapping("/rules/update")
    public Result<AclRuleVO> updateRule(@Valid @RequestBody(required = false) UpdateAclRuleDTO rule) {
        UpdateAclRuleDTO request = requireRequest(rule, "ACL rule request is required");
        return Result.ok(aclService.updateRule(request.toAclRuleVO(), request.getInstanceId()));
    }

    @PostMapping("/rules/delete")
    public Result<Void> deleteRule(@Valid @RequestBody DeleteRequestDTO request) {
        aclService.deleteRule(request.getId(), request.getInstanceId());
        return Result.ok();
    }

    @GetMapping("/users")
    public Result<List<AclUserVO>> listUsers(
            @RequestParam(required = false) String instanceId) {
        return Result.ok(aclService.listUsers(instanceId));
    }

    @GetMapping("/users/page")
    public Result<PageResult<AclUserVO>> pageUsers(
            @RequestParam(required = false) String instanceId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.ok(aclService.pageUsers(instanceId, page, pageSize, keyword));
    }

    @GetMapping("/users/{id}/credentials")
    public ResponseEntity<Result<AclUserVO>> getUserCredentials(@PathVariable String id,
            @RequestParam(required = false) String instanceId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(Result.ok(aclService.getUserCredentials(id, instanceId)));
    }

    @PostMapping("/users/create")
    public Result<AclUserVO> createUser(@Valid @RequestBody CreateAclUserDTO user) {
        return Result.ok(aclService.createUser(user.toAclUserVO(), user.getInstanceId()));
    }

    @PostMapping("/users/update")
    public Result<AclUserVO> updateUser(@Valid @RequestBody(required = false) UpdateAclUserDTO user) {
        UpdateAclUserDTO request = requireRequest(user, "ACL user request is required");
        return Result.ok(aclService.updateUser(request, request.getInstanceId()));
    }

    @PostMapping("/users/delete")
    public Result<Void> deleteUser(@Valid @RequestBody DeleteRequestDTO request) {
        aclService.deleteUser(request.getId(), request.getInstanceId());
        return Result.ok();
    }

    /**
     * Returns a store-level summary of the ACL accounts provisioned for the given cluster. The
     * {@code clusterId} scopes which stored accounts are included; this endpoint does not query
     * live broker state, so {@code aclVersion} / {@code aclEnabled} describe the dashboard store,
     * not broker runtime configuration.
     */
    @GetMapping("/cluster-config")
    public Result<AclClusterConfigVO> examineBrokerClusterAclConfig(
            @RequestParam(required = false) String clusterId) {
        return Result.ok(aclService.examineBrokerClusterAclConfig(clusterId));
    }

    @PostMapping("/plain-access-config")
    public Result<PlainAccessConfigVO> createAndUpdatePlainAccessConfig(
            @Valid @RequestBody UpsertPlainAccessConfigDTO request) {
        return Result.ok(aclService.createAndUpdatePlainAccessConfig(request.toPlainAccessConfigVO()));
    }

    private <T> T requireRequest(T request, String message) {
        if (request == null) {
            throw new BusinessException(400, message);
        }
        return request;
    }
}
