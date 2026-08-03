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

class LoginVOTest {

    @Test
    void toStringShouldNotExposeToken() {
        LoginVO response = LoginVO.builder()
            .token("studio-jwt-secret")
            .expiresIn(3600)
            .user(LoginVO.UserInfo.builder()
                .username("admin")
                .admin(true)
                .build())
            .build();

        String value = response.toString();

        assertThat(value).contains("expiresIn=3600");
        assertThat(value).contains("username=admin");
        assertThat(value).doesNotContain("studio-jwt-secret");
    }
}
