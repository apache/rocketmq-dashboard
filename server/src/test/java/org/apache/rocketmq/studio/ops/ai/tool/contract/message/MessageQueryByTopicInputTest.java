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
package org.apache.rocketmq.studio.ops.ai.tool.contract.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link MessageQueryByTopicInput}: the topic-scoped message query shape. The resultLimit
 * clamping (default 20, cap 100) is the resource guard that keeps a model-issued query from
 * pulling an entire topic.
 */
class MessageQueryByTopicInputTest {

    @Test
    void anUnspecifiedLimitDefaultsToTwenty() {
        MessageQueryByTopicInput input = new MessageQueryByTopicInput("instance-a", "orders", null, 0L, 100L);

        assertThat(input.resultLimit()).isEqualTo(20);
        assertThat(input.includeBody()).isFalse();
    }

    @Test
    void anExplicitLimitBelowTheCapPassesThrough() {
        MessageQueryByTopicInput input = new MessageQueryByTopicInput(
                "instance-a", "orders", "tag", 0L, 100L, 50, true);

        assertThat(input.resultLimit()).isEqualTo(50);
        assertThat(input.includeBody()).isTrue();
    }

    @Test
    void anExplicitLimitAboveTheCapIsClampedTo100() {
        MessageQueryByTopicInput input = new MessageQueryByTopicInput(
                "instance-a", "orders", null, 0L, 100L, 10_000, false);

        assertThat(input.resultLimit()).isEqualTo(100);
    }
}
