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
 * Pins the store-level ACL summary of {@link AclClusterConfigVO}: the cluster identity, the
 * ACL enablement flag, the account list and its denormalized count round-trip, and a fresh
 * summary reads as ACL-disabled with no accounts.
 */
class AclClusterConfigVOTest {

    @Test
    void theSummaryRoundTrips() {
        PlainAccessConfigVO account = PlainAccessConfigVO.builder().accessKey("ak-1").build();
        AclClusterConfigVO config = AclClusterConfigVO.builder()
                .clusterId("cluster-1")
                .aclEnabled(true)
                .aclVersion("ACL 2.0")
                .globalWhiteRemoteAddresses(List.of("10.0.0.0/8"))
                .accounts(List.of(account))
                .accountCount(1)
                .build();

        assertThat(config.getClusterId()).isEqualTo("cluster-1");
        assertThat(config.isAclEnabled()).isTrue();
        assertThat(config.getAclVersion()).isEqualTo("ACL 2.0");
        assertThat(config.getGlobalWhiteRemoteAddresses()).containsExactly("10.0.0.0/8");
        assertThat(config.getAccounts()).hasSize(1);
        assertThat(config.getAccountCount()).isEqualTo(1);
    }

    @Test
    void aFreshSummaryIsDisabledWithNoAccounts() {
        AclClusterConfigVO config = new AclClusterConfigVO();
        assertThat(config.isAclEnabled()).isFalse();
        assertThat(config.getAccounts()).isNull();
        assertThat(config.getAccountCount()).isZero();
    }
}
