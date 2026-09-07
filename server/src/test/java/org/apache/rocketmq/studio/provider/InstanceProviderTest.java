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
package org.apache.rocketmq.studio.provider;

import java.util.Arrays;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.enums.SubscriptionMode;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerPageVO;
import org.apache.rocketmq.studio.instance.topic.TopicConsumerVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class InstanceProviderTest {

    @Test
    public void getTopicConsumersPageShouldHandleLargePageNumberTest() {
        InstanceProvider provider = mock(InstanceProvider.class);
        when(provider.getTopicConsumers("instance-a", "orders")).thenReturn(Arrays.asList(
                TopicConsumerVO.builder().group("group-a").build(),
                TopicConsumerVO.builder().group("group-b").build()));
        when(provider.getTopicConsumersPage("instance-a", "orders", Integer.MAX_VALUE, 100))
                .thenCallRealMethod();

        TopicConsumerPageVO result = provider.getTopicConsumersPage(
                "instance-a", "orders", Integer.MAX_VALUE, 100);

        assertThat(result.getItems()).isEmpty();
        assertThat(result.getTotal()).isEqualTo(2);
        assertThat(result.getPage()).isEqualTo(Integer.MAX_VALUE);
        assertThat(result.getPageSize()).isEqualTo(100);
    }

    @Test
    public void listConsumerGroupsPageShouldFilterBySubscriptionModeBeforePagination() {
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO push = new ConsumerGroupVO();
        push.setName("push-group");
        push.setSubscriptionMode(SubscriptionMode.Push);
        ConsumerGroupVO pop = new ConsumerGroupVO();
        pop.setName("pop-group");
        pop.setSubscriptionMode(SubscriptionMode.Pop);
        ConsumerGroupVO legacy = new ConsumerGroupVO();
        legacy.setName("legacy-group");
        when(provider.listConsumerGroups("instance-a", "orders"))
                .thenReturn(Arrays.asList(push, pop, legacy));
        when(provider.listConsumerGroupsPage("instance-a", "orders", "Pop", 1, 20))
                .thenCallRealMethod();

        PageResult<ConsumerGroupVO> result =
                provider.listConsumerGroupsPage("instance-a", "orders", "Pop", 1, 20);

        assertThat(result.getItems()).extracting(ConsumerGroupVO::getName)
                .containsExactly("pop-group");
        assertThat(result.getTotal()).isEqualTo(1);
    }

    @Test
    public void listConsumerGroupsPageShouldTreatNullSubscriptionModeAsPush() {
        InstanceProvider provider = mock(InstanceProvider.class);
        ConsumerGroupVO push = new ConsumerGroupVO();
        push.setName("push-group");
        push.setSubscriptionMode(SubscriptionMode.Push);
        ConsumerGroupVO pop = new ConsumerGroupVO();
        pop.setName("pop-group");
        pop.setSubscriptionMode(SubscriptionMode.Pop);
        ConsumerGroupVO legacy = new ConsumerGroupVO();
        legacy.setName("legacy-group");
        when(provider.listConsumerGroups("instance-a", null))
                .thenReturn(Arrays.asList(push, pop, legacy));
        when(provider.listConsumerGroupsPage("instance-a", null, "Push", 2, 1))
                .thenCallRealMethod();

        PageResult<ConsumerGroupVO> result =
                provider.listConsumerGroupsPage("instance-a", null, "Push", 2, 1);

        assertThat(result.getItems()).extracting(ConsumerGroupVO::getName)
                .containsExactly("legacy-group");
        assertThat(result.getTotal()).isEqualTo(2);
    }
}
