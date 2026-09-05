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
package org.apache.rocketmq.studio.persistence.entity;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RmqAclUser}: the persisted plain-ACL user row must never leak its
 * access/secret keys through {@code toString}, while still comparing equal on them.
 */
class RmqAclUserTest {

    @Test
    void toStringRedactsTheCredentialKeys() {
        RmqAclUser user = new RmqAclUser();
        user.setId(1L);
        user.setUsername("ops-admin");
        user.setAccessKey("rocketmq-access-key");
        user.setSecretKey("rocketmq-secret-key");
        user.setAdmin(true);

        String value = user.toString();

        assertThat(value).contains("username=ops-admin").contains("admin=true");
        assertThat(value).doesNotContain("accessKey").doesNotContain("secretKey");
        assertThat(value).doesNotContain("rocketmq-access-key").doesNotContain("rocketmq-secret-key");
    }

    @Test
    void dataEqualityCoversTheCredentialKeys() {
        RmqAclUser first = new RmqAclUser();
        first.setId(1L);
        first.setAccessKey("ak-1");
        first.setSecretKey("sk-1");

        RmqAclUser same = new RmqAclUser();
        same.setId(1L);
        same.setAccessKey("ak-1");
        same.setSecretKey("sk-1");

        RmqAclUser rotated = new RmqAclUser();
        rotated.setId(1L);
        rotated.setAccessKey("ak-1");
        rotated.setSecretKey("sk-2");

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(rotated);
    }
}
