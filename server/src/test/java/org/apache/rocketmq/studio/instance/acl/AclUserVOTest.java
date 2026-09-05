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
package org.apache.rocketmq.studio.instance.acl;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AclUserVOTest {

    @Test
    void toStringShouldNotExposeCredentials() {
        AclUserVO user = AclUserVO.builder()
            .id(1L)
            .username("ops-admin")
            .accessKey("plain-access-key")
            .secretKey("plain-secret-key")
            .admin(true)
            .clusters(List.of("prod"))
            .build();

        String value = user.toString();

        assertThat(value).contains("username=ops-admin");
        assertThat(value).contains("admin=true");
        assertThat(value).doesNotContain("plain-access-key");
        assertThat(value).doesNotContain("plain-secret-key");
    }

    @Test
    void toStringOmitsCredentialFieldNamesEntirelyTest() {
        AclUserVO user = AclUserVO.builder()
            .username("ops-admin")
            .accessKey("plain-access-key")
            .secretKey("plain-secret-key")
            .build();

        String value = user.toString();

        assertThat(value).doesNotContain("accessKey").doesNotContain("secretKey");
    }

    @Test
    void dataEqualityCoversAllFieldsTest() {
        AclUserVO first = AclUserVO.builder()
            .id(1L)
            .username("ops-admin")
            .accessKey("ak-1")
            .secretKey("sk-1")
            .admin(true)
            .clusters(List.of("prod", "dev"))
            .permRead(true)
            .permWrite(false)
            .build();
        AclUserVO same = AclUserVO.builder()
            .id(1L)
            .username("ops-admin")
            .accessKey("ak-1")
            .secretKey("sk-1")
            .admin(true)
            .clusters(List.of("prod", "dev"))
            .permRead(true)
            .permWrite(false)
            .build();

        assertThat(first).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(first).isNotEqualTo(AclUserVO.builder().username("ops-admin").build());
    }
}
