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
package org.apache.rocketmq.dashboard.controller;


import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.request.UserCreateRequest;
import org.apache.rocketmq.dashboard.model.request.UserInfoParam;
import org.apache.rocketmq.dashboard.model.request.UserUpdateRequest;
import org.apache.rocketmq.dashboard.service.AclService;
import org.apache.rocketmq.dashboard.support.GlobalExceptionHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AclControllerTest extends BaseControllerTest {

    @Mock
    private AclService aclService;

    @InjectMocks
    private AclController aclController;

    private final Gson gson = new Gson();

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(aclController).setControllerAdvice(GlobalExceptionHandler.class).build();
    }

    @Test
    public void testListUsers() throws Exception {
        // Prepare test data using new ACLUser model
        List<ACLUser> expectedUsers = new ArrayList<>();
        ACLUser user1 = new ACLUser();
        user1.setUserName("user1");
        user1.setAccessKey("password1");
        user1.setUserType("SUPER");
        user1.setStatus("ENABLE");
        expectedUsers.add(user1);

        ACLUser user2 = new ACLUser();
        user2.setUserName("user2");
        user2.setAccessKey("password2");
        user2.setUserType("SUPER");
        user2.setStatus("ENABLE");
        expectedUsers.add(user2);

        // Mock service behavior - new API: listUsers() no parameters
        when(aclService.listUsers()).thenReturn(expectedUsers);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.get("/acl/users.query"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    Type listType = new TypeToken<List<ACLUser>>() {}.getType();
                    List<ACLUser> actualUsers = gson.fromJson(result.getResponse().getContentAsString(), listType);
                    assertEquals(expectedUsers.size(), actualUsers.size());
                });

        // Verify
        verify(aclService, times(1)).listUsers();
    }

    @Test
    public void testDeleteUser() throws Exception {
        // Prepare test data
        String username = "user1";

        // Mock service behavior - new API: deleteUser(String username) returns boolean
        when(aclService.deleteUser(username)).thenReturn(true);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteUser.do")
                        .param("username", username))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).deleteUser(username);
    }

    @Test
    public void testUpdateUser() throws Exception {
        // Prepare test data
        UserUpdateRequest request = new UserUpdateRequest();
        request.setUserInfo(new UserInfoParam("user1", "newPassword", "ENABLE", "SUPER"));

        // Mock service behavior - new API: updateUser(ACLUser user) returns boolean
        when(aclService.updateUser(org.mockito.ArgumentMatchers.any(ACLUser.class))).thenReturn(true);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.post("/acl/updateUser.do")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gson.toJson(request)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).updateUser(org.mockito.ArgumentMatchers.any(ACLUser.class));
    }

    @Test
    public void testCreateUser() throws Exception {
        // Prepare test data
        UserCreateRequest request = new UserCreateRequest();
        request.setUserInfo(new UserInfoParam("user1", "newPassword", "ENABLE", "SUPER"));

        // Mock service behavior - new API: createUser(ACLUser user) returns boolean
        when(aclService.createUser(org.mockito.ArgumentMatchers.any(ACLUser.class))).thenReturn(true);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.post("/acl/createUser.do")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gson.toJson(request)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).createUser(org.mockito.ArgumentMatchers.any(ACLUser.class));
    }

    @Test
    public void testDeleteAcl() throws Exception {
        // Prepare test data
        String subject = "user1";
        String resource = "TOPIC:test";

        // Mock service behavior - new API: removePolicy(String username, String policyId) returns boolean
        when(aclService.removePolicy(subject, resource)).thenReturn(true);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteAcl.do")
                        .param("subject", subject)
                        .param("resource", resource))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).removePolicy(subject, resource);
    }

    @Test
    public void testDeleteAclWithoutResource() throws Exception {
        // Prepare test data
        String subject = "user1";

        // Mock service behavior - new API: removePolicy with null policyId
        when(aclService.removePolicy(subject, null)).thenReturn(true);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteAcl.do")
                        .param("subject", subject))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).removePolicy(subject, null);
    }

    @Test
    public void testListAcls() throws Exception {
        // Prepare test data using new ACLPolicy model
        List<ACLPolicy> expectedPolicies = new ArrayList<>();
        ACLPolicy policy = new ACLPolicy();
        policy.setPolicyId("policy1");
        policy.setPolicyName("testPolicy");
        Set<String> users = new HashSet<>();
        users.add("user1");
        policy.setUsers(users);
        Set<String> resources = new HashSet<>();
        resources.add("TOPIC:test");
        policy.setResources(resources);
        policy.setPolicyType("ALLOW");
        expectedPolicies.add(policy);

        // Mock service behavior - new API: listPolicies(String username)
        when(aclService.listPolicies("user1")).thenReturn(expectedPolicies);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.get("/acl/acls.query")
                        .param("username", "user1"))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    Type listType = new TypeToken<List<ACLPolicy>>() {}.getType();
                    List<ACLPolicy> actualPolicies = gson.fromJson(result.getResponse().getContentAsString(), listType);
                    assertEquals(expectedPolicies.size(), actualPolicies.size());
                });

        // Verify
        verify(aclService, times(1)).listPolicies("user1");
    }

    @Test
    public void testCreateAcl() throws Exception {
        // Prepare test data using PolicyRequest
        org.apache.rocketmq.dashboard.model.PolicyRequest request = new org.apache.rocketmq.dashboard.model.PolicyRequest();
        request.setSubject("user1");
        List<org.apache.rocketmq.dashboard.model.Policy> policies = new ArrayList<>();
        org.apache.rocketmq.dashboard.model.Policy p = new org.apache.rocketmq.dashboard.model.Policy();
        p.setPolicyType("ALLOW");
        List<org.apache.rocketmq.dashboard.model.Entry> entries = new ArrayList<>();
        org.apache.rocketmq.dashboard.model.Entry entry = new org.apache.rocketmq.dashboard.model.Entry();
        List<String> resourceSet = new ArrayList<>();
        resourceSet.add("TOPIC:test");
        entry.setResource(resourceSet);
        List<String> actionSet = new ArrayList<>();
        actionSet.add("PUB");
        entry.setActions(actionSet);
        entries.add(entry);
        p.setEntries(entries);
        policies.add(p);
        request.setPolicies(policies);

        // Mock service behavior - new API: addPolicy(ACLPolicy) returns boolean
        when(aclService.addPolicy(org.mockito.ArgumentMatchers.any(ACLPolicy.class))).thenReturn(true);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.post("/acl/createAcl.do")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gson.toJson(request)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).addPolicy(org.mockito.ArgumentMatchers.any(ACLPolicy.class));
    }

    @Override
    protected Object getTestController() {
        return aclController;
    }
}