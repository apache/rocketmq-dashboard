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
package org.apache.rocketmq.studio.auth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthStatusVOTest {

    @Test
    void builderDefaultsDescribeLoggedOutState() {
        AuthStatusVO vo = AuthStatusVO.builder().build();

        assertFalse(vo.isLoginRequired());
        assertFalse(vo.isAuthenticated());
        assertNull(vo.getUser());
    }

    @Test
    void allArgsCarryAuthenticatedState() {
        LoginVO.UserInfo user = LoginVO.UserInfo.builder()
            .userId(7L)
            .username("alice")
            .admin(true)
            .build();

        AuthStatusVO vo = AuthStatusVO.builder()
            .loginRequired(true)
            .authenticated(true)
            .user(user)
            .build();

        assertTrue(vo.isLoginRequired());
        assertTrue(vo.isAuthenticated());
        assertEquals(7L, vo.getUser().getUserId());
        assertEquals("alice", vo.getUser().getUsername());
        assertTrue(vo.getUser().isAdmin());
    }
}
