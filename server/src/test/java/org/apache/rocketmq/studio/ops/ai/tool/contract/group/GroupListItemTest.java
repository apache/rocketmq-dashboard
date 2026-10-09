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
package org.apache.rocketmq.studio.ops.ai.tool.contract.group;

import org.apache.rocketmq.studio.common.domain.enums.ConsumeType;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link GroupListItem}: the tool-side listing projection of a consumer group. The
 * subscribedTopics null-safety is the contract — a group whose subscription list has not been
 * fetched yet must list with an empty list, not null and not a crash.
 */
class GroupListItemTest {

    @Test
    void mapsEveryListingField() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders-consumer");
        group.setClusterId("instance-a");
        group.setSubscriptionMode(SubscriptionMode.Push);
        group.setConsumeType(ConsumeType.CLUSTERING);
        group.setRetryMaxTimes(16);
        group.setOnlineInstances(3);
        group.setTotalLag(42_000L);
        group.setSubscribedTopics(List.of("orders", "payments"));

        GroupListItem item = GroupListItem.from(group);

        assertThat(item.name()).isEqualTo("orders-consumer");
        assertThat(item.clusterId()).isEqualTo("instance-a");
        assertThat(item.subscriptionMode()).isEqualTo(SubscriptionMode.Push);
        assertThat(item.consumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(item.retryMaxTimes()).isEqualTo(16);
        assertThat(item.onlineInstances()).isEqualTo(3);
        assertThat(item.totalLag()).isEqualTo(42_000L);
        assertThat(item.subscribedTopics()).containsExactly("orders", "payments");
    }

    @Test
    void aGroupWithoutSubscriptionsListsWithAnEmptyList() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("fresh");
        group.setSubscribedTopics(null);

        GroupListItem item = GroupListItem.from(group);

        assertThat(item.subscribedTopics()).isNotNull().isEmpty();
        assertThat(item.name()).isEqualTo("fresh");
        assertThat(item.onlineInstances()).isZero();
    }
}
