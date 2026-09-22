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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MessageTraceLifecycleAggregatorTest {

    private MessageTraceLifecycleAggregator aggregator;

    @BeforeEach
    void setUp() {
        aggregator = new MessageTraceLifecycleAggregator();
    }

    @Test
    void testAggregateNullTraceRecord() {
        MessageTraceWaterfallVO waterfall = aggregator.aggregate("msg-01", "TopicTest", null);
        assertNotNull(waterfall);
        assertEquals("msg-01", waterfall.getMsgId());
        assertEquals("NONE", waterfall.getBottleneckStage());
        assertFalse(waterfall.isCompleted());
        assertTrue(waterfall.getDiagnosticAlerts().get(0).contains("No trace nodes"));
    }

    @Test
    void testAggregateHealthyTrace() {
        long baseTime = 1720000000000L;
        TraceNodeVO pubNode = TraceNodeVO.builder()
                .title("Pub")
                .timestamp(baseTime)
                .costTime(20L)
                .status("SUCCESS")
                .description("Produce message success")
                .build();
        TraceNodeVO subNode = TraceNodeVO.builder()
                .title("Sub")
                .timestamp(baseTime + 100L) // 80ms transit
                .costTime(50L)
                .status("SUCCESS")
                .description("Consume message success")
                .build();

        TraceRecordVO traceRecord = TraceRecordVO.builder()
                .nodes(List.of(pubNode, subNode))
                .build();

        MessageTraceWaterfallVO waterfall = aggregator.aggregate("msg-01", "TopicTest", traceRecord);
        assertNotNull(waterfall);
        assertTrue(waterfall.isCompleted());
        assertEquals(20L, waterfall.getProducerSendDurationMs());
        assertEquals(80L, waterfall.getBrokerTransitDurationMs());
        assertEquals(50L, waterfall.getConsumerProcessingDurationMs());
        assertEquals(150L, waterfall.getTotalDurationMs());
        assertEquals(3, waterfall.getStages().size());
        assertEquals("NONE", waterfall.getBottleneckStage());
    }

    @Test
    void testAggregateConsumerBottleneck() {
        long baseTime = 1720000000000L;
        TraceNodeVO pubNode = TraceNodeVO.builder()
                .title("Pub")
                .timestamp(baseTime)
                .costTime(15L)
                .status("SUCCESS")
                .build();
        TraceNodeVO subNode = TraceNodeVO.builder()
                .title("Sub")
                .timestamp(baseTime + 50L)
                .costTime(2500L) // 2.5s consumer cost
                .status("SUCCESS")
                .build();

        TraceRecordVO traceRecord = TraceRecordVO.builder()
                .nodes(List.of(pubNode, subNode))
                .build();

        MessageTraceWaterfallVO waterfall = aggregator.aggregate("msg-slow-consumer", "TopicOrders", traceRecord);
        assertNotNull(waterfall);
        assertEquals("CONSUMER_EXECUTION", waterfall.getBottleneckStage());
        assertTrue(waterfall.getDiagnosticAlerts().get(0).contains("Consumer execution latency is long"));
    }

    @Test
    void testAggregateBrokerTransitBottleneck() {
        long baseTime = 1720000000000L;
        TraceNodeVO pubNode = TraceNodeVO.builder()
                .title("Pub")
                .timestamp(baseTime)
                .costTime(20L)
                .status("SUCCESS")
                .build();
        TraceNodeVO subNode = TraceNodeVO.builder()
                .title("Sub")
                .timestamp(baseTime + 5000L) // 4980ms queuing transit
                .costTime(30L)
                .status("SUCCESS")
                .build();

        TraceRecordVO traceRecord = TraceRecordVO.builder()
                .nodes(List.of(pubNode, subNode))
                .build();

        MessageTraceWaterfallVO waterfall = aggregator.aggregate("msg-lag", "TopicBacklog", traceRecord);
        assertNotNull(waterfall);
        assertEquals("BROKER_STORAGE_TRANSIT", waterfall.getBottleneckStage());
        assertTrue(waterfall.getDiagnosticAlerts().get(0).contains("High queuing transit delay"));
    }

    @Test
    void testAggregateProducerSendBottleneck() {
        long baseTime = 1720000000000L;
        TraceNodeVO pubNode = TraceNodeVO.builder()
                .title("Pub")
                .timestamp(baseTime)
                .costTime(800L) // 800ms send
                .status("SUCCESS")
                .build();

        TraceRecordVO traceRecord = TraceRecordVO.builder()
                .nodes(List.of(pubNode))
                .build();

        MessageTraceWaterfallVO waterfall = aggregator.aggregate("msg-slow-producer", "TopicOrders", traceRecord);
        assertNotNull(waterfall);
        assertFalse(waterfall.isCompleted());
        assertEquals("PRODUCER_SEND", waterfall.getBottleneckStage());
        assertTrue(waterfall.getDiagnosticAlerts().get(0).contains("Producer send latency is high"));
    }
}
