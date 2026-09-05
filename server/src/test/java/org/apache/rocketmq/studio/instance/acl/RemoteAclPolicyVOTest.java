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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class RemoteAclPolicyVOTest {

    @Test
    void exposesSubjectAndPolicyGroups() {
        RemoteAclPolicyVO.PolicyEntryVO entry = new RemoteAclPolicyVO.PolicyEntryVO(
                "order-*", List.of("PUB"), List.of("10.0.0.0/8"), "Allow");
        RemoteAclPolicyVO.PolicyGroupVO group = new RemoteAclPolicyVO.PolicyGroupVO(
                "USER", List.of(entry));

        RemoteAclPolicyVO vo = new RemoteAclPolicyVO("alice", List.of(group));

        assertEquals("alice", vo.subject());
        assertEquals(List.of(group), vo.policies());
        assertEquals("USER", group.policyType());
        assertEquals("order-*", entry.resource());
        assertEquals(List.of("PUB"), entry.actions());
        assertEquals("Allow", entry.decision());
    }

    @Test
    void equalityFollowsRecordComponents() {
        RemoteAclPolicyVO a = new RemoteAclPolicyVO("alice", List.of());
        RemoteAclPolicyVO same = new RemoteAclPolicyVO("alice", List.of());
        RemoteAclPolicyVO different = new RemoteAclPolicyVO("bob", List.of());

        assertEquals(a, same);
        assertEquals(a.hashCode(), same.hashCode());
        assertNotEquals(a, different);
    }
}
