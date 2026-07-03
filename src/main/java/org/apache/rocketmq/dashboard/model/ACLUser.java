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
package org.apache.rocketmq.dashboard.model;

import lombok.Data;
import java.util.Date;
import java.util.Set;

/**
 *
 */
@Data
public class ACLUser {

    /**
 *
     */
    private String userName;

    /**
     * AccessKey
     */
    private String accessKey;

    /**
 *
     */
    private String userType;

    /**
 *
     */
    private String status;

    /**
 *
     */
    private Date createTime;

    /**
 *
     */
    private Date updateTime;

    /**
 *
     */
    private Date lastLoginTime;

    /**
 *
     */
    private String namespace;

    /**
 *
     */
    private Set<String> policyIds;

    /**
 *
     */
    private Set<String> ipWhiteList;

    /**
 *
     */
    private String description;

    /**
 *
     */
    public boolean isActive() {
        return "ACTIVE".equals(status);
    }

    /**
 *
     */
    public boolean isAdmin() {
        return "ADMIN".equals(userType);
    }

    /**
 *
     */
    public boolean isIpAllowed(String ip) {
        if (ipWhiteList == null || ipWhiteList.isEmpty()) {
            return true; // Removed
        }
        return ipWhiteList.contains(ip);
    }
}