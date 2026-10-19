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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the paged message view of {@link MessageQueryPageVO}: the page envelope round-trips
 * including the honesty flag - a page may be complete even when it is short, and truncated
 * even when it is full.
 */
class MessageQueryPageVOTest {

    @Test
    void thePageEnvelopeRoundTrips() {
        MessageRecordVO record = MessageRecordVO.builder().msgId("msg-1").build();
        MessageQueryPageVO page = MessageQueryPageVO.builder()
                .items(List.of(record))
                .total(42L)
                .page(2)
                .size(20)
                .resultMayBeTruncated(true)
                .build();

        assertThat(page.getItems()).containsExactly(record);
        assertThat(page.getTotal()).isEqualTo(42L);
        assertThat(page.getPage()).isEqualTo(2);
        assertThat(page.getSize()).isEqualTo(20);
        assertThat(page.isResultMayBeTruncated()).isTrue();
    }

    @Test
    void aFreshPageIsCompleteAndEmpty() {
        MessageQueryPageVO page = new MessageQueryPageVO();
        assertThat(page.getItems()).isNull();
        assertThat(page.getTotal()).isZero();
        assertThat(page.isResultMayBeTruncated()).isFalse();
    }
}
