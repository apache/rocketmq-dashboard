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

    @Test
    void toStringOmitsTheTokenFieldEntirelyTest() {
        LoginVO response = LoginVO.builder()
            .token("studio-jwt-secret")
            .expiresIn(3600)
            .build();

        String value = response.toString();

        assertThat(value).doesNotContain("token").doesNotContain("studio-jwt-secret");
    }

    @Test
    void serializationOmitsNullTokensAndIncludesPresentOnes() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper =
                new com.fasterxml.jackson.databind.ObjectMapper();

        LoginVO withoutToken = LoginVO.builder()
            .expiresIn(3600)
            .user(LoginVO.UserInfo.builder().username("admin").admin(true).build())
            .build();
        String nullJson = mapper.writeValueAsString(withoutToken);
        assertThat(nullJson).doesNotContain("token").contains("\"expiresIn\":3600")
                .contains("\"username\":\"admin\"");

        LoginVO withToken = LoginVO.builder().token("studio-token").expiresIn(3600).build();
        String tokenJson = mapper.writeValueAsString(withToken);
        assertThat(tokenJson).contains("\"token\":\"studio-token\"");
    }

    @Test
    void dataEqualityCoversTheNestedUserInfoTest() {
        LoginVO first = LoginVO.builder()
            .token("t1")
            .expiresIn(3600)
            .user(LoginVO.UserInfo.builder().userId(1L).username("admin").admin(true).build())
            .build();
        LoginVO same = LoginVO.builder()
            .token("t1")
            .expiresIn(3600)
            .user(LoginVO.UserInfo.builder().userId(1L).username("admin").admin(true).build())
            .build();
        LoginVO nonAdmin = LoginVO.builder()
            .token("t1")
            .expiresIn(3600)
            .user(LoginVO.UserInfo.builder().userId(1L).username("admin").admin(false).build())
            .build();

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(nonAdmin);
    }
}
