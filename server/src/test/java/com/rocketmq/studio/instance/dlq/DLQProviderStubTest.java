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

package com.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DLQProviderStubTest {

    private final DLQProviderStub provider = new DLQProviderStub();

    @Test
    void listDLQGroupsShouldReturnSampleGroupsForAllClusters() {
        List<DLQGroupVO> groups = provider.listDLQGroups(null);

        assertThat(groups)
                .extracting(DLQGroupVO::getGroupName)
                .containsExactly("cg-order-payment", "cg-inventory-sync", "legacy-order-consumer");
        assertThat(groups)
                .allSatisfy(group -> {
                    assertThat(group.getDlqTopic()).startsWith("%DLQ%");
                    assertThat(group.getMessageCount()).isPositive();
                    assertThat(group.getLastEnqueueTime()).isNotNull();
                    assertThat(group.getStatus()).isNotBlank();
                });
    }

    @Test
    void listDLQGroupsShouldFilterByClusterId() {
        List<DLQGroupVO> groups = provider.listDLQGroups("rmq-cn-v5-prod-01");

        assertThat(groups)
                .extracting(DLQGroupVO::getGroupName)
                .containsExactly("cg-order-payment", "cg-inventory-sync");
    }

    @Test
    void listDLQGroupsShouldReturnEmptyForUnknownCluster() {
        assertThat(provider.listDLQGroups("missing-cluster")).isEmpty();
    }

    @Test
    void listDLQGroupsShouldReturnDefensiveCopies() {
        List<DLQGroupVO> firstRead = provider.listDLQGroups("rmq-cn-v5-prod-01");
        firstRead.get(0).setGroupName("mutated");

        List<DLQGroupVO> secondRead = provider.listDLQGroups("rmq-cn-v5-prod-01");

        assertThat(secondRead.get(0).getGroupName()).isEqualTo("cg-order-payment");
    }
}
