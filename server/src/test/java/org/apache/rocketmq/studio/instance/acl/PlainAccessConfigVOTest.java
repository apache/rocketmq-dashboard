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

/**
 * Pins the plain access account view of {@link PlainAccessConfigVO}: the stored account with
 * its whitelist, admin flag, default permissions and per-resource entries. A fresh account is
 * non-admin with no restrictions - the safe default for a newly provisioned account.
 */
class PlainAccessConfigVOTest {

    @Test
    void everyFieldRoundTripsThroughTheBuilder() {
        PlainAccessConfigVO account = PlainAccessConfigVO.builder()
                .accessKey("ak-1")
                .secretKey("sk-1")
                .whiteRemoteAddress("10.0.0.0/8")
                .admin(true)
                .defaultTopicPerm("PUB")
                .defaultGroupPerm("SUB")
                .topicPerms(List.of("order-*=PUB"))
                .groupPerms(List.of("cg-order-*=SUB"))
                .build();

        assertThat(account.getAccessKey()).isEqualTo("ak-1");
        assertThat(account.getSecretKey()).isEqualTo("sk-1");
        assertThat(account.getWhiteRemoteAddress()).isEqualTo("10.0.0.0/8");
        assertThat(account.isAdmin()).isTrue();
        assertThat(account.getDefaultTopicPerm()).isEqualTo("PUB");
        assertThat(account.getDefaultGroupPerm()).isEqualTo("SUB");
        assertThat(account.getTopicPerms()).containsExactly("order-*=PUB");
        assertThat(account.getGroupPerms()).containsExactly("cg-order-*=SUB");
    }

    @Test
    void aFreshAccountIsNonAdminWithNoRestrictions() {
        PlainAccessConfigVO account = new PlainAccessConfigVO();

        assertThat(account.isAdmin()).isFalse();
        assertThat(account.getWhiteRemoteAddress()).isNull();
        assertThat(account.getTopicPerms()).isNull();
        assertThat(account.getGroupPerms()).isNull();
    }
}
