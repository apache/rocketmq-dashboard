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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicQueueShrinkGuardEngineTest {

    private TopicQueueShrinkGuardEngine engine;

    @BeforeEach
    void setUp() {
        engine = new TopicQueueShrinkGuardEngine();
    }

    @Test
    void testPrecheckExpansionOrNoOp() {
        TopicShrinkPrecheckVO report = engine.precheckQueueShrink("TopicTest", 8, 16, List.of());
        assertNotNull(report);
        assertTrue(report.isSafeToShrink());
        assertEquals(8, report.getCurrentQueueNum());
        assertEquals(16, report.getTargetQueueNum());
        assertTrue(report.getWarnings().get(0).contains("expansion or no-op"));
        assertEquals(1, report.getRecommendedSteps().size());
        assertEquals("DIRECT_APPLY", report.getRecommendedSteps().get(0).getAction());
    }

    @Test
    void testPrecheckInvalidZeroOrNegativeTarget() {
        TopicShrinkPrecheckVO report1 = engine.precheckQueueShrink("TopicTest", 8, 0, List.of());
        assertNotNull(report1);
        assertFalse(report1.isSafeToShrink());
        assertTrue(report1.getWarnings().get(0).contains("must be positive"));

        TopicShrinkPrecheckVO report2 = engine.precheckQueueShrink("TopicTest", 8, -4, List.of());
        assertNotNull(report2);
        assertFalse(report2.isSafeToShrink());
        assertTrue(report2.getWarnings().get(0).contains("must be positive"));
    }

    @Test
    void testPrecheckSafeShrinkWithZeroLagOnTruncatedQueues() {
        List<TopicQueueStatsVO> stats = List.of(
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(0).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(1).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(2).minOffset(50L).maxOffset(50L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(3).minOffset(10L).maxOffset(10L).build()
        );

        TopicShrinkPrecheckVO report = engine.precheckQueueShrink("TopicTest", 4, 2, stats);
        assertNotNull(report);
        assertTrue(report.isSafeToShrink());
        assertEquals(0L, report.getTotalUnconsumedMessagesOnTruncatedQueues());
        assertTrue(report.getTruncatedQueues().isEmpty());
        assertEquals(1, report.getRecommendedSteps().size());
        assertEquals("DIRECT_SHRINK", report.getRecommendedSteps().get(0).getAction());
    }

    @Test
    void testPrecheckDangerousShrinkWithBacklog() {
        List<TopicQueueStatsVO> stats = List.of(
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(0).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(1).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(2).minOffset(50L).maxOffset(150L).build(), // 100 lag
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(3).minOffset(10L).maxOffset(60L).build()   // 50 lag
        );

        TopicShrinkPrecheckVO report = engine.precheckQueueShrink("TopicTest", 4, 2, stats);
        assertNotNull(report);
        assertFalse(report.isSafeToShrink());
        assertEquals(150L, report.getTotalUnconsumedMessagesOnTruncatedQueues());
        assertEquals(2, report.getTruncatedQueues().size());
        assertEquals(2, report.getTruncatedQueues().get(0).getQueueId());
        assertEquals(100L, report.getTruncatedQueues().get(0).getUnconsumedLag());
        assertTrue(report.getWarnings().get(0).contains("DANGER: Truncating queues"));
        assertEquals(3, report.getRecommendedSteps().size());
        assertEquals("SET_WRITE_QUEUE_NUM_FIRST", report.getRecommendedSteps().get(0).getAction());
        assertEquals("WAIT_CONSUMER_CATCHUP", report.getRecommendedSteps().get(1).getAction());
        assertEquals("FINALIZE_READ_QUEUE_SHRINK", report.getRecommendedSteps().get(2).getAction());
    }

    @Test
    void testPrecheckMultiBrokerTruncatedQueues() {
        List<TopicQueueStatsVO> stats = new ArrayList<>();
        // broker-a queues 0..7
        for (int i = 0; i < 8; i++) {
            stats.add(TopicQueueStatsVO.builder()
                    .brokerName("broker-a")
                    .queueId(i)
                    .minOffset(0L)
                    .maxOffset(i >= 4 ? 20_000L : 100L) // queues >= 4 have 20000 backlog
                    .build());
        }
        // broker-b queues 0..7
        for (int i = 0; i < 8; i++) {
            stats.add(TopicQueueStatsVO.builder()
                    .brokerName("broker-b")
                    .queueId(i)
                    .minOffset(0L)
                    .maxOffset(i >= 4 ? 500L : 50L) // queues >= 4 have 500 backlog
                    .build());
        }

        TopicShrinkPrecheckVO report = engine.precheckQueueShrink("TopicOrders", 8, 4, stats);
        assertNotNull(report);
        assertFalse(report.isSafeToShrink());
        // 4 queues on broker-a (4 * 20000) + 4 queues on broker-b (4 * 500) = 82000
        assertEquals(82000L, report.getTotalUnconsumedMessagesOnTruncatedQueues());
        assertEquals(8, report.getTruncatedQueues().size());
        assertTrue(report.getTruncatedQueues().stream().anyMatch(q -> "CRITICAL".equals(q.getRiskLevel())));
    }

    @Test
    void testPrecheckNullQueueStatsList() {
        TopicShrinkPrecheckVO report = engine.precheckQueueShrink("TopicNull", 8, 4, null);
        assertNotNull(report);
        assertTrue(report.isSafeToShrink());
        assertEquals(0L, report.getTotalUnconsumedMessagesOnTruncatedQueues());
        assertTrue(report.getTruncatedQueues().isEmpty());
    }
}
