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
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.studio.cluster.config.ClusterConfigVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrokerConfigDriftEngineTest {

    private BrokerConfigDriftEngine engine;

    @BeforeEach
    void setUp() {
        engine = new BrokerConfigDriftEngine();
    }

    @Test
    void testAuditClusterConfigDriftEmpty() {
        BrokerConfigAuditReportVO report = engine.auditClusterConfigDrift("cluster-a", "inst-1", Collections.emptyMap());
        assertNotNull(report);
        assertEquals(0, report.getTotalBrokers());
        assertEquals(0.0, report.getClusterConsistencyScore());
        assertTrue(report.getOperationalRecommendations().get(0).contains("No reachable broker"));
    }

    @Test
    void testAuditClusterConfigDriftSingleBroker() {
        ClusterConfigVO cfg = new ClusterConfigVO();
        cfg.setBrokerName("broker-a");
        cfg.setFlushDiskType("ASYNC_FLUSH");

        BrokerConfigAuditReportVO report = engine.auditClusterConfigDrift("cluster-a", "inst-1", Map.of("10.0.0.1:10911", cfg));
        assertNotNull(report);
        assertEquals(1, report.getTotalBrokers());
        assertEquals(100.0, report.getClusterConsistencyScore());
        assertEquals("10.0.0.1:10911", report.getBaselineBrokerAddress());
        assertTrue(report.getOperationalRecommendations().get(0).contains("Single broker detected"));
    }

    @Test
    void testAuditClusterConfigDriftSynchronizedCluster() {
        ClusterConfigVO cfg1 = new ClusterConfigVO();
        cfg1.setBrokerName("broker-a");
        cfg1.setFlushDiskType("ASYNC_FLUSH");
        cfg1.setAutoCreateTopicEnable(false);
        cfg1.setMaxMessageSize(4194304);

        ClusterConfigVO cfg2 = new ClusterConfigVO();
        cfg2.setBrokerName("broker-b");
        cfg2.setFlushDiskType("ASYNC_FLUSH");
        cfg2.setAutoCreateTopicEnable(false);
        cfg2.setMaxMessageSize(4194304);

        Map<String, ClusterConfigVO> map = new LinkedHashMap<>();
        map.put("10.0.0.1:10911", cfg1);
        map.put("10.0.0.2:10911", cfg2);

        BrokerConfigAuditReportVO report = engine.auditClusterConfigDrift("cluster-a", "inst-1", map);
        assertNotNull(report);
        assertEquals(2, report.getTotalBrokers());
        assertEquals(0, report.getDriftedFieldCount());
        assertEquals(100.0, report.getClusterConsistencyScore());
        assertTrue(report.getDriftedProperties().isEmpty());
        assertTrue(report.getOperationalRecommendations().get(0).contains("completely synchronized"));
    }

    @Test
    void testAuditClusterConfigDriftCriticalDivergence() {
        ClusterConfigVO cfg1 = new ClusterConfigVO();
        cfg1.setBrokerName("broker-a");
        cfg1.setFlushDiskType("ASYNC_FLUSH");
        cfg1.setAutoCreateTopicEnable(false);
        cfg1.setMaxMessageSize(4194304);

        ClusterConfigVO cfg2 = new ClusterConfigVO();
        cfg2.setBrokerName("broker-b");
        cfg2.setFlushDiskType("SYNC_FLUSH"); // Drift!
        cfg2.setAutoCreateTopicEnable(true);  // Drift!
        cfg2.setMaxMessageSize(1048576);     // Drift!

        Map<String, ClusterConfigVO> map = new LinkedHashMap<>();
        map.put("10.0.0.1:10911", cfg1);
        map.put("10.0.0.2:10911", cfg2);

        BrokerConfigAuditReportVO report = engine.auditClusterConfigDrift("cluster-a", "inst-1", map);
        assertNotNull(report);
        assertEquals(2, report.getTotalBrokers());
        assertEquals(3, report.getDriftedFieldCount());
        assertTrue(report.getClusterConsistencyScore() < 60.0);
        assertEquals(3, report.getDriftedProperties().size());

        boolean hasFlushDrift = report.getDriftedProperties().stream()
                .anyMatch(p -> "flushDiskType".equals(p.getPropertyName()) && "CRITICAL".equals(p.getSeverity()));
        boolean hasTopicDrift = report.getDriftedProperties().stream()
                .anyMatch(p -> "autoCreateTopicEnable".equals(p.getPropertyName()) && "HIGH".equals(p.getSeverity()));

        assertTrue(hasFlushDrift);
        assertTrue(hasTopicDrift);
        assertEquals(3, report.getOperationalRecommendations().size());
    }
}
