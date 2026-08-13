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
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.util.CredentialUtils;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.model.Acl2PolicyContext;
import org.apache.rocketmq.studio.instance.InstanceRepository;
import org.apache.rocketmq.studio.instance.InstanceVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AclService {

    private final AclRepository aclRepository;
    private final OperationAuditService operationAuditService;
    private final InstanceRepository instanceRepository;

    public AclCapabilitiesVO capabilities(String instanceId) {
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400, "instanceId is required");
        }
        InstanceVO instance = instanceRepository.findByIdentifier(instanceId)
                .orElseThrow(() -> new BusinessException(404, "Instance not found: " + instanceId));
        boolean apacheInstance = instance.getVendor() == null || instance.getVendor() == InstanceVendor.APACHE;
        return new AclCapabilitiesVO(instance.getId(), instance.getVendor(), instance.getType(),
                apacheInstance ? "APACHE_ACL2" : "STUDIO_LOCAL", apacheInstance, false);
    }


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
        recordAudit("DELETE_ACL_RULE", "ACL_RULE", id, null, null);
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
        if (user.getUsername() != null && !StringUtils.hasText(user.getUsername())) {
            throw new BusinessException(400, "ACL username is required");
        }
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
        recordAudit("DELETE_ACL_USER", "ACL_USER", id, null, null);
    }

    /**
     * Returns a store-level summary of the ACL accounts provisioned for the cluster from the
     * MySQL-backed store. This does not query live broker state; the {@code clusterId} only
     * scopes which stored accounts are included.
     */
    public AclClusterConfigVO examineBrokerClusterAclConfig(String clusterId) {
        if (!StringUtils.hasText(clusterId)) {
            throw new BusinessException(400, "clusterId is required");
        }
        log.info("Examining broker cluster ACL config for clusterId={}", clusterId);
        return aclRepository.examineBrokerClusterAclConfig(clusterId);
    }

    /**
     * Creates a new plain access account or updates an existing one. The account identity is
     * persisted to {@code rmq_acl_user} (including the IP whitelist) and the per-resource
     * permissions to {@code rmq_acl_rule} via the MySQL-backed repository. The user row and the
     * rule replacement happen in one transaction; a blank secret on an existing account keeps
     * the stored secret unchanged.
     */
    public PlainAccessConfigVO createAndUpdatePlainAccessConfig(PlainAccessConfigVO config) {
        if (config == null || !StringUtils.hasText(config.getAccessKey())) {
            throw new BusinessException(400, "accessKey is required");
        }
        log.info("Creating/updating plain access config accessKey={}", config.getAccessKey());
        PlainAccessConfigVO saved = aclRepository.createAndUpdatePlainAccessConfig(config);
        String auditDetail = "admin=" + saved.isAdmin()
                + ", whiteRemoteAddressConfigured="
                + StringUtils.hasText(saved.getWhiteRemoteAddress());
        recordAudit("UPSERT_PLAIN_ACCESS_CONFIG", "ACL_USER", saved.getAccessKey(), null,
                auditDetail);
        return saved;
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

    /**
     * Validates an ACL 2.0 RBAC policy context so the model can be used by callers before it is
     * persisted. This makes the {@link org.apache.rocketmq.studio.model.Acl2PolicyContext} model
     * operational without duplicating the cluster-config endpoints owned by the separate ACL 2.0
     * functional change.
     *
     * <p>Checks: non-blank policy name, a valid binding type, a non-null rules list, and that every
     * IP whitelist entry is a well-formed range (validated through {@link IpRangeMatcher}).
     *
     * @param policy the ACL 2.0 policy to validate
     * @throws BusinessException with HTTP 400 when a required field is missing or malformed
     */
    public void validateAcl2Policy(Acl2PolicyContext policy) {
        if (policy == null) {
            throw new BusinessException(400, "ACL 2.0 policy is required");
        }
        if (!StringUtils.hasText(policy.getPolicyName())) {
            throw new BusinessException(400, "ACL 2.0 policyName is required");
        }
        if (!StringUtils.hasText(policy.getBoundType()) || !isValidAcl2BoundType(policy.getBoundType())) {
            throw new BusinessException(400,
                    "ACL 2.0 boundType must be one of TOPIC, GROUP, *, USER, SERVICE_ACCOUNT (got: "
                            + policy.getBoundType() + ")");
        }
        if (policy.getRules() == null) {
            throw new BusinessException(400, "ACL 2.0 policy rules are required");
        }
        if (policy.getWhiteSet() != null) {
            for (String entry : policy.getWhiteSet()) {
                if (!IpRangeMatcher.isValidRange(entry)) {
                    throw new BusinessException(400,
                            "ACL 2.0 whiteSet entry is not a valid IP/CIDR range: " + entry);
                }
            }
        }
    }

    private boolean isValidAcl2BoundType(String boundType) {
        return switch (boundType.trim().toUpperCase(Locale.ROOT)) {
            case "TOPIC", "GROUP", "*", "USER", "SERVICE_ACCOUNT" -> true;
            default -> false;
        };
    }

    private AclUserVO maskCredentials(AclUserVO user) {
        return AclUserVO.builder()
                .id(user.getId())
                .username(user.getUsername())
                .accessKey(CredentialUtils.mask(user.getAccessKey()))
                .secretKey(CredentialUtils.mask(user.getSecretKey()))
                .admin(user.isAdmin())
                .clusters(user.getClusters() == null ? null : List.copyOf(user.getClusters()))
                .whiteRemoteAddress(user.getWhiteRemoteAddress())
                .createdAt(user.getCreatedAt())
                .build();
    }

    private void auditRule(String operation, AclRuleVO rule) {
        recordAudit(operation, "ACL_RULE", rule.getId(), null,
                "principal=" + rule.getPrincipal());
    }

    private void auditUser(String operation, AclUserVO user) {
        recordAudit(operation, "ACL_USER", user.getId(), null,
                "username=" + user.getUsername() + ", admin=" + user.isAdmin());
    }


    private void recordAudit(String operation, String resourceType, String resourceName,
                             String clusterId, String detail) {
        try {
            operationAuditService.record(operation, resourceType, resourceName, clusterId, detail, "SUCCESS", null);
        } catch (Exception auditFailure) {
            log.warn("Failed to record audit operation={} resource={}: {}", operation, resourceName,
                    auditFailure.getMessage());
        }
    }

}
