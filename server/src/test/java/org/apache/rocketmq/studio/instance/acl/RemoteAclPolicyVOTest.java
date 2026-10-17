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
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link RemoteAclPolicyVO}: the REST representation of an Apache ACL 2.0 policy. The
 * projection's whole job is that a sparse upstream answer never surfaces null lists or null rows
 * to the API consumer: every level null-safes to empty and filters null elements.
 */
class RemoteAclPolicyVOTest {

    @Test
    void mapsAPolicyWithAllItsLevels() {
        AclInfo.PolicyEntryInfo entry = AclInfo.PolicyEntryInfo.of(
                "Topic:orders", List.of("PUB", "SUB"), List.of("10.0.0.0/8"), "ALLOW");
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setPolicyType("CUSTOM");
        group.setEntries(List.of(entry));
        AclInfo policy = new AclInfo();
        policy.setSubject("User:alice");
        policy.setPolicies(List.of(group));

        RemoteAclPolicyVO vo = RemoteAclPolicyVO.from(policy);

        assertThat(vo.subject()).isEqualTo("User:alice");
        assertThat(vo.policies()).singleElement().satisfies(mapped -> {
            assertThat(mapped.policyType()).isEqualTo("CUSTOM");
            assertThat(mapped.entries()).singleElement().satisfies(mappedEntry -> {
                assertThat(mappedEntry.resource()).isEqualTo("Topic:orders");
                assertThat(mappedEntry.actions()).containsExactly("PUB", "SUB");
                assertThat(mappedEntry.sourceIps()).containsExactly("10.0.0.0/8");
                assertThat(mappedEntry.decision()).isEqualTo("ALLOW");
            });
        });
    }

    @Test
    void aNullPolicyListProjectsToEmpty() {
        AclInfo policy = new AclInfo();
        policy.setSubject("User:alice");
        policy.setPolicies(null);

        assertThat(RemoteAclPolicyVO.from(policy).policies()).isEmpty();
    }

    @Test
    void nullPolicyRowsAreFiltered() {
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setPolicyType("CUSTOM");
        AclInfo policy = new AclInfo();
        policy.setPolicies(Arrays.asList(group, null));

        assertThat(RemoteAclPolicyVO.from(policy).policies()).hasSize(1);
    }

    @Test
    void aNullEntryListProjectsToEmpty() {
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setPolicyType("CUSTOM");
        group.setEntries(null);
        AclInfo policy = new AclInfo();
        policy.setPolicies(List.of(group));

        assertThat(RemoteAclPolicyVO.from(policy).policies().get(0).entries()).isEmpty();
    }

    @Test
    void nullEntryRowsAreFiltered() {
        AclInfo.PolicyEntryInfo entry = AclInfo.PolicyEntryInfo.of(
                "Topic:orders", List.of("PUB"), List.of(), "ALLOW");
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setEntries(Arrays.asList(entry, null));
        AclInfo policy = new AclInfo();
        policy.setPolicies(List.of(group));

        assertThat(RemoteAclPolicyVO.from(policy).policies().get(0).entries()).hasSize(1);
    }

    @Test
    void nullStringsInsideActionsAndSourceIpsAreFiltered() {
        // the wire answer carries null slots in typed lists; the REST answer must not
        AclInfo.PolicyEntryInfo entry = AclInfo.PolicyEntryInfo.of(
                "Topic:orders", Arrays.asList("PUB", null), Arrays.asList(null, "10.0.0.0/8"), "ALLOW");
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setEntries(List.of(entry));
        AclInfo policy = new AclInfo();
        policy.setPolicies(List.of(group));

        RemoteAclPolicyVO.PolicyEntryVO mapped =
                RemoteAclPolicyVO.from(policy).policies().get(0).entries().get(0);

        assertThat(mapped.actions()).containsExactly("PUB");
        assertThat(mapped.sourceIps()).containsExactly("10.0.0.0/8");
    }

    @Test
    void nullActionAndSourceIpListsProjectToEmpty() {
        AclInfo.PolicyEntryInfo entry = AclInfo.PolicyEntryInfo.of(
                "Topic:orders", null, null, "DENY");
        AclInfo.PolicyInfo group = new AclInfo.PolicyInfo();
        group.setEntries(List.of(entry));
        AclInfo policy = new AclInfo();
        policy.setPolicies(List.of(group));

        RemoteAclPolicyVO.PolicyEntryVO mapped =
                RemoteAclPolicyVO.from(policy).policies().get(0).entries().get(0);

        assertThat(mapped.actions()).isNotNull().isEmpty();
        assertThat(mapped.sourceIps()).isNotNull().isEmpty();
    }
}
