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

class LoginDTOTest {

    @Test
    void toStringShouldNotExposePassword() {
        LoginDTO request = new LoginDTO();
        request.setUsername("admin");
        request.setPassword("plain-secret");

        String value = request.toString();

        assertThat(value).contains("username=admin");
        assertThat(value).doesNotContain("plain-secret");
    }

    @Test
    void dataEqualityCoversUsernameAndPasswordTest() {
        LoginDTO first = new LoginDTO();
        first.setUsername("admin");
        first.setPassword("secret-a");

        LoginDTO same = new LoginDTO();
        same.setUsername("admin");
        same.setPassword("secret-a");

        LoginDTO differentPassword = new LoginDTO();
        differentPassword.setUsername("admin");
        differentPassword.setPassword("secret-b");

        LoginDTO differentUser = new LoginDTO();
        differentUser.setUsername("reader");
        differentUser.setPassword("secret-a");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(differentPassword).isNotEqualTo(differentUser);
    }

    @Test
    void toStringOmitsThePasswordFieldEntirelyTest() {
        LoginDTO request = new LoginDTO();
        request.setUsername("admin");
        request.setPassword("plain-secret");

        String value = request.toString();

        assertThat(value).doesNotContain("password").doesNotContain("plain-secret");
    }
}
