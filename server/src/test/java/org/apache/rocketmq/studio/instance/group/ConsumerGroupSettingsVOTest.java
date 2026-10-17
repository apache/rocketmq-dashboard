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

package org.apache.rocketmq.studio.instance.group;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ConsumerGroupSettingsVO}, the broker-side consume settings the group settings
 * dialog reads and writes: retry queue count and retry max times, and the three consume
 * switches (enabled, orderly, broadcast). All construction paths must agree and the no-args
 * path must read as "no overrides" rather than as a partially defaulted object.
 */
class ConsumerGroupSettingsVOTest {

    @Test
    void theSettingsCarryEveryField() {
        ConsumerGroupSettingsVO settings = ConsumerGroupSettingsVO.builder()
                .groupName("order-consumer-group")
                .retryQueueNums(2)
                .retryMaxTimes(16)
                .consumeEnable(true)
                .consumeMessageOrderly(true)
                .consumeBroadcastEnable(false)
                .build();

        assertThat(settings.getGroupName()).isEqualTo("order-consumer-group");
        assertThat(settings.getRetryQueueNums()).isEqualTo(2);
        assertThat(settings.getRetryMaxTimes()).isEqualTo(16);
        assertThat(settings.isConsumeEnable()).isTrue();
        assertThat(settings.isConsumeMessageOrderly()).isTrue();
        assertThat(settings.isConsumeBroadcastEnable()).isFalse();
    }

    @Test
    void allConstructionPathsAgree() {
        ConsumerGroupSettingsVO allArgs = new ConsumerGroupSettingsVO(
                "order-consumer-group", 2, 16, true, true, false);
        ConsumerGroupSettingsVO built = ConsumerGroupSettingsVO.builder()
                .groupName("order-consumer-group")
                .retryQueueNums(2)
                .retryMaxTimes(16)
                .consumeEnable(true)
                .consumeMessageOrderly(true)
                .consumeBroadcastEnable(false)
                .build();

        assertThat(allArgs).isEqualTo(built);

        ConsumerGroupSettingsVO empty = new ConsumerGroupSettingsVO();
        assertThat(empty.getGroupName()).isNull();
        assertThat(empty.getRetryQueueNums()).isZero();
        assertThat(empty.getRetryMaxTimes()).isZero();
        assertThat(empty.isConsumeEnable()).isFalse();
        assertThat(empty.isConsumeMessageOrderly()).isFalse();
        assertThat(empty.isConsumeBroadcastEnable()).isFalse();
    }
}
