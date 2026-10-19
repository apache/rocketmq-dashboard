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

package org.apache.rocketmq.studio.instance.topic;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link TopicConsumerPageVO}, one page of the topic's consumer groups: the item slice
 * plus the server-side totals. The pagination controls read {@code total} — not
 * {@code items.size()} — so a last short page must still carry the full count, and the page
 * coordinates must travel untouched next to the slice.
 */
class TopicConsumerPageVOTest {

    @Test
    void aPageCarriesItsSliceAndTheServerSideTotal() {
        TopicConsumerVO first = TopicConsumerVO.builder()
                .group("order-consumer-group")
                .diffTotal(120)
                .build();
        TopicConsumerVO second = TopicConsumerVO.builder()
                .group("payment-consumer-group")
                .diffTotal(0)
                .build();

        TopicConsumerPageVO page = TopicConsumerPageVO.builder()
                .items(List.of(first, second))
                .total(47)
                .page(3)
                .pageSize(20)
                .build();

        assertThat(page.getItems()).hasSize(2);
        assertThat(page.getItems()).extracting(TopicConsumerVO::getGroup)
                .containsExactly("order-consumer-group", "payment-consumer-group");
        assertThat(page.getTotal()).isEqualTo(47);
        assertThat(page.getPage()).isEqualTo(3);
        assertThat(page.getPageSize()).isEqualTo(20);
    }

    @Test
    void aShortLastPageStillCarriesTheFullTotal() {
        TopicConsumerPageVO lastPage = TopicConsumerPageVO.builder()
                .items(List.of(TopicConsumerVO.builder().group("order-consumer-group").build()))
                .total(41)
                .page(3)
                .pageSize(20)
                .build();

        assertThat(lastPage.getItems()).hasSize(1);
        assertThat(lastPage.getTotal()).isEqualTo(41);

        TopicConsumerPageVO emptyPage = TopicConsumerPageVO.builder()
                .items(List.of())
                .total(0)
                .page(1)
                .pageSize(20)
                .build();
        assertThat(emptyPage.getItems()).isEmpty();
        assertThat(emptyPage.getTotal()).isZero();
    }

    @Test
    void identicalPagesAreValueEqual() {
        TopicConsumerPageVO left = TopicConsumerPageVO.builder()
                .items(List.of(TopicConsumerVO.builder().group("order-consumer-group").build()))
                .total(1)
                .page(1)
                .pageSize(20)
                .build();
        TopicConsumerPageVO right = TopicConsumerPageVO.builder()
                .items(List.of(TopicConsumerVO.builder().group("order-consumer-group").build()))
                .total(1)
                .page(1)
                .pageSize(20)
                .build();

        assertThat(left).isEqualTo(right);
    }
}
