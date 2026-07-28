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
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class AclModelTest {

    // ==================== AclInfo ====================

    @Test
    public void testAclInfoOfAndAccessors() {
        AclInfo aclInfo = AclInfo.of("User:rocketmq",
            Arrays.asList("Topic:topicA", "Group:groupA"),
            Arrays.asList("PUB", "SUB"),
            Collections.singletonList("192.168.0.0/24"),
            "Allow");

        assertEquals("User:rocketmq", aclInfo.getSubject());
        assertEquals(1, aclInfo.getPolicies().size());
        AclInfo.PolicyInfo policy = aclInfo.getPolicies().get(0);
        assertNull(policy.getPolicyType());
        assertEquals(2, policy.getEntries().size());
        AclInfo.PolicyEntryInfo entry = policy.getEntries().get(0);
        assertEquals("Topic:topicA", entry.getResource());
        assertEquals(Arrays.asList("PUB", "SUB"), entry.getActions());
        assertEquals(Collections.singletonList("192.168.0.0/24"), entry.getSourceIps());
        assertEquals("Allow", entry.getDecision());
    }

    @Test
    public void testAclInfoEqualsAndHashCode() {
        AclInfo first = AclInfo.of("User:a", Collections.singletonList("Topic:t"),
            Collections.singletonList("PUB"), Collections.emptyList(), "Allow");
        AclInfo second = AclInfo.of("User:a", Collections.singletonList("Topic:t"),
            Collections.singletonList("PUB"), Collections.emptyList(), "Allow");
        AclInfo third = AclInfo.of("User:b", Collections.singletonList("Topic:t"),
            Collections.singletonList("PUB"), Collections.emptyList(), "Deny");

        assertEquals(first, first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, third);
        assertNotEquals(first, null);
        assertNotEquals(first, "other type");

        assertEquals(first.getPolicies().get(0), second.getPolicies().get(0));
        assertEquals(first.getPolicies().get(0).hashCode(), second.getPolicies().get(0).hashCode());
        assertNotEquals(first.getPolicies().get(0), third.getPolicies().get(0));
        assertEquals(first.getPolicies().get(0).getEntries().get(0),
            second.getPolicies().get(0).getEntries().get(0));
        assertEquals(first.getPolicies().get(0).getEntries().get(0).hashCode(),
            second.getPolicies().get(0).getEntries().get(0).hashCode());
        assertNotEquals(first.getPolicies().get(0).getEntries().get(0),
            third.getPolicies().get(0).getEntries().get(0));
    }

    @Test
    public void testAclInfoSetters() {
        AclInfo aclInfo = new AclInfo();
        aclInfo.setSubject("User:x");
        AclInfo.PolicyInfo policy = new AclInfo.PolicyInfo();
        policy.setPolicyType("Custom");
        AclInfo.PolicyEntryInfo entry = new AclInfo.PolicyEntryInfo();
        entry.setResource("Topic:x");
        entry.setActions(Collections.singletonList("PUB"));
        entry.setSourceIps(Collections.singletonList("*"));
        entry.setDecision("Deny");
        policy.setEntries(Collections.singletonList(entry));
        aclInfo.setPolicies(Collections.singletonList(policy));

        assertEquals("User:x", aclInfo.getSubject());
        assertEquals("Custom", aclInfo.getPolicies().get(0).getPolicyType());
        assertEquals("Deny", aclInfo.getPolicies().get(0).getEntries().get(0).getDecision());
    }

    @Test
    public void testAclInfoCopyFrom() {
        org.apache.rocketmq.remoting.protocol.body.AclInfo source =
            org.apache.rocketmq.remoting.protocol.body.AclInfo.of("User:remote",
                Collections.singletonList("Topic:remote"),
                Collections.singletonList("PUB"),
                Collections.singletonList("10.0.0.1"),
                "Allow");
        source.getPolicies().get(0).setPolicyType("Custom");

        AclInfo target = new AclInfo();
        target.copyFrom(source);
        assertEquals("User:remote", target.getSubject());
        assertEquals(1, target.getPolicies().size());
        assertEquals("Custom", target.getPolicies().get(0).getPolicyType());
        AclInfo.PolicyEntryInfo entry = target.getPolicies().get(0).getEntries().get(0);
        assertEquals("Topic:remote", entry.getResource());
        assertEquals(Collections.singletonList("PUB"), entry.getActions());
        assertEquals(Collections.singletonList("10.0.0.1"), entry.getSourceIps());
        assertEquals("Allow", entry.getDecision());
    }

    @Test
    public void testAclInfoCopyFromNullCollections() {
        // [ISSUE #403] actions / sourceIps may be null for some ACL entries
        org.apache.rocketmq.remoting.protocol.body.AclInfo source =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo();
        source.setSubject("User:nulls");
        org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo policy =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo();
        org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyEntryInfo entry =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyEntryInfo();
        entry.setResource("Topic:n");
        policy.setEntries(Collections.singletonList(entry));
        org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo emptyPolicy =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo.PolicyInfo();
        source.setPolicies(Arrays.asList(policy, emptyPolicy));

        AclInfo target = new AclInfo();
        target.copyFrom(source);
        assertEquals("User:nulls", target.getSubject());
        assertEquals(2, target.getPolicies().size());
        AclInfo.PolicyEntryInfo copied = target.getPolicies().get(0).getEntries().get(0);
        assertNotNull(copied.getActions());
        assertTrue(copied.getActions().isEmpty());
        assertNotNull(copied.getSourceIps());
        assertTrue(copied.getSourceIps().isEmpty());
        assertNull(target.getPolicies().get(1).getEntries());
    }

    @Test
    public void testAclInfoCopyFromNullPolicies() {
        org.apache.rocketmq.remoting.protocol.body.AclInfo source =
            new org.apache.rocketmq.remoting.protocol.body.AclInfo();
        source.setSubject("User:empty");

        AclInfo target = new AclInfo();
        target.copyFrom(source);
        assertEquals("User:empty", target.getSubject());
        assertNull(target.getPolicies());
    }

    // ==================== Acl2PolicyContext ====================

    @Test
    public void testAcl2PolicyContextAccessors() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        context.setAccessKey("ak");
        context.setSecretKey("sk");
        context.setAdmin(true);
        context.setWhiteSet(Collections.singletonList("192.168.*"));
        context.setPolicyName("policy-1");
        context.setBoundType("USER");
        context.setBoundEntityId("user-1");
        context.setNamespaceScopes(Collections.singletonList("ns-1"));
        context.setEnabled(true);
        context.setDescription("desc");
        context.setClusterName("DefaultCluster");
        context.setBrokerName("broker-a");
        Date now = new Date();
        context.setCreateTime(now);
        context.setUpdateTime(now);

        assertEquals("ak", context.getAccessKey());
        assertEquals("sk", context.getSecretKey());
        assertTrue(context.isAdmin());
        assertEquals(Collections.singletonList("192.168.*"), context.getWhiteSet());
        assertEquals("policy-1", context.getPolicyName());
        assertEquals("USER", context.getBoundType());
        assertEquals("user-1", context.getBoundEntityId());
        assertEquals(Collections.singletonList("ns-1"), context.getNamespaceScopes());
        assertTrue(context.isEnabled());
        assertEquals("desc", context.getDescription());
        assertEquals("DefaultCluster", context.getClusterName());
        assertEquals("broker-a", context.getBrokerName());
        assertEquals(now, context.getCreateTime());
        assertEquals(now, context.getUpdateTime());
        assertTrue(context.toString().contains("policy-1"));
    }

    @Test
    public void testAuthorizationRuleFactories() {
        Acl2PolicyContext.AuthorizationRule allow =
            Acl2PolicyContext.AuthorizationRule.defaultAllowRule("Topic:orders/**");
        assertEquals("Topic:orders/**", allow.getResourcePattern());
        assertEquals(Arrays.asList("READ", "WRITE"), allow.getActions());
        assertEquals("Allow", allow.getEffect());
        assertEquals(100, allow.getPriority());

        Acl2PolicyContext.AuthorizationRule deny = Acl2PolicyContext.AuthorizationRule.denyAllRule();
        assertEquals("**", deny.getResourcePattern());
        assertEquals(Collections.singletonList("*"), deny.getActions());
        assertEquals("Deny", deny.getEffect());
        assertEquals(0, deny.getPriority());

        deny.setDescription("deny everything");
        assertEquals("deny everything", deny.getDescription());
        assertTrue(deny.toString().contains("Deny"));
    }

    @Test
    public void testAcl2PolicyContextValidate() {
        Acl2PolicyContext context = new Acl2PolicyContext();
        assertValidateFails(context, "accessKey");
        context.setAccessKey("ak");
        assertValidateFails(context, "policyName");
        context.setPolicyName("policy-1");
        // No rules: passes
        context.validate();

        context.setRules(new ArrayList<>());
        assertValidateFails(context, "rules list");

        Acl2PolicyContext.AuthorizationRule rule = new Acl2PolicyContext.AuthorizationRule();
        List<Acl2PolicyContext.AuthorizationRule> rules = new ArrayList<>();
        rules.add(rule);
        context.setRules(rules);
        assertValidateFails(context, "resourcePattern");

        rule.setResourcePattern("Topic:x");
        assertValidateFails(context, "actions");

        rule.setActions(Collections.singletonList("READ"));
        rule.setEffect("Maybe");
        assertValidateFails(context, "effect");

        rule.setEffect(null);
        context.validate();
        assertEquals("Allow", rule.getEffect());

        context.setBoundType("ROBOT");
        assertValidateFails(context, "boundType");
        context.setBoundType("SERVICE_ACCOUNT");
        context.validate();
        context.setBoundType("GROUP");
        context.validate();
    }

    private void assertValidateFails(Acl2PolicyContext context, String expected) {
        try {
            context.validate();
            fail("Expected IllegalArgumentException about " + expected);
        } catch (IllegalArgumentException e) {
            assertTrue("message should mention " + expected + " but was: " + e.getMessage(),
                e.getMessage().contains(expected));
        }
    }

    // ==================== AccessControlList ====================

    @Test
    public void testAccessControlList() {
        AccessControlList acl = new AccessControlList();
        acl.setBrokerAddr("127.0.0.1:10911");
        acl.setClusterName("DefaultCluster");
        acl.setVersion(7L);

        AccessControlList.AccessControlEntry entry = new AccessControlList.AccessControlEntry();
        entry.setAccessKey("ak");
        entry.setSecretKey("sk");
        entry.setAdmin("true");
        entry.setDefaultTopicPerm("DENY");
        entry.setDefaultGroupPerm("SUB");
        entry.setTopicPerms(Arrays.asList("topicA=PUB"));
        entry.setGroupPerms(Arrays.asList("groupA=SUB"));
        acl.setEntries(Collections.singletonList(entry));

        assertEquals("127.0.0.1:10911", acl.getBrokerAddr());
        assertEquals("DefaultCluster", acl.getClusterName());
        assertEquals(7L, acl.getVersion());
        assertEquals(1, acl.getEntries().size());
        AccessControlList.AccessControlEntry got = acl.getEntries().get(0);
        assertEquals("ak", got.getAccessKey());
        assertEquals("sk", got.getSecretKey());
        assertEquals("true", got.getAdmin());
        assertEquals("DENY", got.getDefaultTopicPerm());
        assertEquals("SUB", got.getDefaultGroupPerm());
        assertEquals(Arrays.asList("topicA=PUB"), got.getTopicPerms());
        assertEquals(Arrays.asList("groupA=SUB"), got.getGroupPerms());
    }
}
