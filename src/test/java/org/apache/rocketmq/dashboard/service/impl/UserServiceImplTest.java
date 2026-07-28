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

import org.apache.rocketmq.auth.authentication.enums.UserType;
import org.apache.rocketmq.dashboard.admin.UserMQAdminPoolManager;
import org.apache.rocketmq.dashboard.model.User;
import org.apache.rocketmq.dashboard.service.strategy.UserContext;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class UserServiceImplTest {

    @InjectMocks
    private UserServiceImpl userService;

    @Mock
    private UserContext userContext;

    @Mock
    private UserMQAdminPoolManager userMQAdminPoolManager;

    @Mock
    private MQAdminExt mqAdminExt;

    private UserInfo buildUserInfo(String username, String password, String userType) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUsername(username);
        userInfo.setPassword(password);
        userInfo.setUserType(userType);
        return userInfo;
    }

    @Test
    public void testQueryByNameReturnsNullWhenUserNotFound() {
        when(userContext.queryByUsername("absent")).thenReturn(null);
        assertNull(userService.queryByName("absent"));
    }

    @Test
    public void testQueryByNameReturnsUser() {
        when(userContext.queryByUsername("admin"))
            .thenReturn(buildUserInfo("admin", "secret", UserType.SUPER.getName()));

        User user = userService.queryByName("admin");
        assertNotNull(user);
        assertEquals("admin", user.getName());
        assertEquals("secret", user.getPassword());
        assertEquals(UserType.SUPER.getCode(), user.getType());
    }

    @Test
    public void testQueryByNameNormalUser() {
        when(userContext.queryByUsername("normal"))
            .thenReturn(buildUserInfo("normal", "pwd", UserType.NORMAL.getName()));

        User user = userService.queryByName("normal");
        assertNotNull(user);
        assertEquals(UserType.NORMAL.getCode(), user.getType());
    }

    @Test
    public void testQueryByUsernameAndPasswordMatches() {
        when(userContext.queryByUsername("admin"))
            .thenReturn(buildUserInfo("admin", "secret", UserType.SUPER.getName()));

        User user = userService.queryByUsernameAndPassword("admin", "secret");
        assertNotNull(user);
        assertEquals("admin", user.getName());
    }

    @Test
    public void testQueryByUsernameAndPasswordMismatch() {
        when(userContext.queryByUsername("admin"))
            .thenReturn(buildUserInfo("admin", "secret", UserType.SUPER.getName()));

        assertNull(userService.queryByUsernameAndPassword("admin", "wrong"));
    }

    @Test
    public void testQueryByUsernameAndPasswordUserNotFound() {
        when(userContext.queryByUsername(anyString())).thenReturn(null);
        assertNull(userService.queryByUsernameAndPassword("nobody", "pwd"));
    }

    @Test
    public void testGetMQAdminExtForUserNullUser() throws Exception {
        try {
            userService.getMQAdminExtForUser(null);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // expected
        }
        verifyNoInteractions(userMQAdminPoolManager);
    }

    @Test
    public void testGetMQAdminExtForUser() throws Exception {
        User user = new User("admin", "secret", User.SUPER);
        when(userMQAdminPoolManager.borrowMQAdminExt("admin", "secret")).thenReturn(mqAdminExt);

        MQAdminExt result = userService.getMQAdminExtForUser(user);
        assertSame(mqAdminExt, result);
    }

    @Test
    public void testReturnMQAdminExtForUserWithNulls() {
        userService.returnMQAdminExtForUser(null, mqAdminExt);
        userService.returnMQAdminExtForUser(new User("admin", "secret", User.SUPER), null);
        verify(userMQAdminPoolManager, never()).returnMQAdminExt(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    public void testReturnMQAdminExtForUser() {
        User user = new User("admin", "secret", User.SUPER);
        userService.returnMQAdminExtForUser(user, mqAdminExt);
        verify(userMQAdminPoolManager).returnMQAdminExt("admin", mqAdminExt);
    }

    @Test
    public void testOnUserLogoutNullUser() {
        userService.onUserLogout(null);
        verifyNoInteractions(userMQAdminPoolManager);
    }

    @Test
    public void testOnUserLogout() {
        userService.onUserLogout(new User("admin", "secret", User.SUPER));
        verify(userMQAdminPoolManager).shutdownUserPool("admin");
    }
}
