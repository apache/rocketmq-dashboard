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

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;

@Data
public class CreateAclRuleDTO {
    @NotBlank(message = "principal is required")
    private String principal;
    @NotBlank(message = "resource is required")
    private String resource;
    private String resourceType;
    private String resourcePattern;
    private List<String> actions;
    private String decision;
    private String scope;
    private String aclVersion;
    /** Instance id used to route the operation to a cloud-vendor ACL backend. */
    private String instanceId;

    public AclRuleVO toAclRuleVO() {
        return AclRuleVO.builder()
                .principal(principal)
                .resource(resource)
                .resourceType(resourceType)
                .resourcePattern(resourcePattern)
                .actions(actions)
                .decision(decision)
                .scope(scope)
                .aclVersion(aclVersion)
                .build();
    }
}
