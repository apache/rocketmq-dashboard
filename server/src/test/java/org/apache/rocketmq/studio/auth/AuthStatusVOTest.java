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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the status view of {@link AuthStatusVO}: a fresh status reads as not login-required and
 * not authenticated, and the nested user round-trips through the builder.
 */
class AuthStatusVOTest {

    @Test
    void aFreshStatusIsNeitherRequiredNorAuthenticated() {
        AuthStatusVO status = new AuthStatusVO();
        assertThat(status.isLoginRequired()).isFalse();
        assertThat(status.isAuthenticated()).isFalse();
        assertThat(status.getUser()).isNull();
    }

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        LoginVO.UserInfo user = new LoginVO.UserInfo();
        user.setUserId(7L);
        user.setUsername("alice");
        user.setAdmin(true);

        AuthStatusVO status = AuthStatusVO.builder()
                .loginRequired(true)
                .authenticated(true)
                .user(user)
                .build();

        assertThat(status.isLoginRequired()).isTrue();
        assertThat(status.isAuthenticated()).isTrue();
        assertThat(status.getUser().getUserId()).isEqualTo(7L);
        assertThat(status.getUser().getUsername()).isEqualTo("alice");
        assertThat(status.getUser().isAdmin()).isTrue();
    }
}
