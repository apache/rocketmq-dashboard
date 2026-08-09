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

import org.springframework.util.StringUtils;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AclService {

    private final AclRepository aclRepository;
    private final OperationAuditService operationAuditService;


    public List<AclRuleVO> listRules(String clusterId, String principal) {
        log.info("Listing ACL rules for clusterId={}, principal={}", clusterId, principal);
        return aclRepository.findRules(clusterId, principal);
    }


    public AclRuleVO createRule(AclRuleVO rule) {
        log.info("Creating ACL rule for principal={}", rule.getPrincipal());
        if (!StringUtils.hasText(rule.getPrincipal())) {
            throw new BusinessException(400, "ACL principal is required");
        }
        if (!StringUtils.hasText(rule.getResource())) {
            throw new BusinessException(400, "ACL resource is required");
        }
        rule.setId(UUID.randomUUID().toString());
        rule.setCreatedAt(LocalDateTime.now());
        AclRuleVO saved = aclRepository.saveRule(rule);
        auditRule("CREATE_ACL_RULE", saved);
        return saved;
    }

    public AclRuleVO updateRule(AclRuleVO rule) {
        if (!StringUtils.hasText(rule.getId())) {
            throw new BusinessException(400, "ACL rule id is required");
        }
        log.info("Updating ACL rule id={}, principal={}", rule.getId(), rule.getPrincipal());
        AclRuleVO saved = aclRepository.replaceRule(rule)
                .orElseThrow(() -> new BusinessException(404, "ACL rule not found: " + rule.getId()));
        auditRule("UPDATE_ACL_RULE", saved);
        return saved;
    }

    public void deleteRule(String id) {
        log.info("Deleting ACL rule id={}", id);
        if (!aclRepository.deleteRule(id)) {
            throw new BusinessException(404, "ACL rule not found: " + id);
        }
        operationAuditService.record("DELETE_ACL_RULE", "ACL_RULE", id, null, null, "SUCCESS", null);
    }


    public List<AclUserVO> listUsers() {
        log.info("Listing ACL users");
        return aclRepository.findUsers().stream()
                .map(this::maskCredentials)
                .toList();
    }


    public AclUserVO createUser(AclUserVO user) {
        log.info("Creating ACL user username={}", user.getUsername());
        if (!StringUtils.hasText(user.getUsername())) {
            throw new BusinessException(400, "ACL username is required");
        }
        user.setId(UUID.randomUUID().toString());
        user.setAccessKey(UUID.randomUUID().toString().replace("-", ""));
        user.setSecretKey(UUID.randomUUID().toString().replace("-", ""));
        user.setCreatedAt(LocalDateTime.now());
        AclUserVO saved = aclRepository.saveUser(user);
        auditUser("CREATE_ACL_USER", saved);
        return saved;
    }

    public AclUserVO updateUser(UpdateAclUserDTO user) {
        if (!StringUtils.hasText(user.getId())) {
            throw new BusinessException(400, "ACL user id is required");
        }
        log.info("Updating ACL user id={}, username={}", user.getId(), user.getUsername());
        AclUserVO existing = aclRepository.findUserById(user.getId())
                .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + user.getId()));
        AclUserVO merged = AclUserVO.builder()
                .id(existing.getId())
                .username(user.getUsername() == null ? existing.getUsername() : user.getUsername())
                .accessKey(existing.getAccessKey())
                .secretKey(existing.getSecretKey())
                .admin(user.getAdmin() == null ? existing.isAdmin() : user.getAdmin())
                .clusters(user.getClusters() == null ? existing.getClusters() : user.getClusters())
                .createdAt(existing.getCreatedAt())
                .build();
        AclUserVO saved = aclRepository.saveUser(merged);
        auditUser("UPDATE_ACL_USER", saved);
        return maskCredentials(saved);
    }

    public void deleteUser(String id) {
        log.info("Deleting ACL user id={}", id);
        if (!aclRepository.deleteUser(id)) {
            throw new BusinessException(404, "ACL user not found: " + id);
        }
        operationAuditService.record("DELETE_ACL_USER", "ACL_USER", id, null, null, "SUCCESS", null);
    }

    /**
     * Returns the plain-text credentials of a user for the "view password" action.
     * The secret is stored base64-encoded in the database and decoded here.
     */
    public AclUserVO getUserCredentials(String id) {
        if (!StringUtils.hasText(id)) {
            throw new BusinessException(400, "ACL user id is required");
        }
        log.info("Revealing credentials for ACL user id={}", id);
        return aclRepository.findUserById(id)
                .orElseThrow(() -> new BusinessException(404, "ACL user not found: " + id));
    }

    private AclUserVO maskCredentials(AclUserVO user) {
        return AclUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .accessKey(CredentialUtils.mask(user.getAccessKey()))
                .secretKey(CredentialUtils.mask(user.getSecretKey()))
                .admin(user.isAdmin())
                .clusters(user.getClusters() == null ? null : List.copyOf(user.getClusters()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void auditRule(String operation, AclRuleVO rule) {
        operationAuditService.record(operation, "ACL_RULE", rule.getId(), null,
                "principal=" + rule.getPrincipal(), "SUCCESS", null);
    }

    private void auditUser(String operation, AclUserVO user) {
        operationAuditService.record(operation, "ACL_USER", user.getId(), null,
                "username=" + user.getUsername() + ", admin=" + user.isAdmin(), "SUCCESS", null);
    }

}
