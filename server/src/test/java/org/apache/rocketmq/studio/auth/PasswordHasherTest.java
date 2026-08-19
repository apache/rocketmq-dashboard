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

class PasswordHasherTest {

    @Test
    void hashesPasswordsWithSaltAndVerifiesOnlyTheMatchingPassword() {
        PasswordHasher hasher = new PasswordHasher();

        String firstHash = hasher.hash("a-long-enough-password");
        String secondHash = hasher.hash("a-long-enough-password");

        assertThat(firstHash).startsWith("pbkdf2$");
        assertThat(firstHash).isNotEqualTo(secondHash);
        assertThat(hasher.matches("a-long-enough-password", firstHash)).isTrue();
        assertThat(hasher.matches("different-password", firstHash)).isFalse();
    }
    @Test
    void rejectsUnexpectedSaltAndDigestSizesBeforeDerivingAKey() {
        PasswordHasher hasher = new PasswordHasher();
        String validHash = hasher.hash("a-long-enough-password");
        String[] parts = validHash.split("\\$", -1);

        assertThat(hasher.matches("a-long-enough-password",
                "pbkdf2$210000$" + "A".repeat(1_000_000) + "$" + parts[3])).isFalse();
        assertThat(hasher.matches("a-long-enough-password",
                "pbkdf2$210000$" + parts[2] + "$AA==")).isFalse();
    }

}
