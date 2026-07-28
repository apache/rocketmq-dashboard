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
 * distributed according to the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.model;

import java.util.Date;
import java.util.Set;

public class ACLUser {

    private String userName;

    /**
     * AccessKey
     */
    private String accessKey;

    private String userType;

    private String status;

    private Date createTime;

    private Date updateTime;

    private Date lastLoginTime;

    private Set<String> policyIds;

    private Set<String> ipWhiteList;

    private String description;

    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    public boolean isAdmin() {
        return "ADMIN".equals(userType);
    }

    public boolean isIpAllowed(String ip) {
        if (ipWhiteList == null || ipWhiteList.isEmpty()) {
            return true;
        }
        return ipWhiteList.contains(ip);
    }
    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Date getLastLoginTime() {
        return lastLoginTime;
    }

    public void setLastLoginTime(Date lastLoginTime) {
        this.lastLoginTime = lastLoginTime;
    }

    public Set<String> getPolicyIds() {
        return policyIds;
    }

    public void setPolicyIds(Set<String> policyIds) {
        this.policyIds = policyIds;
    }

    public Set<String> getIpWhiteList() {
        return ipWhiteList;
    }

    public void setIpWhiteList(Set<String> ipWhiteList) {
        this.ipWhiteList = ipWhiteList;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

}