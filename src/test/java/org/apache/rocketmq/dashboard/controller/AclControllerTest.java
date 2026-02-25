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
import org.apache.rocketmq.auth.authentication.enums.UserStatus;
import org.apache.rocketmq.auth.authentication.enums.UserType;
import org.apache.rocketmq.dashboard.model.UserInfoDto;
import org.apache.rocketmq.dashboard.model.request.UserCreateRequest;
import org.apache.rocketmq.dashboard.model.request.UserInfoParam;
import org.apache.rocketmq.dashboard.model.request.UserUpdateRequest;
import org.apache.rocketmq.dashboard.service.impl.AclServiceImpl;
import org.apache.rocketmq.dashboard.support.GlobalExceptionHandler;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class AclControllerTest extends BaseControllerTest {

    @Mock
    private AclServiceImpl aclService;

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
        // Prepare test data
        String clusterName = "test-cluster";
        String brokerName = "localhost:10911";
        List<UserInfoDto> expectedUsers = Arrays.asList(
                new UserInfoDto("user1", "password1", "super","enable"),
                new UserInfoDto("user2", "password2", "super","enable")
        );

        // Mock service behavior
        when(aclService.listUsers(clusterName, brokerName)).thenReturn(expectedUsers);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.get("/acl/users.query")
                        .param("clusterName", clusterName)
                        .param("brokerName", brokerName))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    List<UserInfoDto> actualUsers = gson.fromJson(result.getResponse().getContentAsString(), List.class);
                    // Due to Gson's deserialization of List to LinkedTreeMap, direct assertEquals on List<UserInfo> won't work easily.
                    // A more robust comparison would involve iterating or using a custom matcher if UserInfoDto doesn't override equals/hashCode.
                    // For simplicity, let's assume UserInfoDto has proper equals/hashCode for now or convert to JSON string for comparison.
                    assertEquals(gson.toJson(expectedUsers), result.getResponse().getContentAsString());
                });

        // Verify
        verify(aclService, times(1)).listUsers(clusterName, brokerName);
    }

    @Test
    public void testListUsersWithoutBrokerAddressAndClusterName() throws Exception {
        // Prepare test data
        List<UserInfoDto> expectedUsers = Arrays.asList(
                new UserInfoDto("user2", "password2", "super","enable")
        );

        // Mock service behavior
        when(aclService.listUsers(null, null)).thenReturn(expectedUsers);

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.get("/acl/users.query"))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals(gson.toJson(expectedUsers), result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).listUsers(null, null);
    }




    @Test
    public void testDeleteUser() throws Exception {
        // Prepare test data
        String clusterName = "test-cluster";
        String brokerName = "localhost:9092";
        String username = "user1";

        // Mock service behavior (void method)
        doNothing().when(aclService).deleteUser(clusterName, brokerName, username);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteUser.do")
                        .param("clusterName", clusterName)
                        .param("brokerName", brokerName)
                        .param("username", username))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).deleteUser(clusterName, brokerName, username);
    }

    @Test
    public void testDeleteUserWithoutBrokerAddressAndClusterName() throws Exception {
        // Prepare test data
        String username = "user1";

        // Mock service behavior (void method)
        doNothing().when(aclService).deleteUser(null, null, username);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteUser.do")
                        .param("username", username))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).deleteUser(null, null, username);
    }

    @Test
    public void testUpdateUser() throws Exception {
        // Prepare test data
        UserUpdateRequest request = new UserUpdateRequest();
        request.setClusterName("test-cluster");
        request.setBrokerName("localhost:9092");
        request.setUserInfo(new UserInfoParam("user1", "newPassword", UserStatus.ENABLE.getName(), UserType.SUPER.getName()));

        // Mock service behavior (void method)
        doNothing().when(aclService).updateUser(request.getClusterName(), request.getBrokerName(), request.getUserInfo());

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.post("/acl/updateUser.do")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gson.toJson(request)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).updateUser(request.getClusterName(), request.getBrokerName(), request.getUserInfo());
    }

    @Test
    public void testCreateUser() throws Exception {
        // Prepare test data
        UserCreateRequest request = new UserCreateRequest();
        request.setClusterName("test-cluster");
        request.setBrokerName("localhost:9092");
        request.setUserInfo(new UserInfoParam("user1", "newPassword", UserStatus.ENABLE.getName(), UserType.SUPER.getName()));

        // Mock service behavior (void method)
        doNothing().when(aclService).createUser(request.getClusterName(), request.getBrokerName(), request.getUserInfo());

        // Call controller method via MockMVC
        mockMvc.perform(MockMvcRequestBuilders.post("/acl/createUser.do")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(gson.toJson(request)))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).createUser(request.getClusterName(), request.getBrokerName(), request.getUserInfo());
    }

    @Test
    public void testDeleteAcl() throws Exception {
        // Prepare test data
        String clusterName = "test-cluster";
        String brokerName = "localhost:9092";
        String subject = "user1";
        String resource = "TOPIC:test";

        // Mock service behavior (void method)
        doNothing().when(aclService).deleteAcl(clusterName, brokerName, subject, resource);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteAcl.do")
                        .param("clusterName", clusterName)
                        .param("brokerName", brokerName)
                        .param("subject", subject)
                        .param("resource", resource))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).deleteAcl(clusterName, brokerName, subject, resource);
    }

    @Test
    public void testDeleteAclWithoutBrokerAddressAndResourceAndClusterName() throws Exception {
        // Prepare test data
        String subject = "user1";

        // Mock service behavior (void method)
        doNothing().when(aclService).deleteAcl(null, null, subject, null);

        // Call controller method via MockMVC
        mockMvc.perform(delete("/acl/deleteAcl.do")
                        .param("subject", subject))
                .andExpect(status().isOk())
                .andExpect(result -> assertEquals("true", result.getResponse().getContentAsString()));

        // Verify
        verify(aclService, times(1)).deleteAcl(null, null, subject, null);
    }



    @Override
    protected Object getTestController() {
        return aclController;
    }
}
