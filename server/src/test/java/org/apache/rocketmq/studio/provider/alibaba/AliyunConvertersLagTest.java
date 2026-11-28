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
package org.apache.rocketmq.studio.provider.alibaba;

import com.aliyun.sdk.service.rocketmq20220801.models.DataLiteTopicLagMapValue;
import com.aliyun.sdk.service.rocketmq20220801.models.DataTopicLagMapValue;
import com.aliyun.sdk.service.rocketmq20220801.models.GetConsumerGroupLagResponseBody;
import org.apache.rocketmq.studio.provider.apache.ConsumerLagResolver;
import org.apache.rocketmq.studio.instance.group.QueueProgressVO;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AliyunConvertersLagTest {

    @Test
    void topicBreakdownShouldNotBeCountedAgainAsAnAggregateRow() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .topicLagMap(Map.of(
                        "orders", DataTopicLagMapValue.builder().readyCount(40L).build(),
                        "payments", DataTopicLagMapValue.builder().readyCount(60L).build()))
                .totalLag(GetConsumerGroupLagResponseBody.TotalLag.builder().readyCount(100L).build())
                .build();

        List<QueueProgressVO> rows = AliyunConverters.toQueueProgressRows(data);

        assertThat(rows).extracting(QueueProgressVO::getTopic)
                .containsExactlyInAnyOrder("orders", "payments");
        assertThat(rows).noneMatch(row -> "total".equals(row.getBroker()));
        assertThat(rows.stream().mapToLong(QueueProgressVO::getDiffTotal).sum()).isEqualTo(100L);
    }

    @Test
    void aggregateShouldRemainAvailableWhenTopicBreakdownIsMissing() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .totalLag(GetConsumerGroupLagResponseBody.TotalLag.builder().readyCount(100L).build())
                .build();

        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> {
                    assertThat(row.getBroker()).isEqualTo("total");
                    assertThat(row.getDiffTotal()).isEqualTo(100L);
                });
    }

    @Test
    void queueOffsetsShouldBeUnknownWhenOnlyTheTopicLagIsKnownTest() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .topicLagMap(Map.of("orders", DataTopicLagMapValue.builder().readyCount(40L).build()))
                .build();

        // The Aliyun API reports the lag per topic and no per-queue offsets, so the row must not
        // claim offsets of zero next to the real lag.
        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> {
                    assertThat(row.getBrokerOffset()).isEqualTo(QueueProgressVO.UNKNOWN_OFFSET);
                    assertThat(row.getConsumerOffset()).isEqualTo(QueueProgressVO.UNKNOWN_OFFSET);
                    assertThat(row.getDiffTotal()).isEqualTo(40L);
                });
    }

    @Test
    void aggregateRowOffsetsShouldBeUnknownTooTest() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .totalLag(GetConsumerGroupLagResponseBody.TotalLag.builder().readyCount(100L).build())
                .build();

        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> {
                    assertThat(row.getBroker()).isEqualTo("total");
                    assertThat(row.getBrokerOffset()).isEqualTo(QueueProgressVO.UNKNOWN_OFFSET);
                    assertThat(row.getConsumerOffset()).isEqualTo(QueueProgressVO.UNKNOWN_OFFSET);
                });
    }

    @Test
    void unreportedReadyCountShouldBeUnknownNotZeroTest() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .topicLagMap(Map.of("orders", DataTopicLagMapValue.builder().build()))
                .build();

        // A topic whose ready count the API omitted must render unknown, not a measured
        // zero backlog that reads as "fully consumed".
        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> assertThat(row.getDiffTotal())
                        .isEqualTo(ConsumerLagResolver.UNKNOWN));
    }

    @Test
    void aNullTopicLagEntryShouldBeUnknownNotZeroTest() {
        Map<String, DataTopicLagMapValue> topicLagMap = new HashMap<>();
        topicLagMap.put("orders", null);
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .topicLagMap(topicLagMap)
                .build();

        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> assertThat(row.getDiffTotal())
                        .isEqualTo(ConsumerLagResolver.UNKNOWN));
    }

    @Test
    void liteTopicBreakdownShouldNotBeDroppedTest() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .liteTopicLagMap(Map.of(
                        "lite-orders", DataLiteTopicLagMapValue.builder().readyCount(7L).build()))
                .build();

        // A group that only consumes lite topics carries no topicLagMap rows; dropping the
        // lite map reported the whole group as having no backlog at all.
        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> {
                    assertThat(row.getTopic()).isEqualTo("lite-orders");
                    assertThat(row.getBroker()).isEqualTo("topic:lite-orders");
                    assertThat(row.getDiffTotal()).isEqualTo(7L);
                });
    }

    @Test
    void liteTopicBreakdownShouldSuppressTheAggregateRowTest() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .liteTopicLagMap(Map.of(
                        "lite-orders", DataLiteTopicLagMapValue.builder().readyCount(7L).build()))
                .totalLag(GetConsumerGroupLagResponseBody.TotalLag.builder().readyCount(7L).build())
                .build();

        assertThat(AliyunConverters.toQueueProgressRows(data))
                .noneMatch(row -> "total".equals(row.getBroker()));
    }

    @Test
    void liteTopicRowsShareTheUnknownOffsetsContractTest() {
        GetConsumerGroupLagResponseBody.Data data = GetConsumerGroupLagResponseBody.Data.builder()
                .liteTopicLagMap(Map.of(
                        "lite-orders", DataLiteTopicLagMapValue.builder().build()))
                .build();

        assertThat(AliyunConverters.toQueueProgressRows(data)).singleElement()
                .satisfies(row -> {
                    assertThat(row.getBrokerOffset()).isEqualTo(QueueProgressVO.UNKNOWN_OFFSET);
                    assertThat(row.getConsumerOffset()).isEqualTo(QueueProgressVO.UNKNOWN_OFFSET);
                    assertThat(row.getDiffTotal()).isEqualTo(ConsumerLagResolver.UNKNOWN);
                });
    }

}

