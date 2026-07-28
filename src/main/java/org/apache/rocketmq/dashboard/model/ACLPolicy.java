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
import java.util.Set;

public class ACLPolicy {

    private String policyId;

    private String policyName;

    private String description;

    private Set<String> users;

    private Set<String> resources;

    private Set<String> actions;

    private String policyType;

    private Set<String> ipWhiteList;

    private Date effectiveTime;

    private Date expirationTime;

    private Date createTime;

    private Date updateTime;

    private String status;

    private Boolean defaultPolicy;

    public boolean isEffective() {
        Date now = new Date();
        if (effectiveTime != null && now.before(effectiveTime)) {
            return false;
        }
        if (expirationTime != null && now.after(expirationTime)) {
            return false;
        }
        if (!"ACTIVE".equals(status)) {
            return false;
        }
        return true;
    }

    public boolean hasPermission(String user, String resource, String action) {
        if (!isEffective()) {
            return false;
        }

        if (users != null && !users.isEmpty() && !users.contains(user)) {
            return false;
        }

        if (resources != null && !resources.isEmpty() && !resources.contains(resource)) {
            return false;
        }

        if (actions != null && !actions.contains(action)) {
            return false;
        }

        return "ALLOW".equals(policyType);
    }
    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getPolicyName() {
        return policyName;
    }

    public void setPolicyName(String policyName) {
        this.policyName = policyName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Set<String> getUsers() {
        return users;
    }

    public void setUsers(Set<String> users) {
        this.users = users;
    }

    public Set<String> getResources() {
        return resources;
    }

    public void setResources(Set<String> resources) {
        this.resources = resources;
    }

    public Set<String> getActions() {
        return actions;
    }

    public void setActions(Set<String> actions) {
        this.actions = actions;
    }

    public String getPolicyType() {
        return policyType;
    }

    public void setPolicyType(String policyType) {
        this.policyType = policyType;
    }

    public Set<String> getIpWhiteList() {
        return ipWhiteList;
    }

    public void setIpWhiteList(Set<String> ipWhiteList) {
        this.ipWhiteList = ipWhiteList;
    }

    public Date getEffectiveTime() {
        return effectiveTime;
    }

    public void setEffectiveTime(Date effectiveTime) {
        this.effectiveTime = effectiveTime;
    }

    public Date getExpirationTime() {
        return expirationTime;
    }

    public void setExpirationTime(Date expirationTime) {
        this.expirationTime = expirationTime;
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

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getDefaultPolicy() {
        return defaultPolicy;
    }

    public void setDefaultPolicy(Boolean defaultPolicy) {
        this.defaultPolicy = defaultPolicy;
    }

}