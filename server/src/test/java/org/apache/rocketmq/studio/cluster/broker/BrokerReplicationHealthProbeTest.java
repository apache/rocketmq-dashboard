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

import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BrokerReplicationHealthProbeTest {

    private BrokerReplicationHealthProbe probe;
    private MQAdminExt admin;

    @BeforeEach
    void setUp() {
        probe = new BrokerReplicationHealthProbe();
        admin = mock(MQAdminExt.class);
    }

    @Test
    void testProbeClusterHaStatusEmptyClusterInfo() {
        BrokerHaReportVO report = probe.probeClusterHaStatus("cluster-a", null, admin);
        assertNotNull(report);
        assertFalse(report.isAllReplicasHealthy());
        assertTrue(report.getHaWarnings().get(0).contains("No broker cluster topology"));
    }

    @Test
    void testProbeClusterHaStatusSynchronizedMasterSlave() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();

        BrokerData data = new BrokerData();
        data.setBrokerName("broker-a");
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(MixAll.MASTER_ID, "10.0.0.1:10911");
        addrs.put(1L, "10.0.0.2:10911");
        data.setBrokerAddrs(addrs);
        brokerAddrTable.put("broker-a", data);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        when(admin.maxOffset("10.0.0.1:10911")).thenReturn(50000L);
        when(admin.maxOffset("10.0.0.2:10911")).thenReturn(50000L);

        BrokerHaReportVO report = probe.probeClusterHaStatus("cluster-a", clusterInfo, admin);
        assertNotNull(report);
        assertTrue(report.isAllReplicasHealthy());
        assertEquals(1, report.getTotalMasterBrokers());
        assertEquals(1, report.getTotalSlaveBrokers());
        assertEquals(1, report.getBrokerPairs().size());

        BrokerHaReportVO.BrokerHaPairVO pair = report.getBrokerPairs().get(0);
        assertTrue(pair.isMasterOnline());
        assertEquals(50000L, pair.getMasterMaxOffset());

        BrokerHaReportVO.SlaveReplicationStatusVO slave = pair.getSlaves().get(0);
        assertEquals(1L, slave.getBrokerId());
        assertEquals(0L, slave.getReplicationLagBytes());
        assertTrue(slave.isInSync());
        assertEquals("IN_SYNC", slave.getHealthStatus());
    }

    @Test
    void testProbeClusterHaStatusMinorLag() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();

        BrokerData data = new BrokerData();
        data.setBrokerName("broker-b");
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(MixAll.MASTER_ID, "10.0.0.1:10911");
        addrs.put(1L, "10.0.0.2:10911");
        data.setBrokerAddrs(addrs);
        brokerAddrTable.put("broker-b", data);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        when(admin.maxOffset("10.0.0.1:10911")).thenReturn(100000L);
        when(admin.maxOffset("10.0.0.2:10911")).thenReturn(99500L); // 500 byte lag (< 1MiB)

        BrokerHaReportVO report = probe.probeClusterHaStatus("cluster-a", clusterInfo, admin);
        assertNotNull(report);
        assertTrue(report.isAllReplicasHealthy());
        BrokerHaReportVO.SlaveReplicationStatusVO slave = report.getBrokerPairs().get(0).getSlaves().get(0);
        assertEquals(500L, slave.getReplicationLagBytes());
        assertTrue(slave.isInSync());
        assertEquals("MINOR_LAG", slave.getHealthStatus());
    }

    @Test
    void testProbeClusterHaStatusCriticalLag() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();

        BrokerData data = new BrokerData();
        data.setBrokerName("broker-c");
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(MixAll.MASTER_ID, "10.0.0.1:10911");
        addrs.put(1L, "10.0.0.2:10911");
        data.setBrokerAddrs(addrs);
        brokerAddrTable.put("broker-c", data);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        long severeLag = 70L * 1024L * 1024L; // 70 MiB (> 64 MiB critical)
        when(admin.maxOffset("10.0.0.1:10911")).thenReturn(100000000L);
        when(admin.maxOffset("10.0.0.2:10911")).thenReturn(100000000L - severeLag);

        BrokerHaReportVO report = probe.probeClusterHaStatus("cluster-a", clusterInfo, admin);
        assertNotNull(report);
        assertFalse(report.isAllReplicasHealthy());
        BrokerHaReportVO.SlaveReplicationStatusVO slave = report.getBrokerPairs().get(0).getSlaves().get(0);
        assertEquals(severeLag, slave.getReplicationLagBytes());
        assertFalse(slave.isInSync());
        assertEquals("CRITICAL_LAG", slave.getHealthStatus());
    }

    @Test
    void testProbeClusterHaStatusMasterOffline() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();

        BrokerData data = new BrokerData();
        data.setBrokerName("broker-d");
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(MixAll.MASTER_ID, "10.0.0.1:10911");
        addrs.put(1L, "10.0.0.2:10911");
        data.setBrokerAddrs(addrs);
        brokerAddrTable.put("broker-d", data);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);

        when(admin.maxOffset("10.0.0.1:10911")).thenThrow(new RuntimeException("Connection refused"));
        when(admin.maxOffset("10.0.0.2:10911")).thenReturn(50000L);

        BrokerHaReportVO report = probe.probeClusterHaStatus("cluster-a", clusterInfo, admin);
        assertNotNull(report);
        assertFalse(report.isAllReplicasHealthy());
        BrokerHaReportVO.BrokerHaPairVO pair = report.getBrokerPairs().get(0);
        assertFalse(pair.isMasterOnline());
        assertEquals("OFFLINE", pair.getSlaves().get(0).getHealthStatus());
    }
}
