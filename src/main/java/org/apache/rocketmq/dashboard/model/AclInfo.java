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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class AclInfo {

    private String subject;
    private List<PolicyInfo> policies;

    public static AclInfo of(String subject, List<String> resources, List<String> actions,
                             List<String> sourceIps,
                             String decision) {
        AclInfo aclInfo = new AclInfo();
        aclInfo.setSubject(subject);
        PolicyInfo policyInfo = PolicyInfo.of(resources, actions, sourceIps, decision);
        aclInfo.setPolicies(Collections.singletonList(policyInfo));
        return aclInfo;
    }

    public static class PolicyInfo {

        private String policyType;
        private List<PolicyEntryInfo> entries;

        public static PolicyInfo of(List<String> resources, List<String> actions,
                                    List<String> sourceIps, String decision) {
            PolicyInfo policyInfo = new PolicyInfo();
            List<PolicyEntryInfo> entries = resources.stream()
                    .map(resource -> PolicyEntryInfo.of(resource, actions, sourceIps, decision))
                    .collect(Collectors.toList());
            policyInfo.setEntries(entries);
            return policyInfo;
        }

        public String getPolicyType() {
            return policyType;
        }

        public void setPolicyType(String policyType) {
            this.policyType = policyType;
        }

        public List<PolicyEntryInfo> getEntries() {
            return entries;
        }

        public void setEntries(List<PolicyEntryInfo> entries) {
            this.entries = entries;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PolicyInfo that = (PolicyInfo) o;
            return Objects.equals(policyType, that.policyType) &&
                    Objects.equals(entries, that.entries);
        }

        @Override
        public int hashCode() {
            return Objects.hash(policyType, entries);
        }
    }

    public static class PolicyEntryInfo {
        private String resource;
        private List<String> actions;
        private List<String> sourceIps;
        private String decision;

        public static PolicyEntryInfo of(String resource, List<String> actions, List<String> sourceIps,
                                         String decision) {
            PolicyEntryInfo policyEntryInfo = new PolicyEntryInfo();
            policyEntryInfo.setResource(resource);
            policyEntryInfo.setActions(actions);
            policyEntryInfo.setSourceIps(sourceIps);
            policyEntryInfo.setDecision(decision);
            return policyEntryInfo;
        }

        public String getResource() {
            return resource;
        }

        public void setResource(String resource) {
            this.resource = resource;
        }

        public List<String> getActions() {
            return actions;
        }

        public void setActions(List<String> actions) {
            this.actions = actions;
        }

        public List<String> getSourceIps() {
            return sourceIps;
        }

        public void setSourceIps(List<String> sourceIps) {
            this.sourceIps = sourceIps;
        }

        public String getDecision() {
            return decision;
        }

        public void setDecision(String decision) {
            this.decision = decision;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            PolicyEntryInfo that = (PolicyEntryInfo) o;
            return Objects.equals(resource, that.resource) &&
                    Objects.equals(actions, that.actions) &&
                    Objects.equals(sourceIps, that.sourceIps) &&
                    Objects.equals(decision, that.decision);
        }

        @Override
        public int hashCode() {
            return Objects.hash(resource, actions, sourceIps, decision);
        }
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public List<PolicyInfo> getPolicies() {
        return policies;
    }

    public void setPolicies(List<PolicyInfo> policies) {
        this.policies = policies;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AclInfo aclInfo = (AclInfo) o;
        return Objects.equals(subject, aclInfo.subject) &&
                Objects.equals(policies, aclInfo.policies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(subject, policies);
    }

    public void copyFrom(org.apache.rocketmq.remoting.protocol.body.AclInfo source) {
        this.subject = source.getSubject();
        if (source.getPolicies() != null) {
            List<PolicyInfo> copiedPolicies = new ArrayList<>();
            for (org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo policy : source.getPolicies()) {
                PolicyInfo copiedPolicy = new PolicyInfo();
                copiedPolicy.setPolicyType(policy.getPolicyType());
                if (policy.getEntries() != null) {
                    List<PolicyEntryInfo> copiedEntries = new ArrayList<>();
                    for (org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyEntryInfo entry : policy.getEntries()) {
                        PolicyEntryInfo copiedEntry = new PolicyEntryInfo();
                        copiedEntry.setResource(entry.getResource());
                        copiedEntry.setActions(new ArrayList<>(entry.getActions()));
                        copiedEntry.setSourceIps(new ArrayList<>(entry.getSourceIps()));
                        copiedEntry.setDecision(entry.getDecision());
                        copiedEntries.add(copiedEntry);
                    }
                    copiedPolicy.setEntries(copiedEntries);
                }
                copiedPolicies.add(copiedPolicy);
            }
            this.setPolicies(copiedPolicies);
        } else {
            this.setPolicies(null);
        }
    }

}
