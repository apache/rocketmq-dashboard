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
package org.apache.rocketmq.studio.instance.dlq;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DLQFeatureClusteringEngineTest {

    private DLQFeatureClusteringEngine engine;

    @BeforeEach
    void setUp() {
        engine = new DLQFeatureClusteringEngine();
    }

    @Test
    void testClusterAndEvaluateEmptyMessages() {
        DLQClusteringReportVO report = engine.clusterAndEvaluateReplay("GroupTest", List.of());
        assertNotNull(report);
        assertEquals(0, report.getTotalSampledMessages());
        assertEquals(0, report.getClusterCount());
        assertTrue(report.getReplayRecommendations().get(0).contains("No DLQ messages found"));
    }

    @Test
    void testClusterAndEvaluateTransientNetworkFailure() {
        DLQMessageVO m1 = DLQMessageVO.builder()
                .msgId("m1")
                .topic("%DLQ%GroupA")
                .properties(Map.of("RETRY_TOPIC", "OrderTopic", "ERROR_MSG", "java.net.SocketTimeoutException: connect timed out"))
                .reconsumeTimes(16)
                .build();
        DLQMessageVO m2 = DLQMessageVO.builder()
                .msgId("m2")
                .topic("%DLQ%GroupA")
                .properties(Map.of("RETRY_TOPIC", "OrderTopic", "ERROR_MSG", "java.net.SocketTimeoutException: read timed out"))
                .reconsumeTimes(16)
                .build();

        DLQClusteringReportVO report = engine.clusterAndEvaluateReplay("GroupA", List.of(m1, m2));
        assertNotNull(report);
        assertEquals(2, report.getTotalSampledMessages());
        assertEquals(1, report.getClusterCount());

        DLQClusteringReportVO.DLQMessageClusterVO cluster = report.getClusters().get(0);
        assertEquals("OrderTopic", cluster.getOriginTopic());
        assertEquals("SocketTimeoutException", cluster.getDetectedExceptionClass());
        assertTrue(cluster.isRecommendedForReplay());
        assertEquals("SAFE_TO_REPLAY", cluster.getRiskAssessment());
        assertTrue(report.getReplayRecommendations().get(0).contains("safe for automated replay"));
    }

    @Test
    void testClusterAndEvaluatePermanentSchemaFailure() {
        DLQMessageVO m1 = DLQMessageVO.builder()
                .msgId("m-npe")
                .topic("%DLQ%GroupA")
                .body("java.lang.NullPointerException at com.example.OrderHandler.process(OrderHandler.java:42)")
                .reconsumeTimes(16)
                .build();

        DLQClusteringReportVO report = engine.clusterAndEvaluateReplay("GroupA", List.of(m1));
        assertNotNull(report);
        assertEquals(1, report.getClusterCount());

        DLQClusteringReportVO.DLQMessageClusterVO cluster = report.getClusters().get(0);
        assertEquals("NullPointerException", cluster.getDetectedExceptionClass());
        assertFalse(cluster.isRecommendedForReplay());
        assertEquals("PERMANENT_SCHEMA_FAILURE", cluster.getRiskAssessment());
        assertTrue(report.getReplayRecommendations().get(0).contains("Fix business code before replaying"));
    }

    @Test
    void testClusterAndEvaluateMultiErrorSignatures() {
        DLQMessageVO m1 = DLQMessageVO.builder()
                .msgId("m1")
                .topic("%DLQ%GroupB")
                .properties(Map.of("RETRY_TOPIC", "Topic1", "ERROR", "java.net.ConnectException"))
                .reconsumeTimes(16)
                .build();
        DLQMessageVO m2 = DLQMessageVO.builder()
                .msgId("m2")
                .topic("%DLQ%GroupB")
                .properties(Map.of("RETRY_TOPIC", "Topic2", "ERROR", "java.lang.ClassCastException"))
                .reconsumeTimes(16)
                .build();

        DLQClusteringReportVO report = engine.clusterAndEvaluateReplay("GroupB", List.of(m1, m2));
        assertNotNull(report);
        assertEquals(2, report.getClusterCount());
        assertEquals(2, report.getReplayRecommendations().size());
    }
}
