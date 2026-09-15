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
package org.apache.rocketmq.studio.ops.ai.tool.contract.acl;

import org.apache.rocketmq.studio.instance.acl.AclRuleVO;

import java.util.List;

public record AclRuleItem(
        Long id,
        String principal,
        String resource,
        String resourceType,
        String resourcePattern,
        List<String> actions,
        String decision,
        String scope,
        String aclVersion,
        String gmtCreate) {

    public static AclRuleItem from(AclRuleVO rule) {
        return new AclRuleItem(
                rule.getId(),
                rule.getPrincipal(),
                rule.getResource(),
                rule.getResourceType(),
                rule.getResourcePattern(),
                rule.getActions(),
                rule.getDecision(),
                rule.getScope(),
                rule.getAclVersion(),
                rule.getGmtCreate() == null ? null : rule.getGmtCreate().toString());
    }
}
