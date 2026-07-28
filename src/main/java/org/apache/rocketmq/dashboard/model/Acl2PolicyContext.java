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
package org.apache.rocketmq.studio.model;


import java.util.Date;
import java.util.List;

/**
 * ACL 2.0 Policy Context - DTO for passing enhanced ACL 2.0 policy data between Controller and Service layers.
 *
 * This model extends ACL 1.0 compatible fields with ACL 2.0 RBAC-specific extensions including:
 * - Policy name as unique identifier
 * - Binding type (USER / GROUP / SERVICE_ACCOUNT)
 * - Fine-grained authorization rules with resource patterns and effects
 *
 * @see ACLPolicy
 */
public class Acl2PolicyContext {

    // ========== Base Fields (Compatible with ACL 1.0) ==========

    /** Access key (user identifier) */
    private String accessKey;

    /** Secret key for authentication */
    private String secretKey;

    /** Whether this is an admin user */
    private boolean isAdmin;

    /** IP whitelist patterns */
    private List<String> whiteSet;

    // ========== ACL 2.0 RBAC Extended Fields ==========

    /** Policy name (unique identifier for ACL 2.0 policies) */
    private String policyName;

    /** Binding type: USER / GROUP / SERVICE_ACCOUNT */
    private String boundType;

    /** Bound entity ID (e.g., username, group name) */
    private String boundEntityId;

    /** List of authorization rules */
    private List<AuthorizationRule> rules;

    /** Whether this policy is enabled */
    private boolean enabled;

    /** Policy description */
    private String description;

    /** Cluster scope for this policy */
    private String clusterName;

    /** Broker scope for this policy */
    private String brokerName;

    /** Metadata for tracking creation/update */
    private Date createTime;

    /** Last update time */
    private Date updateTime;

    /**
     * Authorization Rule - defines what actions are allowed/denied on which resources.
     * Supports wildcards in resource patterns following path pattern syntax.
     */
        public static class AuthorizationRule {

        /** Resource path pattern supporting wildcards (* matches any single level, ** matches all levels) */
        private String resourcePattern;

        /** Allowed operations: READ / WRITE / ADMIN / DELETE / UPDATE / CREATE / SUBSCRIBE / CONSUME */
        private List<String> actions;

        /** Effect: Allow or Deny (default is Allow when not specified) */
        private String effect;

        /** Priority: lower values are matched first. Default is 100. */
        private int priority;

        /** Optional rule description */
        private String description;

        /** Create a default Allow rule for backward compatibility */
        public static AuthorizationRule defaultAllowRule(String resourcePattern) {
            AuthorizationRule rule = new AuthorizationRule();
            rule.setResourcePattern(resourcePattern);
            rule.setActions(List.of("READ", "WRITE"));
            rule.setEffect("Allow");
            rule.setPriority(100);
            return rule;
        }

        /** Create a deny-all rule */
        public static AuthorizationRule denyAllRule() {
            AuthorizationRule rule = new AuthorizationRule();
            rule.setResourcePattern("**");
            rule.setActions(List.of("*"));
            rule.setEffect("Deny");
            rule.setPriority(0);
            return rule;
        }
    }

    /**
     * Validate this policy context for consistency.
     *
     * @throws IllegalArgumentException if required fields are missing
     */
    public void validate() {
        if (accessKey == null || accessKey.trim().isEmpty()) {
            throw new IllegalArgumentException("accessKey cannot be empty");
        }
        if (policyName == null || policyName.trim().isEmpty()) {
            throw new IllegalArgumentException("policyName cannot be empty");
        }
        if (rules != null && rules.isEmpty()) {
            throw new IllegalArgumentException("rules list must not be empty");
        }
        if (rules != null) {
            for (int i = 0; i < rules.size(); i++) {
                AuthorizationRule rule = rules.get(i);
                if (rule.getResourcePattern() == null || rule.getResourcePattern().trim().isEmpty()) {
                    throw new IllegalArgumentException(
                        String.format("rules[%d].resourcePattern cannot be empty", i));
                }
                if (rule.getActions() == null || rule.getActions().isEmpty()) {
                    throw new IllegalArgumentException(
                        String.format("rules[%d].actions cannot be empty", i));
                }
                String effect = rule.getEffect();
                if (effect != null && !"Allow".equalsIgnoreCase(effect) && !"Deny".equalsIgnoreCase(effect)) {
                    throw new IllegalArgumentException(
                        String.format("rules[%d].effect must be 'Allow' or 'Deny', got: %s", i, effect));
                }
                if (effect == null) {
                    rule.setEffect("Allow");
                }
            }
        }
        if (boundType != null && !isValidBoundType(boundType)) {
            throw new IllegalArgumentException(
                String.format("boundType must be USER, GROUP, or SERVICE_ACCOUNT, got: %s", boundType));
        }
    }

    private boolean isValidBoundType(String type) {
        return "USER".equals(type) || "GROUP".equals(type) || "SERVICE_ACCOUNT".equals(type);
    }
    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public boolean isIsAdmin() {
        return isAdmin;
    }

    public void setIsAdmin(boolean isAdmin) {
        this.isAdmin = isAdmin;
    }

    public List<String> getWhiteSet() {
        return whiteSet;
    }

    public void setWhiteSet(List<String> whiteSet) {
        this.whiteSet = whiteSet;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getBoundType() {
        return boundType;
    }

    public void setBoundType(String boundType) {
        this.boundType = boundType;
    }

    public String getBoundEntityId() {
        return boundEntityId;
    }

    public void setBoundEntityId(String boundEntityId) {
        this.boundEntityId = boundEntityId;
    }

    public List<AuthorizationRule> getRules() {
        return rules;
    }

    public void setRules(List<AuthorizationRule> rules) {
        this.rules = rules;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getClusterName() {
        return clusterName;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public String getBrokerName() {
        return brokerName;
    }

    public void setBrokerName(String brokerName) {
        this.brokerName = brokerName;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

}
