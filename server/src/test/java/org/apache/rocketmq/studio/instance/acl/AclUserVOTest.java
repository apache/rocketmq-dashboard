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
    void toStringShouldIncludePermissionAndWhitelistFields() {
        AclUserVO user = AclUserVO.builder()
            .username("tencent-role")
            .accessKey("ak-secret")
            .secretKey("sk-secret")
            .permRead(true)
            .permWrite(false)
            .whiteRemoteAddress("10.0.0.0/8")
            .build();

        String value = user.toString();

        assertThat(value).contains("username=tencent-role");
        assertThat(value).contains("permRead=true");
        assertThat(value).contains("permWrite=false");
        assertThat(value).contains("whiteRemoteAddress=10.0.0.0/8");
        assertThat(value).doesNotContain("ak-secret");
        assertThat(value).doesNotContain("sk-secret");
    }

    @Test
    void builderShouldRoundTripPermissionFieldsAndClusters() {
        AclUserVO user = AclUserVO.builder()
            .id(9L)
            .username("plain-account")
            .accessKey("ak-1")
            .secretKey("sk-1")
            .admin(false)
            .clusters(List.of("prod", "staging"))
            .permRead(null)
            .permWrite(true)
            .whiteRemoteAddress("192.168.1.0/24")
            .build();

        assertThat(user.getId()).isEqualTo(9L);
        assertThat(user.getUsername()).isEqualTo("plain-account");
        assertThat(user.getClusters()).containsExactly("prod", "staging");
        assertThat(user.getPermWrite()).isTrue();
        assertThat(user.getPermRead()).isNull();
        assertThat(user.getWhiteRemoteAddress()).isEqualTo("192.168.1.0/24");
    }
}
