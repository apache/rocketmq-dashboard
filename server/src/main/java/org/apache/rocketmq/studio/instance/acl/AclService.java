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

import org.apache.rocketmq.studio.common.exception.BusinessException;
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

    private static final int VISIBLE_CREDENTIAL_CHARS = 4;
    private static final int MIN_PARTIALLY_MASKED_CREDENTIAL_CHARS = 17;
    private static final String CREDENTIAL_MASK = "****";

    private final AclRepository aclRepository;


    public List<AclRuleVO> listRules(String clusterId, String principal) {
        log.info("Listing ACL rules for clusterId={}, principal={}", clusterId, principal);
        return aclRepository.findRules(clusterId, principal);
    }


    public AclRuleVO createRule(AclRuleVO rule) {
        log.info("Creating ACL rule for principal={}", rule.getPrincipal());
        if (isBlank(rule.getPrincipal())) {
            throw new BusinessException(400, "ACL principal is required");
        }
        if (isBlank(rule.getResource())) {
            throw new BusinessException(400, "ACL resource is required");
        }
        rule.setId(UUID.randomUUID().toString());
        rule.setCreatedAt(LocalDateTime.now());
        return aclRepository.saveRule(rule);
    }

    public AclRuleVO updateRule(AclRuleVO rule) {
        if (isBlank(rule.getId())) {
            throw new BusinessException(400, "ACL rule id is required");
        }
        log.info("Updating ACL rule id={}, principal={}", rule.getId(), rule.getPrincipal());
        if (rule.getCreatedAt() == null) {
            rule.setCreatedAt(LocalDateTime.now());
        }
        return aclRepository.saveRule(rule);
    }

    public void deleteRule(String id) {
        log.info("Deleting ACL rule id={}", id);
        aclRepository.deleteRule(id);
    }


    public List<AclUserVO> listUsers() {
        log.info("Listing ACL users");
        return aclRepository.findUsers().stream()
                .map(this::maskCredentials)
                .toList();
    }


    public AclUserVO createUser(AclUserVO user) {
        log.info("Creating ACL user username={}", user.getUsername());
        if (isBlank(user.getUsername())) {
            throw new BusinessException(400, "ACL username is required");
        }
        user.setId(UUID.randomUUID().toString());
        user.setAccessKey(UUID.randomUUID().toString().replace("-", ""));
        user.setSecretKey(UUID.randomUUID().toString().replace("-", ""));
        user.setCreatedAt(LocalDateTime.now());
        return aclRepository.saveUser(user);
    }

    public AclUserVO updateUser(AclUserVO user) {
        if (isBlank(user.getId())) {
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
                .admin(user.isAdmin())
                .clusters(user.getClusters() == null ? existing.getClusters() : user.getClusters())
                .createdAt(existing.getCreatedAt())
                .build();
        return maskCredentials(aclRepository.saveUser(merged));
    }

    public void deleteUser(String id) {
        log.info("Deleting ACL user id={}", id);
        aclRepository.deleteUser(id);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private AclUserVO maskCredentials(AclUserVO user) {
        return AclUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .accessKey(maskCredential(user.getAccessKey()))
                .secretKey(maskCredential(user.getSecretKey()))
                .admin(user.isAdmin())
                .clusters(user.getClusters() == null ? null : List.copyOf(user.getClusters()))
                .createdAt(user.getCreatedAt())
                .build();
    }

    private String maskCredential(String credential) {
        if (credential == null || credential.isEmpty()) {
            return credential;
        }
        if (credential.length() < MIN_PARTIALLY_MASKED_CREDENTIAL_CHARS) {
            return CREDENTIAL_MASK;
        }
        return credential.substring(0, VISIBLE_CREDENTIAL_CHARS)
                + CREDENTIAL_MASK
                + credential.substring(credential.length() - VISIBLE_CREDENTIAL_CHARS);
    }
}
