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
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TopicTrafficSkewDetectorTest {

    private TopicTrafficSkewDetector detector;

    @BeforeEach
    void setUp() {
        detector = new TopicTrafficSkewDetector();
    }

    @Test
    void testDetectSkewEmptyStats() {
        TopicTrafficSkewReportVO report = detector.detectSkew("TopicEmpty", Collections.emptyList());
        assertNotNull(report);
        assertEquals(0, report.getTotalQueues());
        assertEquals("NORMAL", report.getSkewSeverity());
        assertTrue(report.getDiagnosticAlerts().get(0).contains("No queue offset statistics"));
    }

    @Test
    void testDetectSkewSingleQueue() {
        TopicQueueStatsVO stat = TopicQueueStatsVO.builder()
                .brokerName("broker-a")
                .queueId(0)
                .minOffset(0L)
                .maxOffset(5000L)
                .build();

        TopicTrafficSkewReportVO report = detector.detectSkew("TopicSingle", List.of(stat));
        assertNotNull(report);
        assertEquals(1, report.getTotalQueues());
        assertEquals(5000L, report.getTotalMessagesAcrossQueues());
        assertEquals(0.0, report.getGiniCoefficient());
        assertFalse(report.isSkewDetected());
        assertEquals("NORMAL", report.getSkewSeverity());
        assertTrue(report.getDiagnosticAlerts().get(0).contains("Single queue topic"));
    }

    @Test
    void testDetectSkewEvenlyDistributedQueues() {
        List<TopicQueueStatsVO> stats = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            stats.add(TopicQueueStatsVO.builder()
                    .brokerName("broker-a")
                    .queueId(i)
                    .minOffset(0L)
                    .maxOffset(1000L) // 1000 messages each
                    .build());
        }

        TopicTrafficSkewReportVO report = detector.detectSkew("TopicBalanced", stats);
        assertNotNull(report);
        assertEquals(4, report.getTotalQueues());
        assertEquals(4000L, report.getTotalMessagesAcrossQueues());
        assertEquals(0.0, report.getGiniCoefficient());
        assertEquals(0.0, report.getStandardDeviation());
        assertFalse(report.isSkewDetected());
        assertEquals("NORMAL", report.getSkewSeverity());
        assertTrue(report.getDiagnosticAlerts().get(0).contains("evenly balanced"));
    }

    @Test
    void testDetectSkewSevereHotspot() {
        List<TopicQueueStatsVO> stats = List.of(
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(0).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(1).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(2).minOffset(0L).maxOffset(100L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(3).minOffset(0L).maxOffset(9700L).build() // Hotspot!
        );

        TopicTrafficSkewReportVO report = detector.detectSkew("TopicHotspot", stats);
        assertNotNull(report);
        assertEquals(4, report.getTotalQueues());
        assertEquals(10000L, report.getTotalMessagesAcrossQueues());
        assertTrue(report.getGiniCoefficient() >= 0.70);
        assertTrue(report.isSkewDetected());
        assertEquals("SEVERE_HOTSPOT", report.getSkewSeverity());

        TopicTrafficSkewReportVO.QueueTrafficDistributionVO hotQueue = report.getQueueDistributions().get(3);
        assertTrue(hotQueue.isHotspot());
        assertEquals(97.0, hotQueue.getTrafficSharePercent());
        assertTrue(report.getDiagnosticAlerts().get(0).contains("Severe traffic hotspot detected"));
        assertTrue(report.getDiagnosticAlerts().stream().anyMatch(a -> a.contains("MessageQueueSelector hash keys")));
    }

    @Test
    void testDetectSkewModerateDivergence() {
        List<TopicQueueStatsVO> stats = List.of(
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(0).minOffset(0L).maxOffset(200L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(1).minOffset(0L).maxOffset(400L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(2).minOffset(0L).maxOffset(1200L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(3).minOffset(0L).maxOffset(2200L).build()
        );

        TopicTrafficSkewReportVO report = detector.detectSkew("TopicModerate", stats);
        assertNotNull(report);
        assertTrue(report.getGiniCoefficient() >= 0.40);
        assertTrue(report.isSkewDetected());
        assertEquals("MODERATE_SKEW", report.getSkewSeverity());
        assertTrue(report.getDiagnosticAlerts().stream().anyMatch(a -> a.contains("Moderate traffic skew detected")));
    }

    @Test
    void testDetectSkewZeroTotalMessages() {
        List<TopicQueueStatsVO> stats = List.of(
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(0).minOffset(10L).maxOffset(10L).build(),
                TopicQueueStatsVO.builder().brokerName("broker-a").queueId(1).minOffset(20L).maxOffset(20L).build()
        );

        TopicTrafficSkewReportVO report = detector.detectSkew("TopicZeroVolume", stats);
        assertNotNull(report);
        assertEquals(2, report.getTotalQueues());
        assertEquals(0L, report.getTotalMessagesAcrossQueues());
        assertEquals(0.0, report.getGiniCoefficient());
        assertFalse(report.isSkewDetected());
        assertEquals("NORMAL", report.getSkewSeverity());
    }
}
