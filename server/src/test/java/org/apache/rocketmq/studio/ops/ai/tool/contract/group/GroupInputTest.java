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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link GroupInput}: the tool-side consumer group mutation shape. The partial-merge
 * semantics (absent values keep the current configuration) and the creation path's nullable
 * defaults are the two halves of the contract.
 */
class GroupInputTest {

    @Test
    void absentFieldsKeepTheCurrentConfigurationUnderMerge() {
        ConsumerGroupVO current = new ConsumerGroupVO();
        current.setName("orders-consumer");
        current.setRetryMaxTimes(16);
        current.setDelaySeconds(300);
        current.setSubscriptionDataType("JSON");
        GroupInput input = new GroupInput(
                "instance-a", "orders-consumer", null, null, null, null, null, null);

        ConsumerGroupVO merged = input.mergeWith(current);

        assertThat(merged.getRetryMaxTimes()).isEqualTo(16);
        assertThat(merged.getDelaySeconds()).isEqualTo(300);
        assertThat(merged.getSubscriptionDataType()).isEqualTo("JSON");
    }

    @Test
    void suppliedFieldsOverrideTheCurrentConfiguration() {
        ConsumerGroupVO current = new ConsumerGroupVO();
        current.setName("orders-consumer");
        current.setRetryMaxTimes(16);
        GroupInput input = new GroupInput(
                "instance-a", "orders-consumer",
                SubscriptionMode.Pop, ConsumeType.BROADCASTING,
                "TAG", "UNIFORM", 32, 600);

        ConsumerGroupVO merged = input.mergeWith(current);

        assertThat(merged.getSubscriptionMode()).isEqualTo(SubscriptionMode.Pop);
        assertThat(merged.getConsumeType()).isEqualTo(ConsumeType.BROADCASTING);
        assertThat(merged.getRetryMaxTimes()).isEqualTo(32);
        assertThat(merged.getDelaySeconds()).isEqualTo(600);
    }

    @Test
    void theCreationPathMapsEverySuppliedField() {
        GroupInput input = new GroupInput(
                "instance-a", "orders-consumer",
                SubscriptionMode.Push, ConsumeType.CLUSTERING,
                "JSON", "UNIFORM", 16, 300);

        ConsumerGroupVO vo = input.toConsumerGroupVO();

        assertThat(vo.getName()).isEqualTo("orders-consumer");
        assertThat(vo.getInstanceId()).isEqualTo("instance-a");
        assertThat(vo.getSubscriptionMode()).isEqualTo(SubscriptionMode.Push);
        assertThat(vo.getConsumeType()).isEqualTo(ConsumeType.CLUSTERING);
        assertThat(vo.getSubscriptionDataType()).isEqualTo("JSON");
        assertThat(vo.getDeliveryOrderType()).isEqualTo("UNIFORM");
        assertThat(vo.getRetryMaxTimes()).isEqualTo(16);
        assertThat(vo.getDelaySeconds()).isEqualTo(300);
    }

    @Test
    void theMergeStartsFromACopy() {
        ConsumerGroupVO before = new ConsumerGroupVO();
        before.setRetryMaxTimes(16);
        GroupInput input = new GroupInput(null, null, null, null, null, null, 32, null);

        input.mergeWith(before);

        assertThat(before.getRetryMaxTimes()).isEqualTo(16);
    }
}
