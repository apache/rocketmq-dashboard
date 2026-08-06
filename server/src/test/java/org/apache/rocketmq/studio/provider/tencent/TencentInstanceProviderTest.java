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
package org.apache.rocketmq.studio.provider.tencent;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TencentInstanceProviderTest {

    private final TencentInstanceProvider provider = new TencentInstanceProvider();

    @Test
    void vendorShouldBeTencentTest() {
        assertThat(provider.vendor()).isEqualTo(InstanceVendor.TENCENT);
    }

    @Test
    void allOperationsShouldThrowUnsupportedTest() {
        assertThatThrownBy(() -> provider.listTopics("inst", null, null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.createTopic("inst", new TopicVO()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.updateTopic("inst", new TopicVO()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.deleteTopic("inst", "topic"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.getTopicConsumers("inst", "topic"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.listConsumerGroups("inst", null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.createConsumerGroup("inst", new ConsumerGroupVO()))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.deleteConsumerGroup("inst", "group"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.getGroupProgress("inst", "group"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.getGroupSubscriptions("inst", "group"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.resetOffset("inst", "group", 1L, "topic"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.queryMessages("inst", "topic", null, null, null, null, null))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> provider.getMessageTrace("inst", "msg"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
