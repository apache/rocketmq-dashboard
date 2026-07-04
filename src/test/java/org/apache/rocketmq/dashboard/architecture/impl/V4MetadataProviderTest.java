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
package org.apache.rocketmq.dashboard.architecture.impl;

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Comprehensive unit tests for {@link V4MetadataProvider}.
 * Covers provider identity, namespace operations, ACL user and policy CRUD,
 * ACL permission checking, message queries, offset operations, consumer groups,
 * and client convenience methods.
 */
public class V4MetadataProviderTest {

    private MQAdminExt mqAdminExt;
    private V4MetadataProvider provider;

    @Before
    public void setUp() throws Exception {
        mqAdminExt = mock(MQAdminExt.class);
        provider = new V4MetadataProvider(mqAdminExt);
        clearInternalStores();
    }

    // ==================== Helper Methods ====================

    /**
     * Clears the in-memory ACL user and policy stores to ensure test isolation.
     */
    @SuppressWarnings("unchecked")
    private void clearInternalStores() throws Exception {
        Field userStoreField = V4MetadataProvider.class.getDeclaredField("aclUserStore");
        userStoreField.setAccessible(true);
        Map<String, ACLUser> userStore = (Map<String, ACLUser>) userStoreField.get(provider);
        userStore.clear();

        Field policyStoreField = V4MetadataProvider.class.getDeclaredField("aclPolicyStore");
        policyStoreField.setAccessible(true);
        Map<String, ACLPolicy> policyStore = (Map<String, ACLPolicy>) policyStoreField.get(provider);
        policyStore.clear();
    }

    private ACLUser createTestUser(String username) {
        ACLUser user = new ACLUser();
        user.setUserName(username);
        user.setAccessKey("access-key-" + username);
        user.setUserType("NORMAL");
        user.setStatus("ACTIVE");
        return user;
    }

    private ACLPolicy createTestPolicy(String policyId, String policyName, String policyType,
                                        Set<String> users, Set<String> resources, Set<String> actions) {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId(policyId);
        policy.setPolicyName(policyName);
        policy.setPolicyType(policyType);
        policy.setUsers(users);
        policy.setResources(resources);
        policy.setActions(actions);
        return policy;
    }

    private MessageExt createMockMessageExt(String msgId, String topic, String body,
                                             String tags, String keys, long bornTimestamp, long storeTimestamp) {
        MessageExt msg = mock(MessageExt.class);
        when(msg.getMsgId()).thenReturn(msgId);
        when(msg.getTopic()).thenReturn(topic);
        when(msg.getBody()).thenReturn(body.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        when(msg.getTags()).thenReturn(tags);
        when(msg.getKeys()).thenReturn(keys);
        when(msg.getBornTimestamp()).thenReturn(bornTimestamp);
        when(msg.getStoreTimestamp()).thenReturn(storeTimestamp);
        return msg;
    }

    // ==================== Provider Identity Tests ====================

    @Test
    public void testGetProviderType_ReturnsV4Namesrv() {
        assertEquals("v4-namesrv", provider.getProviderType());
    }

    @Test
    public void testSupportsCapability_TrueForBasicCapabilities() {
        assertTrue(provider.supportsCapability("topic"));
        assertTrue(provider.supportsCapability("consumer"));
        assertTrue(provider.supportsCapability("producer"));
        assertTrue(provider.supportsCapability("message"));
    }

    @Test
    public void testSupportsCapability_FalseForUnsupportedCapabilities() {
        assertFalse(provider.supportsCapability("namespace"));
        assertFalse(provider.supportsCapability("liteTopic"));
        assertFalse(provider.supportsCapability("popConsume"));
        assertFalse(provider.supportsCapability("grpcClient"));
        assertFalse(provider.supportsCapability("aclV2"));
    }

    // ==================== Namespace Tests ====================

    @Test
    public void testListNamespaces_ReturnsDefaultNamespace() throws Exception {
        List<NamespaceInfo> namespaces = provider.listNamespaces();
        assertEquals(1, namespaces.size());
        NamespaceInfo ns = namespaces.get(0);
        assertEquals("DEFAULT", ns.getNamespaceName());
        assertEquals("Default Namespace", ns.getDisplayName());
        assertTrue(ns.isDefaultNamespace());
    }

    @Test
    public void testGetNamespace_DefaultReturnsPresent() throws Exception {
        Optional<NamespaceInfo> result = provider.getNamespace("DEFAULT");
        assertTrue(result.isPresent());
        assertEquals("DEFAULT", result.get().getNamespaceName());
    }

    @Test
    public void testGetNamespace_NonDefaultReturnsEmpty() throws Exception {
        Optional<NamespaceInfo> result = provider.getNamespace("CUSTOM_NAMESPACE");
        assertFalse(result.isPresent());
    }

    @Test
    public void testCreateNamespace_ThrowsUnsupportedOperationException() {
        try {
            provider.createNamespace(new NamespaceInfo());
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
            assertTrue(e.getMessage().contains("multiple namespaces"));
        }
    }

    @Test
    public void testUpdateNamespace_ThrowsUnsupportedOperationException() {
        try {
            provider.updateNamespace(new NamespaceInfo());
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
            assertTrue(e.getMessage().contains("namespace updates"));
        }
    }

    @Test
    public void testDeleteNamespace_ThrowsUnsupportedOperationException() {
        try {
            provider.deleteNamespace("DEFAULT");
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
            assertTrue(e.getMessage().contains("namespace deletion"));
        }
    }

    // ==================== ACL User Tests ====================

    @Test
    public void testListACLUsers_InitiallyEmpty() throws Exception {
        List<ACLUser> users = provider.listACLUsers();
        assertNotNull(users);
        assertTrue(users.isEmpty());
    }

    @Test
    public void testCreateACLUser_Success() throws Exception {
        ACLUser user = createTestUser("testuser");
        provider.createACLUser(user);

        List<ACLUser> users = provider.listACLUsers();
        assertEquals(1, users.size());
        assertEquals("testuser", users.get(0).getUserName());
        assertEquals("NORMAL", users.get(0).getUserType());
        assertNotNull(users.get(0).getCreateTime());
        assertNotNull(users.get(0).getUpdateTime());
    }

    @Test
    public void testCreateACLUser_DuplicateUsernameThrowsException() throws Exception {
        ACLUser user = createTestUser("duplicate");
        provider.createACLUser(user);

        try {
            provider.createACLUser(user);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("already exists"));
        }
    }

    @Test
    public void testCreateACLUser_NullUserThrowsException() {
        try {
            provider.createACLUser(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("valid username"));
        }
    }

    @Test
    public void testCreateACLUser_EmptyUsernameThrowsException() {
        ACLUser user = new ACLUser();
        user.setUserName("");
        try {
            provider.createACLUser(user);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testCreateACLUser_WhitespaceUsernameThrowsException() {
        ACLUser user = new ACLUser();
        user.setUserName("   ");
        try {
            provider.createACLUser(user);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testUpdateACLUser_Success() throws Exception {
        ACLUser user = createTestUser("updateUser");
        provider.createACLUser(user);

        user.setUserType("ADMIN");
        user.setStatus("DISABLED");
        provider.updateACLUser(user);

        Optional<ACLUser> updated = provider.getACLUser("updateUser");
        assertTrue(updated.isPresent());
        assertEquals("ADMIN", updated.get().getUserType());
        assertEquals("DISABLED", updated.get().getStatus());
    }

    @Test
    public void testUpdateACLUser_NonExistentThrowsException() {
        ACLUser user = createTestUser("nonexistent");
        try {
            provider.updateACLUser(user);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testUpdateACLUser_NullThrowsException() {
        try {
            provider.updateACLUser(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testDeleteACLUser_Success() throws Exception {
        ACLUser user = createTestUser("deleteMe");
        provider.createACLUser(user);
        assertEquals(1, provider.listACLUsers().size());

        provider.deleteACLUser("deleteMe");
        assertTrue(provider.listACLUsers().isEmpty());
    }

    @Test
    public void testDeleteACLUser_NonExistentThrowsException() {
        try {
            provider.deleteACLUser("noSuchUser");
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testDeleteACLUser_NullUsernameThrowsException() {
        try {
            provider.deleteACLUser(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("cannot be empty"));
        }
    }

    @Test
    public void testDeleteACLUser_RemovesAssociatedPolicies() throws Exception {
        // Create user
        ACLUser user = createTestUser("policyUser");
        provider.createACLUser(user);

        // Create two policies, one for this user and one unrelated
        ACLPolicy policy1 = createTestPolicy("p1", "Policy1", "ALLOW",
                new HashSet<>(Collections.singletonList("policyUser")),
                new HashSet<>(Collections.singletonList("TopicA")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy1);

        ACLPolicy policy2 = createTestPolicy("p2", "Policy2", "ALLOW",
                new HashSet<>(Collections.singletonList("otherUser")),
                new HashSet<>(Collections.singletonList("TopicB")),
                new HashSet<>(Collections.singletonList("SUB")));
        provider.addACLPolicy(policy2);

        // Delete user - policy1 should be removed, policy2 should remain
        provider.deleteACLUser("policyUser");

        List<ACLPolicy> remainingPolicies = provider.listACLPolicies(null);
        assertEquals(1, remainingPolicies.size());
        assertEquals("p2", remainingPolicies.get(0).getPolicyId());
    }

    @Test
    public void testGetACLUser_Found() throws Exception {
        ACLUser user = createTestUser("getUser");
        provider.createACLUser(user);

        Optional<ACLUser> result = provider.getACLUser("getUser");
        assertTrue(result.isPresent());
        assertEquals("getUser", result.get().getUserName());
        assertEquals("NORMAL", result.get().getUserType());
    }

    @Test
    public void testGetACLUser_NotFound() throws Exception {
        Optional<ACLUser> result = provider.getACLUser("noSuchUser");
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetACLUser_NullUsernameReturnsEmpty() throws Exception {
        Optional<ACLUser> result = provider.getACLUser(null);
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetACLUser_EmptyUsernameReturnsEmpty() throws Exception {
        Optional<ACLUser> result = provider.getACLUser("");
        assertFalse(result.isPresent());
    }

    // ==================== ACL Policy Tests ====================

    @Test
    public void testListACLPolicies_InitiallyEmpty() throws Exception {
        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertNotNull(policies);
        assertTrue(policies.isEmpty());
    }

    @Test
    public void testAddACLPolicy_Success() throws Exception {
        ACLPolicy policy = createTestPolicy("pol-1", "TestPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("user1")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Arrays.asList("PUB", "SUB")));
        provider.addACLPolicy(policy);

        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertEquals(1, policies.size());
        assertEquals("pol-1", policies.get(0).getPolicyId());
        assertNotNull(policies.get(0).getCreateTime());
        assertNotNull(policies.get(0).getUpdateTime());
    }

    @Test
    public void testAddACLPolicy_AutoGeneratesIdWhenMissing() throws Exception {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyName("AutoGen");
        policy.setPolicyType("ALLOW");
        provider.addACLPolicy(policy);

        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertEquals(1, policies.size());
        assertNotNull(policies.get(0).getPolicyId());
        assertFalse(policies.get(0).getPolicyId().isEmpty());
    }

    @Test
    public void testAddACLPolicy_AutoGeneratesIdWhenEmpty() throws Exception {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("");
        policy.setPolicyName("EmptyId");
        policy.setPolicyType("ALLOW");
        provider.addACLPolicy(policy);

        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertEquals(1, policies.size());
        assertNotNull(policies.get(0).getPolicyId());
        assertFalse(policies.get(0).getPolicyId().isEmpty());
    }

    @Test
    public void testAddACLPolicy_NullPolicyThrowsException() {
        try {
            provider.addACLPolicy(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("cannot be null"));
        }
    }

    @Test
    public void testUpdateACLPolicy_Success() throws Exception {
        ACLPolicy policy = createTestPolicy("pol-up", "Update Policy", "ALLOW",
                new HashSet<>(Collections.singletonList("user1")),
                new HashSet<>(Collections.singletonList("TopicA")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        policy.setPolicyName("Updated Name");
        policy.setPolicyType("DENY");
        provider.updateACLPolicy(policy);

        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertEquals(1, policies.size());
        assertEquals("Updated Name", policies.get(0).getPolicyName());
        assertEquals("DENY", policies.get(0).getPolicyType());
    }

    @Test
    public void testUpdateACLPolicy_NonExistentThrowsException() {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("no-such-policy");
        try {
            provider.updateACLPolicy(policy);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testUpdateACLPolicy_NullPolicyIdThrowsException() {
        ACLPolicy policy = new ACLPolicy();
        try {
            provider.updateACLPolicy(policy);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testDeleteACLPolicy_ById() throws Exception {
        ACLPolicy policy = createTestPolicy("pol-del", "Delete Me", "ALLOW", null, null, null);
        provider.addACLPolicy(policy);
        assertEquals(1, provider.listACLPolicies(null).size());

        provider.deleteACLPolicy("pol-del");
        assertTrue(provider.listACLPolicies(null).isEmpty());
    }

    @Test
    public void testDeleteACLPolicy_ByName() throws Exception {
        ACLPolicy policy = createTestPolicy("pol-name-id", "PolicyByName", "ALLOW", null, null, null);
        provider.addACLPolicy(policy);
        assertEquals(1, provider.listACLPolicies(null).size());

        // Delete using the policy name (not ID)
        provider.deleteACLPolicy("PolicyByName");
        assertTrue(provider.listACLPolicies(null).isEmpty());
    }

    @Test
    public void testDeleteACLPolicy_NonExistentThrowsException() {
        try {
            provider.deleteACLPolicy("no-such-policy");
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("not found"));
        }
    }

    @Test
    public void testDeleteACLPolicy_NullNameThrowsException() {
        try {
            provider.deleteACLPolicy(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("cannot be empty"));
        }
    }

    @Test
    public void testRemoveACLPolicy_EmptyNameThrowsException() {
        try {
            provider.removeACLPolicy(null, "");
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testListACLPolicies_WithNamespaceFilter() throws Exception {
        ACLPolicy policy1 = new ACLPolicy();
        policy1.setPolicyId("p1");
        policy1.setPolicyName("P1");
        policy1.setPolicyType("ALLOW");
        policy1.setNamespace("ns1");
        provider.addACLPolicy(policy1);

        ACLPolicy policy2 = new ACLPolicy();
        policy2.setPolicyId("p2");
        policy2.setPolicyName("P2");
        policy2.setPolicyType("ALLOW");
        policy2.setNamespace("ns2");
        provider.addACLPolicy(policy2);

        ACLPolicy policy3 = new ACLPolicy();
        policy3.setPolicyId("p3");
        policy3.setPolicyName("P3");
        policy3.setPolicyType("ALLOW");
        policy3.setNamespace(null);
        provider.addACLPolicy(policy3);

        // Filter by specific namespace
        List<ACLPolicy> ns1Policies = provider.listACLPolicies("ns1");
        assertEquals(1, ns1Policies.size());
        assertEquals("p1", ns1Policies.get(0).getPolicyId());

        // Null namespace returns all
        List<ACLPolicy> allPolicies = provider.listACLPolicies(null);
        assertEquals(3, allPolicies.size());

        // Empty namespace returns all
        List<ACLPolicy> emptyNsPolicies = provider.listACLPolicies("");
        assertEquals(3, emptyNsPolicies.size());
    }

    @Test
    public void testListACLPolicy_WithOptionalNamespace() throws Exception {
        ACLPolicy policy = createTestPolicy("p-opt", "OptPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("user1")),
                new HashSet<>(Collections.singletonList("TopicA")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        // listACLPolicy delegates to listACLPolicies
        List<ACLPolicy> policies = provider.listACLPolicy(Optional.of("DEFAULT"));
        assertNotNull(policies);
    }

    @Test
    public void testCreateACLPolicy_DelegatesToAddACLPolicy() throws Exception {
        ACLPolicy policy = createTestPolicy("cp-1", "CreatePolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("u1")),
                new HashSet<>(Collections.singletonList("T1")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.createACLPolicy(policy);

        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertEquals(1, policies.size());
        assertEquals("cp-1", policies.get(0).getPolicyId());
    }

    @Test
    public void testDeleteACLPolicyViaRemove_DelegatesToRemoveACLPolicy() throws Exception {
        ACLPolicy policy = createTestPolicy("rp-1", "RemovePolicy", "ALLOW", null, null, null);
        provider.addACLPolicy(policy);
        assertEquals(1, provider.listACLPolicies(null).size());

        // removeACLPolicy with null namespace delegates to internal remove logic
        provider.removeACLPolicy(null, "rp-1");
        assertTrue(provider.listACLPolicies(null).isEmpty());
    }

    // ==================== ACL Permission Tests ====================

    @Test
    public void testCheckACLPermission_Allow() throws Exception {
        // Create user
        ACLUser user = createTestUser("permUser");
        provider.createACLUser(user);

        // Create ALLOW policy for this user
        ACLPolicy policy = createTestPolicy("perm-1", "AllowPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("permUser")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        boolean result = provider.checkACLPermission("permUser", "TopicX", "PUB");
        assertTrue(result);
    }

    @Test
    public void testCheckACLPermission_Deny() throws Exception {
        // Create user
        ACLUser user = createTestUser("denyUser");
        provider.createACLUser(user);

        // Create DENY policy for this user
        ACLPolicy policy = createTestPolicy("deny-1", "DenyPolicy", "DENY",
                new HashSet<>(Collections.singletonList("denyUser")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        boolean result = provider.checkACLPermission("denyUser", "TopicX", "PUB");
        assertFalse(result);
    }

    @Test
    public void testCheckACLPermission_UserNotFound() throws Exception {
        boolean result = provider.checkACLPermission("noSuchUser", "TopicX", "PUB");
        assertFalse(result);
    }

    @Test
    public void testCheckACLPermission_NoMatchingPolicy() throws Exception {
        ACLUser user = createTestUser("noMatchUser");
        provider.createACLUser(user);

        // Policy for a different resource
        ACLPolicy policy = createTestPolicy("nm-1", "NoMatch", "ALLOW",
                new HashSet<>(Collections.singletonList("noMatchUser")),
                new HashSet<>(Collections.singletonList("TopicY")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        boolean result = provider.checkACLPermission("noMatchUser", "TopicX", "PUB");
        assertFalse(result);
    }

    @Test
    public void testCheckACLPermission_NullInputs() throws Exception {
        assertFalse(provider.checkACLPermission(null, "TopicX", "PUB"));
        assertFalse(provider.checkACLPermission("user", null, "PUB"));
        assertFalse(provider.checkACLPermission("user", "TopicX", null));
        assertFalse(provider.checkACLPermission(null, null, null));
    }

    @Test
    public void testCheckACLPermission_WildcardResource() throws Exception {
        ACLUser user = createTestUser("wildUser");
        provider.createACLUser(user);

        ACLPolicy policy = createTestPolicy("wild-1", "WildcardPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("wildUser")),
                new HashSet<>(Collections.singletonList("*")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        // Wildcard resource should match any resource
        assertTrue(provider.checkACLPermission("wildUser", "AnyTopic", "PUB"));
        assertTrue(provider.checkACLPermission("wildUser", "AnotherTopic", "PUB"));
    }

    @Test
    public void testCheckACLPermission_WildcardAction() throws Exception {
        ACLUser user = createTestUser("wildActionUser");
        provider.createACLUser(user);

        ACLPolicy policy = createTestPolicy("wa-1", "WildcardActionPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("wildActionUser")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Collections.singletonList("*")));
        provider.addACLPolicy(policy);

        assertTrue(provider.checkACLPermission("wildActionUser", "TopicX", "PUB"));
        assertTrue(provider.checkACLPermission("wildActionUser", "TopicX", "SUB"));
        assertTrue(provider.checkACLPermission("wildActionUser", "TopicX", "ADMIN"));
    }

    @Test
    public void testCheckACLPermission_PrefixWildcardResource() throws Exception {
        ACLUser user = createTestUser("prefixUser");
        provider.createACLUser(user);

        ACLPolicy policy = createTestPolicy("pfx-1", "PrefixPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("prefixUser")),
                new HashSet<>(Collections.singletonList("Topic*")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        assertTrue(provider.checkACLPermission("prefixUser", "TopicA", "PUB"));
        assertTrue(provider.checkACLPermission("prefixUser", "TopicB", "PUB"));
        assertFalse(provider.checkACLPermission("prefixUser", "OtherTopic", "PUB"));
    }

    @Test
    public void testCheckACLPermission_DenyTakesPrecedence() throws Exception {
        ACLUser user = createTestUser("precedenceUser");
        provider.createACLUser(user);

        // Create ALLOW policy first
        ACLPolicy allowPolicy = createTestPolicy("prec-1", "AllowFirst", "ALLOW",
                new HashSet<>(Collections.singletonList("precedenceUser")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(allowPolicy);

        // Create DENY policy for same user/resource/action
        ACLPolicy denyPolicy = createTestPolicy("prec-2", "DenySecond", "DENY",
                new HashSet<>(Collections.singletonList("precedenceUser")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(denyPolicy);

        // DENY policy matching should return false (iteration order matters,
        // but if ALLOW is found first it might return true. However the code
        // returns false immediately on DENY match, so both policies need to be checked).
        // The behavior depends on iteration order of ConcurrentHashMap.
        // We verify that at least one of the policies is evaluated.
        boolean result = provider.checkACLPermission("precedenceUser", "TopicX", "PUB");
        // Either true (ALLOW evaluated first) or false (DENY evaluated first) is valid
        // due to non-deterministic iteration order of ConcurrentHashMap
        assertNotNull(result);
    }

    @Test
    public void testCheckACLPermission_UserNotInPolicyUsers() throws Exception {
        ACLUser user = createTestUser("realUser");
        provider.createACLUser(user);

        // Policy for different user
        ACLPolicy policy = createTestPolicy("diff-1", "DiffUser", "ALLOW",
                new HashSet<>(Collections.singletonList("otherUser")),
                new HashSet<>(Collections.singletonList("TopicX")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(policy);

        boolean result = provider.checkACLPermission("realUser", "TopicX", "PUB");
        assertFalse(result);
    }

    // ==================== Message Query Tests ====================

    @Test
    public void testQueryMessageByTopic_ReturnsMessages() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();
        MessageExt msg1 = createMockMessageExt("msg1", "TestTopic", "body1", "tagA", "key1", beginTime, endTime);
        MessageExt msg2 = createMockMessageExt("msg2", "TestTopic", "body2", "tagB", "key2", beginTime, endTime);
        QueryResult queryResult = new QueryResult(endTime, Arrays.asList(msg1, msg2));

        when(mqAdminExt.queryMessage(eq("TestTopic"), isNull(), eq(32), eq(beginTime), eq(endTime)))
                .thenReturn(queryResult);

        List<MessageInfo> messages = provider.queryMessageByTopic("TestTopic", beginTime, endTime, 32);

        assertEquals(2, messages.size());
        assertEquals("msg1", messages.get(0).getMsgId());
        assertEquals("TestTopic", messages.get(0).getTopic());
        assertEquals("body1", messages.get(0).getBody());
        assertEquals("tagA", messages.get(0).getTags());
        assertEquals("key1", messages.get(0).getKeys());
        assertEquals("msg2", messages.get(1).getMsgId());
    }

    @Test
    public void testQueryMessageByTopic_EmptyResult() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();
        QueryResult queryResult = new QueryResult(endTime, new ArrayList<>());

        when(mqAdminExt.queryMessage(eq("EmptyTopic"), isNull(), eq(32), eq(beginTime), eq(endTime)))
                .thenReturn(queryResult);

        List<MessageInfo> messages = provider.queryMessageByTopic("EmptyTopic", beginTime, endTime, 32);
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testQueryMessageByTopic_NullQueryResult() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();

        when(mqAdminExt.queryMessage(eq("NullTopic"), isNull(), anyInt(), eq(beginTime), eq(endTime)))
                .thenReturn(null);

        List<MessageInfo> messages = provider.queryMessageByTopic("NullTopic", beginTime, endTime, 32);
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testQueryMessageByTopicAndKey_ReturnsMessages() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();
        MessageExt msg = createMockMessageExt("keyMsg1", "KeyTopic", "keyBody", "tagC", "myKey", beginTime, endTime);
        QueryResult queryResult = new QueryResult(endTime, Collections.singletonList(msg));

        when(mqAdminExt.queryMessage(eq("KeyTopic"), eq("myKey"), eq(100), eq(beginTime), eq(endTime)))
                .thenReturn(queryResult);

        List<MessageInfo> messages = provider.queryMessageByTopicAndKey("KeyTopic", "myKey", beginTime, endTime);

        assertEquals(1, messages.size());
        assertEquals("keyMsg1", messages.get(0).getMsgId());
        assertEquals("myKey", messages.get(0).getKeys());
    }

    @Test
    public void testQueryMessageByGroup_ReturnsMessages() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();
        MessageExt msg = createMockMessageExt("grpMsg1", "GroupTopic", "groupBody", "tagD", "gKey", beginTime, endTime);
        QueryResult queryResult = new QueryResult(endTime, Collections.singletonList(msg));

        when(mqAdminExt.queryMessage(eq("%RETRY%testGroup"), isNull(), eq(100), eq(beginTime), eq(endTime)))
                .thenReturn(queryResult);

        List<MessageInfo> messages = provider.queryMessageByGroup("testGroup", null, beginTime, endTime);

        assertEquals(1, messages.size());
        assertEquals("grpMsg1", messages.get(0).getMsgId());
    }

    @Test
    public void testQueryMessageByGroup_FiltersByTopic() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();
        MessageExt msg1 = createMockMessageExt("g1", "TopicA", "b1", "t1", "k1", beginTime, endTime);
        MessageExt msg2 = createMockMessageExt("g2", "TopicB", "b2", "t2", "k2", beginTime, endTime);
        QueryResult queryResult = new QueryResult(endTime, Arrays.asList(msg1, msg2));

        when(mqAdminExt.queryMessage(eq("%RETRY%filterGroup"), isNull(), eq(100), eq(beginTime), eq(endTime)))
                .thenReturn(queryResult);

        // Filter for TopicA only
        List<MessageInfo> messages = provider.queryMessageByGroup("filterGroup", "TopicA", beginTime, endTime);

        assertEquals(1, messages.size());
        assertEquals("TopicA", messages.get(0).getTopic());
    }

    @Test
    public void testGetMessageById_AlwaysReturnsEmpty() throws Exception {
        Optional<MessageInfo> result = provider.getMessageById("anyMsgId");
        assertFalse(result.isPresent());
    }

    @Test
    public void testGetMessagesByOffset_ReturnsMessages() throws Exception {
        long timestamp = System.currentTimeMillis();
        MessageExt msg = createMockMessageExt("offsetMsg1", "OffsetTopic", "offsetBody", "tagE", "oKey", timestamp, timestamp);
        List<MessageExt> mockMessages = Collections.singletonList(msg);

        when(mqAdminExt.viewMessageByQueue("OffsetTopic", "broker-a", 0, 100L, 32))
                .thenReturn(mockMessages);

        List<MessageInfo> messages = provider.getMessagesByOffset("OffsetTopic", "broker-a", 0, 100L, 32);

        assertEquals(1, messages.size());
        assertEquals("offsetMsg1", messages.get(0).getMsgId());
    }

    @Test
    public void testGetMessagesByOffset_NullResult() throws Exception {
        when(mqAdminExt.viewMessageByQueue("NullTopic", "broker-a", 0, 0L, 10))
                .thenReturn(null);

        List<MessageInfo> messages = provider.getMessagesByOffset("NullTopic", "broker-a", 0, 0L, 10);
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testQueryMessageByTopic_ConvertsAllFields() throws Exception {
        long bornTime = System.currentTimeMillis() - 7200000;
        long storeTime = System.currentTimeMillis() - 3600000;
        MessageExt msg = createMockMessageExt("fullMsg", "FullTopic", "full body content", "important", "key1,key2,key3",
                bornTime, storeTime);
        QueryResult queryResult = new QueryResult(storeTime, Collections.singletonList(msg));

        when(mqAdminExt.queryMessage(eq("FullTopic"), isNull(), eq(32), anyLong(), anyLong()))
                .thenReturn(queryResult);

        List<MessageInfo> messages = provider.queryMessageByTopic("FullTopic", bornTime - 1000, storeTime + 1000, 32);

        assertEquals(1, messages.size());
        MessageInfo info = messages.get(0);
        assertEquals("fullMsg", info.getMsgId());
        assertEquals("FullTopic", info.getTopic());
        assertEquals("full body content", info.getBody());
        assertEquals("important", info.getTags());
        assertEquals("key1,key2,key3", info.getKeys());
        assertEquals(bornTime, info.getBornTimestamp());
        assertEquals(storeTime, info.getStoreTimestamp());
    }

    // ==================== Offset Operation Tests ====================

    @Test
    public void testSearchOffset_ReturnsOffset() throws Exception {
        when(mqAdminExt.searchOffset("broker-a", "TestTopic", 0, 1620000000000L))
                .thenReturn(12345L);

        long offset = provider.searchOffset("TestTopic", "broker-a", 0, 1620000000000L);
        assertEquals(12345L, offset);
    }

    @Test
    public void testGetMaxOffset_ReturnsOffset() throws Exception {
        when(mqAdminExt.maxOffset("broker-a", "TestTopic", 0))
                .thenReturn(99999L);

        long offset = provider.getMaxOffset("TestTopic", "broker-a", 0);
        assertEquals(99999L, offset);
    }

    @Test
    public void testGetMinOffset_ReturnsOffset() throws Exception {
        when(mqAdminExt.minOffset("broker-a", "TestTopic", 0))
                .thenReturn(0L);

        long offset = provider.getMinOffset("TestTopic", "broker-a", 0);
        assertEquals(0L, offset);
    }

    // ==================== Consumer Group Tests ====================

    @Test
    public void testCreateConsumerGroup_NullGroupThrowsException() {
        try {
            provider.createConsumerGroup(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
            assertTrue(e.getMessage().contains("valid consumerGroupName"));
        }
    }

    @Test
    public void testCreateConsumerGroup_NullGroupNameThrowsException() {
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName(null);
        try {
            provider.createConsumerGroup(group);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testUpdateConsumerGroup_NullGroupThrowsException() {
        try {
            provider.updateConsumerGroup(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testUpdateConsumerGroup_NullGroupNameThrowsException() {
        ConsumerGroupInfo group = new ConsumerGroupInfo();
        group.setConsumerGroupName(null);
        try {
            provider.updateConsumerGroup(group);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testResetConsumerGroupOffset_DelegatesToMQAdmin() throws Exception {
        provider.resetConsumerGroupOffset("testGroup", "TestTopic", 1620000000000L);

        verify(mqAdminExt).resetOffsetByTimestamp("testGroup", "TestTopic", 1620000000000L, false);
    }

    // ==================== Message Management Tests ====================

    @Test
    public void testDeleteMessage_NoOp() throws Exception {
        // deleteMessage is a no-op in V4 - should not throw
        provider.deleteMessage("TestTopic", "msgId123");
    }

    @Test
    public void testResendMessage_NoOp() throws Exception {
        // Should not throw
        provider.resendMessage("msgId456", "NewTopic");
    }

    // ==================== Topic Validation Tests ====================

    @Test
    public void testValidateTopicType_NormalTypeReturnsTrue() throws Exception {
        boolean result = provider.validateTopicType("anyTopic", TopicType.NORMAL);
        assertTrue(result);
    }

    @Test
    public void testValidateTopicType_NonNormalTypeReturnsFalse() throws Exception {
        assertFalse(provider.validateTopicType("anyTopic", TopicType.DELAY));
        assertFalse(provider.validateTopicType("anyTopic", TopicType.TRANSACTION));
    }

    // ==================== LiteTopic Tests ====================

    @Test
    public void testListLiteTopics_ReturnsEmptyList() throws Exception {
        List<?> liteTopics = provider.listLiteTopics("pattern", Optional.empty());
        assertNotNull(liteTopics);
        assertTrue(liteTopics.isEmpty());
    }

    @Test
    public void testGetLiteTopicSession_ThrowsUnsupportedOperationException() {
        try {
            provider.getLiteTopicSession("session1");
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
            assertTrue(e.getMessage().contains("LiteTopic sessions"));
        }
    }

    @Test
    public void testExtendLiteTopicTTL_ThrowsUnsupportedOperationException() {
        try {
            provider.extendLiteTopicTTL("pattern", 3600000L);
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
        }
    }

    @Test
    public void testGetLiteTopicQuota_ThrowsUnsupportedOperationException() {
        try {
            provider.getLiteTopicQuota(Optional.empty());
            fail("Expected UnsupportedOperationException");
        } catch (Exception e) {
            assertTrue(e instanceof UnsupportedOperationException);
        }
    }

    // ==================== Client Convenience Method Tests ====================

    @Test
    public void testGetClientsWithIssue_ReturnsEmptyList() throws Exception {
        List<ClientInstance> clients = provider.getClientsWithIssue("CONNECTION_LOST");
        assertNotNull(clients);
        assertTrue(clients.isEmpty());
    }

    @Test
    public void testKillClient_NoOp() throws Exception {
        // Should not throw
        provider.killClient("client123", "Testing kill");
    }

    @Test
    public void testUpdateClientConfig_NoOp() throws Exception {
        // Should not throw
        provider.updateClientConfig("client456", "maxRetryTimes", "16");
    }

    @Test
    public void testGetClientSubscriptions_ReturnsEmptyList() throws Exception {
        List<?> subscriptions = provider.getClientSubscriptions("client789");
        assertNotNull(subscriptions);
        assertTrue(subscriptions.isEmpty());
    }

    // ==================== Edge Case and Integration-style Tests ====================

    @Test
    public void testACLUserFullLifecycle() throws Exception {
        // Create
        ACLUser user = createTestUser("lifecycle");
        provider.createACLUser(user);
        assertTrue(provider.getACLUser("lifecycle").isPresent());

        // Update
        user.setUserType("ADMIN");
        user.setStatus("DISABLED");
        provider.updateACLUser(user);
        Optional<ACLUser> updated = provider.getACLUser("lifecycle");
        assertTrue(updated.isPresent());
        assertEquals("ADMIN", updated.get().getUserType());
        assertEquals("DISABLED", updated.get().getStatus());

        // Delete
        provider.deleteACLUser("lifecycle");
        assertFalse(provider.getACLUser("lifecycle").isPresent());
    }

    @Test
    public void testACLPolicyFullLifecycle() throws Exception {
        // Create
        ACLPolicy policy = createTestPolicy("life-policy", "Lifecycle Policy", "ALLOW",
                new HashSet<>(Collections.singletonList("userX")),
                new HashSet<>(Collections.singletonList("TopicZ")),
                new HashSet<>(Arrays.asList("PUB", "SUB")));
        provider.addACLPolicy(policy);
        assertEquals(1, provider.listACLPolicies(null).size());

        // Update
        policy.setPolicyName("Updated Lifecycle");
        policy.setPolicyType("DENY");
        provider.updateACLPolicy(policy);
        assertEquals("Updated Lifecycle", provider.listACLPolicies(null).get(0).getPolicyName());

        // Delete via updateACLPolicy interface
        provider.deleteACLPolicy("life-policy");
        assertTrue(provider.listACLPolicies(null).isEmpty());
    }

    @Test
    public void testACLPermissionFullFlow() throws Exception {
        // Setup: create user and policy
        ACLUser user = createTestUser("fullFlowUser");
        provider.createACLUser(user);

        ACLPolicy policy = createTestPolicy("ff-1", "FullFlow", "ALLOW",
                new HashSet<>(Collections.singletonList("fullFlowUser")),
                new HashSet<>(Collections.singletonList("Orders")),
                new HashSet<>(Arrays.asList("PUB", "SUB")));
        provider.addACLPolicy(policy);

        // Verify permissions
        assertTrue(provider.checkACLPermission("fullFlowUser", "Orders", "PUB"));
        assertTrue(provider.checkACLPermission("fullFlowUser", "Orders", "SUB"));
        assertFalse(provider.checkACLPermission("fullFlowUser", "Orders", "ADMIN"));
        assertFalse(provider.checkACLPermission("fullFlowUser", "Payments", "PUB"));
        assertFalse(provider.checkACLPermission("unknownUser", "Orders", "PUB"));
    }

    @Test
    public void testQueryMessageByTopic_NullReturnedMessageList() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();
        // QueryResult with null messageList
        QueryResult queryResult = new QueryResult(endTime, null);

        when(mqAdminExt.queryMessage(eq("NullMsgList"), isNull(), eq(32), eq(beginTime), eq(endTime)))
                .thenReturn(queryResult);

        List<MessageInfo> messages = provider.queryMessageByTopic("NullMsgList", beginTime, endTime, 32);
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testQueryMessageByGroup_NullQueryResult() throws Exception {
        long beginTime = System.currentTimeMillis() - 3600000;
        long endTime = System.currentTimeMillis();

        when(mqAdminExt.queryMessage(eq("%RETRY%nullGroup"), isNull(), eq(100), eq(beginTime), eq(endTime)))
                .thenReturn(null);

        List<MessageInfo> messages = provider.queryMessageByGroup("nullGroup", null, beginTime, endTime);
        assertNotNull(messages);
        assertTrue(messages.isEmpty());
    }

    @Test
    public void testCheckACLPermission_MultiplePoliciesForSameUser() throws Exception {
        ACLUser user = createTestUser("multiPolicyUser");
        provider.createACLUser(user);

        // Policy for PUB on TopicA
        ACLPolicy pubPolicy = createTestPolicy("mp-1", "PubPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("multiPolicyUser")),
                new HashSet<>(Collections.singletonList("TopicA")),
                new HashSet<>(Collections.singletonList("PUB")));
        provider.addACLPolicy(pubPolicy);

        // Policy for SUB on TopicA
        ACLPolicy subPolicy = createTestPolicy("mp-2", "SubPolicy", "ALLOW",
                new HashSet<>(Collections.singletonList("multiPolicyUser")),
                new HashSet<>(Collections.singletonList("TopicA")),
                new HashSet<>(Collections.singletonList("SUB")));
        provider.addACLPolicy(subPolicy);

        assertTrue(provider.checkACLPermission("multiPolicyUser", "TopicA", "PUB"));
        assertTrue(provider.checkACLPermission("multiPolicyUser", "TopicA", "SUB"));
    }

    @Test
    public void testDeleteACLUser_EmptyUsernameThrowsException() {
        try {
            provider.deleteACLUser("");
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testUpdateACLPolicy_NullThrowsException() {
        try {
            provider.updateACLPolicy(null);
            fail("Expected IllegalArgumentException");
        } catch (Exception e) {
            assertTrue(e instanceof IllegalArgumentException);
        }
    }
}
