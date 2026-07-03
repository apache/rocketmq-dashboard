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
package org.apache.rocketmq.dashboard.model;

import lombok.Data;
import java.util.Date;
import java.util.Set;

/**
 *
 */
@Data
public class ACLPolicy {

    /**
 *
     */
    private String policyId;

    /**
 *
     */
    private String policyName;

    /**
 *
     */
    private String description;

    /**
 *
     */
    private Set<String> users;

    /**
 *
     */
    private Set<String> resources;

    /**
 *
     */
    private Set<String> actions;

    /**
 *
     */
    private String policyType;

    /**
 *
     */
    private String namespace;

    /**
 *
     */
    private Set<String> ipWhiteList;

    /**
 *
     */
    private Date effectiveTime;

    /**
 *
     */
    private Date expirationTime;

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
    private String status;

    /**
 *
     */
    private Boolean defaultPolicy;

    /**
 *
     */
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

    /**
 *
     */
    public boolean hasPermission(String user, String resource, String action) {
        if (!isEffective()) {
            return false;
        }

        // Removed
        if (users != null && !users.isEmpty() && !users.contains(user)) {
            return false;
        }

        // Removed
        if (resources != null && !resources.isEmpty() && !resources.contains(resource)) {
            return false;
        }

        // Removed
        if (actions != null && !actions.contains(action)) {
            return false;
        }

        return "ALLOW".equals(policyType);
    }
}