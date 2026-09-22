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

import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.body.ProcessQueueInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.TreeMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsumerHangDiagnosticEngineTest {

    private ConsumerHangDiagnosticEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ConsumerHangDiagnosticEngine();
    }

    @Test
    void testAnalyzeNullRunningInfo() {
        ConsumerHangReportVO report = engine.analyze("inst-1", "GroupA", "client-1", null);
        assertNotNull(report);
        assertEquals("UNKNOWN", report.getOverallHealth());
        assertTrue(report.getDiagnosticSuggestions().get(0).contains("No ConsumerRunningInfo"));
    }

    @Test
    void testAnalyzeEmptyQueueTable() {
        ConsumerRunningInfo info = new ConsumerRunningInfo();
        info.setMqTable(new TreeMap<>());

        ConsumerHangReportVO report = engine.analyze("inst-1", "GroupA", "client-1", info);
        assertNotNull(report);
        assertEquals(0, report.getTotalQueuesAnalyzed());
        assertTrue(report.getDiagnosticSuggestions().get(0).contains("no allocated message queues"));
    }

    @Test
    void testAnalyzeHealthyConsumption() {
        ConsumerRunningInfo info = new ConsumerRunningInfo();
        TreeMap<MessageQueue, ProcessQueueInfo> mqTable = new TreeMap<>();
        MessageQueue mq = new MessageQueue("TopicTest", "broker-a", 0);
        ProcessQueueInfo pq = new ProcessQueueInfo();
        pq.setCachedMsgCount(10);
        pq.setCachedMsgSizeInMiB(1);
        pq.setLastPullTimestamp(System.currentTimeMillis());
        pq.setLastConsumeTimestamp(System.currentTimeMillis() - 5_000); // 5s ago
        mqTable.put(mq, pq);
        info.setMqTable(mqTable);
        info.setJstack("TID: 100 STATE: RUNNABLE");

        ConsumerHangReportVO report = engine.analyze("inst-1", "GroupA", "client-1", info);
        assertNotNull(report);
        assertEquals("HEALTHY", report.getOverallHealth());
        assertEquals(1, report.getTotalQueuesAnalyzed());
        assertEquals(0, report.getStalledQueueCount());
        assertTrue(report.getQueueFindings().isEmpty());
    }

    @Test
    void testAnalyzeCriticalBusinessListenerBlocked() {
        ConsumerRunningInfo info = new ConsumerRunningInfo();
        TreeMap<MessageQueue, ProcessQueueInfo> mqTable = new TreeMap<>();
        MessageQueue mq = new MessageQueue("TopicOrders", "broker-a", 1);
        ProcessQueueInfo pq = new ProcessQueueInfo();
        pq.setCachedMsgCount(20);
        pq.setCachedMsgSizeInMiB(2);
        pq.setLastPullTimestamp(System.currentTimeMillis() - 10_000);
        // Stalled for 150 seconds (> 120s critical)
        pq.setLastConsumeTimestamp(System.currentTimeMillis() - 150_000);
        mqTable.put(mq, pq);
        info.setMqTable(mqTable);
        info.setJstack("TID: 101 STATE: BLOCKED\njava.lang.Thread.State: BLOCKED\njava.lang.Thread.State: WAITING (parking)\njava.lang.Thread.State: BLOCKED");

        ConsumerHangReportVO report = engine.analyze("inst-1", "GroupA", "client-1", info);
        assertNotNull(report);
        assertEquals("CRITICAL", report.getOverallHealth());
        assertEquals(1, report.getStalledQueueCount());
        assertEquals(3, report.getBlockedThreadCount());
        assertEquals(1, report.getQueueFindings().size());
        assertEquals("BUSINESS_LISTENER_BLOCKED", report.getQueueFindings().get(0).getRootCause());
        assertEquals("CRITICAL", report.getQueueFindings().get(0).getSeverity());
    }

    @Test
    void testAnalyzeFlowControlAndDroppedQueue() {
        ConsumerRunningInfo info = new ConsumerRunningInfo();
        TreeMap<MessageQueue, ProcessQueueInfo> mqTable = new TreeMap<>();

        MessageQueue mq1 = new MessageQueue("TopicHuge", "broker-a", 0);
        ProcessQueueInfo pq1 = new ProcessQueueInfo();
        pq1.setCachedMsgCount(800); // Exceeds 500
        pq1.setLastConsumeTimestamp(System.currentTimeMillis() - 40_000); // 40s stall
        mqTable.put(mq1, pq1);

        MessageQueue mq2 = new MessageQueue("TopicDropped", "broker-b", 1);
        ProcessQueueInfo pq2 = new ProcessQueueInfo();
        pq2.setDroped(true);
        pq2.setCachedMsgCount(15);
        pq2.setLastConsumeTimestamp(System.currentTimeMillis() - 40_000); // 40s stall
        mqTable.put(mq2, pq2);

        info.setMqTable(mqTable);

        ConsumerHangReportVO report = engine.analyze("inst-1", "GroupA", "client-1", info);
        assertNotNull(report);
        assertEquals("WARNING", report.getOverallHealth());
        assertEquals(2, report.getStalledQueueCount());
        assertEquals("PULL_FLOW_CONTROL", report.getQueueFindings().get(0).getRootCause());
        assertEquals("CLIENT_DROPPED_QUEUE", report.getQueueFindings().get(1).getRootCause());
    }
}
