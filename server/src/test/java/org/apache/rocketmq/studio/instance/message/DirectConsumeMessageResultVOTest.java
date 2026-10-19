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

package org.apache.rocketmq.studio.instance.message;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link DirectConsumeMessageResultVO}, the outcome of consuming a message straight
 * from the dashboard: what the consume call answered, the remark under it, what it cost, and
 * whether the consume was order-bound and auto-committed — the two flags the result dialog
 * badges by. It had no test.
 */
class DirectConsumeMessageResultVOTest {

    @Test
    void aResultCarriesTheConsumeOutcome() {
        DirectConsumeMessageResultVO result = DirectConsumeMessageResultVO.builder()
                .consumeResult("CONSUME_SUCCESS")
                .remark("handled by dashboard client")
                .spentTimeMillis(42)
                .order(true)
                .autoCommit(false)
                .build();

        assertThat(result.getConsumeResult()).isEqualTo("CONSUME_SUCCESS");
        assertThat(result.getRemark()).isEqualTo("handled by dashboard client");
        assertThat(result.getSpentTimeMillis()).isEqualTo(42);
        assertThat(result.isOrder()).isTrue();
        assertThat(result.isAutoCommit()).isFalse();
    }

    @Test
    void identicalOutcomesAreValueEqual() {
        DirectConsumeMessageResultVO left = DirectConsumeMessageResultVO.builder()
                .consumeResult("CONSUME_SUCCESS")
                .spentTimeMillis(7)
                .order(false)
                .autoCommit(true)
                .build();
        DirectConsumeMessageResultVO right = DirectConsumeMessageResultVO.builder()
                .consumeResult("CONSUME_SUCCESS")
                .spentTimeMillis(7)
                .order(false)
                .autoCommit(true)
                .build();

        assertThat(left).isEqualTo(right);
    }
}
