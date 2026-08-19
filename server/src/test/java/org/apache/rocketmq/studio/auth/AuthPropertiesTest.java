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

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class AuthPropertiesTest {

    @Test
    void configuredUsersShouldTolerateNullConfiguration() {
        AuthProperties properties = new AuthProperties();
        properties.setUsers(null);

        assertThat(properties.configuredUsers()).isEmpty();
    }

    @Test
    void configuredUsersShouldSkipMalformedRowsAndNormalizeUsername() {
        AuthProperties.User blank = new AuthProperties.User();
        blank.setUsername("  ");
        blank.setPassword("password-1");
        AuthProperties.User valid = new AuthProperties.User();
        valid.setUsername("  operator  ");
        valid.setPassword(" password-2 ");
        valid.setAdmin(true);
        AuthProperties properties = new AuthProperties();
        properties.setUsers(Arrays.asList(null, blank, valid));

        assertThat(properties.configuredUsers()).singleElement().satisfies(user -> {
            assertThat(user.getUsername()).isEqualTo("operator");
            assertThat(user.getPassword()).isEqualTo(" password-2 ");
            assertThat(user.isAdmin()).isTrue();
        });
        assertThat(valid.getUsername()).isEqualTo("  operator  ");
    }
}
