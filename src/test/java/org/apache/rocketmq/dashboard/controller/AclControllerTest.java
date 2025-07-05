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


import org.apache.rocketmq.auth.authentication.enums.UserStatus;
import org.apache.rocketmq.auth.authentication.enums.UserType;
import org.apache.rocketmq.auth.authorization.enums.Decision;
import org.apache.rocketmq.dashboard.model.Policy;
import org.apache.rocketmq.dashboard.model.PolicyRequest;
import org.apache.rocketmq.dashboard.model.request.UserCreateRequest;
import org.apache.rocketmq.dashboard.model.request.UserInfoParam;
import org.apache.rocketmq.dashboard.model.request.UserUpdateRequest;
import org.apache.rocketmq.dashboard.service.impl.AclServiceImpl;
import org.apache.rocketmq.dashboard.support.GlobalExceptionHandler;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AclControllerTest extends BaseControllerTest {

    @Mock
    private AclServiceImpl aclService;

    @InjectMocks
    private AclController aclController;

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);
        mockMvc = MockMvcBuilders.standaloneSetup(aclController).setControllerAdvice(GlobalExceptionHandler.class).build();
    }


    @Test
    public void testListUsers() {
        // Prepare test data
        String brokerAddress = "localhost:10911";
        List<UserInfo> expectedUsers = Arrays.asList(
                UserInfo.of("user1", "password1", "super"),
                UserInfo.of("user2", "password2", "super")
        );

        // Mock service behavior
        when(aclService.listUsers(brokerAddress)).thenReturn(expectedUsers);

        // Call controller method
        List<UserInfo> result = aclController.listUsers(brokerAddress);

        // Verify
        assertEquals(expectedUsers, result);
        verify(aclService, times(1)).listUsers(brokerAddress);
    }

    @Test
    public void testListUsersWithoutBrokerAddress() {
        // Prepare test data
        List<UserInfo> expectedUsers = Arrays.asList(
                UserInfo.of("user1", "password1", "super")
        );

        // Mock service behavior
        when(aclService.listUsers(null)).thenReturn(expectedUsers);
        // Call controller method
        List<UserInfo> result = aclController.listUsers(null);
        // Verify
        assertEquals(expectedUsers, result);
        verify(aclService, times(1)).listUsers(null);
    }

    @Test
    public void testListAcls() {
        // Prepare test data
        String brokerAddress = "localhost:9092";
        String searchParam = "user1";
        Object expectedAcls = Arrays.asList(
                AclInfo.of("user1", List.of("READ", "test"), List.of("TOPIC:test"), List.of("localhost:10911"), Decision.ALLOW.getName())
        );

        // Mock service behavior
        when(aclService.listAcls(brokerAddress, searchParam)).thenReturn(expectedAcls);

        // Call controller method
        Object result = aclController.listAcls(brokerAddress, searchParam);

        // Verify
        assertEquals(expectedAcls, result);
        verify(aclService, times(1)).listAcls(brokerAddress, searchParam);
    }

    @Test
    public void testCreateAcl() {
        // Prepare test data
        PolicyRequest request = new PolicyRequest();
        request.setBrokerAddress("localhost:9092");
        request.setSubject("user1");
        request.setPolicies(List.of(
                new Policy()
        ));

        // Call controller method
        Object result = aclController.createAcl(request);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).createAcl(request);
    }

    @Test
    public void testDeleteUser() {
        // Prepare test data
        String brokerAddress = "localhost:9092";
        String username = "user1";

        // Call controller method
        Object result = aclController.deleteUser(brokerAddress, username);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).deleteUser(brokerAddress, username);
    }

    @Test
    public void testDeleteUserWithoutBrokerAddress() {
        // Prepare test data
        String username = "user1";

        // Call controller method
        Object result = aclController.deleteUser(null, username);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).deleteUser(null, username);
    }

    @Test
    public void testUpdateUser() {
        // Prepare test data
        UserUpdateRequest request = new UserUpdateRequest();
        request.setBrokerAddress("localhost:9092");
        request.setUserInfo(new UserInfoParam("user1", "newPassword", UserStatus.ENABLE.getName(), UserType.SUPER.getName()));

        // Call controller method
        Object result = aclController.updateUser(request);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).updateUser(request.getBrokerAddress(), request.getUserInfo());
    }

    @Test
    public void testCreateUser() {
        // Prepare test data
        UserCreateRequest request = new UserCreateRequest();
        request.setBrokerAddress("localhost:9092");
        request.setUserInfo(new UserInfoParam("user1", "newPassword", UserStatus.ENABLE.getName(), UserType.SUPER.getName()));

        // Call controller method
        Object result = aclController.createUser(request);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).createUser(request.getBrokerAddress(), request.getUserInfo());
    }

    @Test
    public void testDeleteAcl() {
        // Prepare test data
        String brokerAddress = "localhost:9092";
        String subject = "user1";
        String resource = "TOPIC:test";

        // Call controller method
        Object result = aclController.deleteAcl(brokerAddress, subject, resource);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).deleteAcl(brokerAddress, subject, resource);
    }

    @Test
    public void testDeleteAclWithoutBrokerAddressAndResource() {
        // Prepare test data
        String subject = "user1";

        // Call controller method
        Object result = aclController.deleteAcl(null, subject, null);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).deleteAcl(null, subject, null);
    }

    @Test
    public void testUpdateAcl() {
        // Prepare test data
        PolicyRequest request = new PolicyRequest();
        request.setBrokerAddress("localhost:9092");
        request.setSubject("user1");
        request.setPolicies(List.of(
                new Policy()
        ));

        // Call controller method
        Object result = aclController.updateAcl(request);

        // Verify
        assertEquals(true, result);
        verify(aclService, times(1)).updateAcl(request);
    }

    @Override
    protected Object getTestController() {
        return aclController;
    }
}
