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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.architecture.MetadataProvider;
import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AclServiceImplTest {

    @InjectMocks
    private AclServiceImpl aclService;

    @Mock
    private MetadataProvider metadataProvider;

    @Before
    public void setUp() throws Exception {
        // Ensure the private (shadowing) field in AclServiceImpl is populated
        Field field = AclServiceImpl.class.getDeclaredField("metadataProvider");
        field.setAccessible(true);
        field.set(aclService, metadataProvider);
    }

    private ACLUser buildUser() {
        ACLUser user = new ACLUser();
        user.setUserName("alice");
        user.setAccessKey("AK");
        return user;
    }

    private ACLPolicy buildPolicy() {
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("policy-1");
        policy.setUsers(new HashSet<>(Collections.singletonList("alice")));
        policy.setResources(new HashSet<>(Collections.singletonList("Topic:test")));
        policy.setActions(new HashSet<>(Collections.singletonList("PUB")));
        return policy;
    }

    @Test
    public void testListUsersSuccess() throws Exception {
        ACLUser user = buildUser();
        when(metadataProvider.listACLUsers()).thenReturn(Collections.singletonList(user));

        List<ACLUser> result = aclService.listUsers();
        assertEquals(1, result.size());
        assertSame(user, result.get(0));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListUsersFailure() throws Exception {
        when(metadataProvider.listACLUsers()).thenThrow(new RuntimeException("acl disabled"));
        aclService.listUsers();
    }

    @Test
    public void testListPoliciesSuccess() throws Exception {
        ACLPolicy policy = buildPolicy();
        when(metadataProvider.listACLPolicies("alice")).thenReturn(Collections.singletonList(policy));

        List<ACLPolicy> result = aclService.listPolicies("alice");
        assertEquals(1, result.size());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListPoliciesFailure() throws Exception {
        when(metadataProvider.listACLPolicies(anyString())).thenThrow(new RuntimeException("boom"));
        aclService.listPolicies("alice");
    }

    @Test
    public void testCreateUserSuccess() throws Exception {
        ACLUser user = buildUser();
        assertTrue(aclService.createUser(user));
        verify(metadataProvider).createACLUser(user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateUserNull() {
        aclService.createUser(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateUserMissingUserName() {
        ACLUser user = new ACLUser();
        user.setAccessKey("AK");
        aclService.createUser(user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCreateUserMissingAccessKey() {
        ACLUser user = new ACLUser();
        user.setUserName("alice");
        aclService.createUser(user);
    }

    @Test
    public void testUpdateUserSuccess() throws Exception {
        ACLUser user = buildUser();
        assertTrue(aclService.updateUser(user));
        verify(metadataProvider).updateACLUser(user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateUserProviderFailure() throws Exception {
        ACLUser user = buildUser();
        doThrow(new RuntimeException("failed")).when(metadataProvider).updateACLUser(user);
        aclService.updateUser(user);
    }

    @Test
    public void testDeleteUserSuccess() throws Exception {
        assertTrue(aclService.deleteUser("alice"));
        verify(metadataProvider).deleteACLUser("alice");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteUserEmptyName() {
        aclService.deleteUser("  ");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDeleteUserProviderFailure() throws Exception {
        doThrow(new RuntimeException("failed")).when(metadataProvider).deleteACLUser("alice");
        aclService.deleteUser("alice");
    }

    @Test
    public void testAddPolicySuccess() throws Exception {
        ACLPolicy policy = buildPolicy();
        assertTrue(aclService.addPolicy(policy));
        verify(metadataProvider).addACLPolicy(policy);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddPolicyNull() {
        aclService.addPolicy(null);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddPolicyMissingUsers() {
        ACLPolicy policy = buildPolicy();
        policy.setUsers(Collections.emptySet());
        aclService.addPolicy(policy);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddPolicyMissingResources() {
        ACLPolicy policy = buildPolicy();
        policy.setResources(null);
        aclService.addPolicy(policy);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testAddPolicyMissingActions() {
        ACLPolicy policy = buildPolicy();
        policy.setActions(null);
        aclService.addPolicy(policy);
    }

    @Test
    public void testRemovePolicySuccess() throws Exception {
        assertTrue(aclService.removePolicy("alice", "policy-1"));
        verify(metadataProvider).removeACLPolicy("alice", "policy-1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemovePolicyEmptyUsername() {
        aclService.removePolicy("", "policy-1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testRemovePolicyEmptyPolicyId() {
        aclService.removePolicy("alice", " ");
    }

    @Test
    public void testGetUserFound() throws Exception {
        ACLUser user = buildUser();
        when(metadataProvider.getACLUser("alice")).thenReturn(Optional.of(user));
        assertSame(user, aclService.getUser("alice"));
    }

    @Test
    public void testGetUserNotFound() throws Exception {
        when(metadataProvider.getACLUser("bob")).thenReturn(Optional.empty());
        assertNull(aclService.getUser("bob"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetUserEmptyName() {
        aclService.getUser("");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetUserProviderFailure() throws Exception {
        when(metadataProvider.getACLUser(anyString())).thenThrow(new RuntimeException("failed"));
        aclService.getUser("alice");
    }

    @Test
    public void testCheckPermissionAllowed() throws Exception {
        when(metadataProvider.checkACLPermission("alice", "Topic:test", "PUB")).thenReturn(true);
        assertTrue(aclService.checkPermission("alice", "Topic:test", "PUB"));
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCheckPermissionEmptyArguments() {
        aclService.checkPermission("alice", "", "PUB");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testCheckPermissionProviderFailure() throws Exception {
        when(metadataProvider.checkACLPermission(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("failed"));
        aclService.checkPermission("alice", "Topic:test", "PUB");
    }
}
