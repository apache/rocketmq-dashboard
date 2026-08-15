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

import org.apache.rocketmq.remoting.protocol.body.AclInfo;

import java.util.List;
import java.util.Objects;

/** REST representation of an Apache ACL 2.0 policy. */
public record RemoteAclPolicyVO(String subject, List<PolicyGroupVO> policies) {

    public static RemoteAclPolicyVO from(AclInfo policy) {
        List<PolicyGroupVO> groups = policy.getPolicies() == null ? List.of() : policy.getPolicies().stream()
                .filter(Objects::nonNull)
                .map(PolicyGroupVO::from)
                .toList();
        return new RemoteAclPolicyVO(policy.getSubject(), groups);
    }

    public record PolicyGroupVO(String policyType, List<PolicyEntryVO> entries) {
        private static PolicyGroupVO from(AclInfo.PolicyInfo policy) {
            List<PolicyEntryVO> policyEntries = policy.getEntries() == null ? List.of() : policy.getEntries().stream()
                    .filter(Objects::nonNull)
                    .map(PolicyEntryVO::from)
                    .toList();
            return new PolicyGroupVO(policy.getPolicyType(), policyEntries);
        }
    }

    public record PolicyEntryVO(String resource, List<String> actions, List<String> sourceIps, String decision) {
        private static PolicyEntryVO from(AclInfo.PolicyEntryInfo entry) {
            return new PolicyEntryVO(entry.getResource(), listOrEmpty(entry.getActions()),
                    listOrEmpty(entry.getSourceIps()), entry.getDecision());
        }

        private static List<String> listOrEmpty(List<String> values) {
            return values == null ? List.of() : values.stream()
                    .filter(Objects::nonNull)
                    .toList();
        }
    }
}
