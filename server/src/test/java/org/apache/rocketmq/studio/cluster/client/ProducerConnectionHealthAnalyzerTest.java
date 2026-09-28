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
package org.apache.rocketmq.studio.cluster.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProducerConnectionHealthAnalyzerTest {

    private ProducerConnectionHealthAnalyzer analyzer;

    @BeforeEach
    void setUp() {
        analyzer = new ProducerConnectionHealthAnalyzer();
    }

    @Test
    void testAnalyzeEmptyConnections() {
        ProducerHealthAuditReportVO report = analyzer.analyze("TopicOrders", "PG_Orders", Collections.emptyList());
        assertNotNull(report);
        assertEquals(0, report.getTotalProducerClients());
        assertEquals("WARNING_NO_CLIENTS", report.getHealthSummary());
        assertFalse(report.isMultiIpRedundancy());
        assertTrue(report.getGovernanceRecommendations().get(0).contains("No active producer connections"));
    }

    @Test
    void testAnalyzeSingleHostWarning() {
        ProducerConnectionVO conn1 = ProducerConnectionVO.builder()
                .clientId("client-1")
                .clientAddr("192.168.1.10:50123")
                .language("JAVA")
                .versionDesc("V5_1_0")
                .build();
        ProducerConnectionVO conn2 = ProducerConnectionVO.builder()
                .clientId("client-2")
                .clientAddr("192.168.1.10:50124")
                .language("JAVA")
                .versionDesc("V5_1_0")
                .build();

        ProducerHealthAuditReportVO report = analyzer.analyze("TopicOrders", "PG_Orders", List.of(conn1, conn2));
        assertNotNull(report);
        assertEquals(2, report.getTotalProducerClients());
        assertFalse(report.isMultiIpRedundancy());
        assertEquals("WARNING_SINGLE_HOST", report.getHealthSummary());
        assertTrue(report.getGovernanceRecommendations().get(0).contains("single host IP"));
    }

    @Test
    void testAnalyzeMultiHostHealthy() {
        ProducerConnectionVO conn1 = ProducerConnectionVO.builder()
                .clientId("client-1")
                .clientAddr("192.168.1.10:50123")
                .language("JAVA")
                .versionDesc("V5_1_0")
                .build();
        ProducerConnectionVO conn2 = ProducerConnectionVO.builder()
                .clientId("client-2")
                .clientAddr("192.168.1.20:50123")
                .language("GO")
                .versionDesc("V5_1_0")
                .build();

        ProducerHealthAuditReportVO report = analyzer.analyze("TopicOrders", "PG_Orders", List.of(conn1, conn2));
        assertNotNull(report);
        assertEquals(2, report.getTotalProducerClients());
        assertTrue(report.isMultiIpRedundancy());
        assertEquals("HEALTHY", report.getHealthSummary());
        assertEquals(1, report.getLanguageDistribution().get("JAVA"));
        assertEquals(1, report.getLanguageDistribution().get("GO"));
        assertTrue(report.getClientRisks().isEmpty());
    }

    @Test
    void testAnalyzeLegacySdkVersionAndLoopback() {
        ProducerConnectionVO conn1 = ProducerConnectionVO.builder()
                .clientId("client-old")
                .clientAddr("192.168.1.10:50123")
                .language("CPP")
                .versionDesc("V4_0_0") // Legacy
                .build();
        ProducerConnectionVO conn2 = ProducerConnectionVO.builder()
                .clientId("client-loopback")
                .clientAddr("127.0.0.1:50124") // Loopback
                .language("JAVA")
                .versionDesc("V5_0_0")
                .build();

        ProducerHealthAuditReportVO report = analyzer.analyze("TopicOrders", "PG_Orders", List.of(conn1, conn2));
        assertNotNull(report);
        assertEquals(2, report.getTotalProducerClients());
        assertEquals("WARNING_LEGACY_SDK", report.getHealthSummary());
        assertEquals(2, report.getClientRisks().size());

        boolean hasLegacy = report.getClientRisks().stream()
                .anyMatch(r -> "OUTDATED_SDK_VERSION".equals(r.getRiskType()));
        boolean hasLoopback = report.getClientRisks().stream()
                .anyMatch(r -> "LOOPBACK_BINDING".equals(r.getRiskType()));

        assertTrue(hasLegacy);
        assertTrue(hasLoopback);
    }

    @Test
    void testAnalyzeUnrecognizedLanguageAndSlashPrefixedAddress() {
        ProducerConnectionVO conn1 = ProducerConnectionVO.builder()
                .clientId("client-unknown")
                .clientAddr("/10.0.1.50:40001")
                .language("") // Empty -> UNKNOWN
                .versionDesc("V5_1_0")
                .build();
        ProducerConnectionVO conn2 = ProducerConnectionVO.builder()
                .clientId("client-known")
                .clientAddr("/10.0.1.51:40002")
                .language("RUST")
                .versionDesc("V5_1_0")
                .build();

        ProducerHealthAuditReportVO report = analyzer.analyze("TopicOrders", "PG_Orders", List.of(conn1, conn2));
        assertNotNull(report);
        assertTrue(report.isMultiIpRedundancy());
        assertEquals(1, report.getClientRisks().size());
        assertEquals("UNRECOGNIZED_LANGUAGE", report.getClientRisks().get(0).getRiskType());
    }
}
