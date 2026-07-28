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
package org.apache.rocketmq.dashboard.service.impl;

import org.apache.rocketmq.dashboard.BaseTest;
import org.apache.rocketmq.dashboard.architecture.AdminClient;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.service.ClusterService;
import org.apache.rocketmq.dashboard.service.ConsumerService;
import org.apache.rocketmq.dashboard.service.DashboardCollectService;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.apache.rocketmq.tools.admin.MQAdminExt;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class DashboardServiceImplTest extends BaseTest {

    @InjectMocks
    private DashboardServiceImpl dashboardService;

    @Mock
    private DashboardCollectService dashboardCollectService;

    @Mock
    private ConsumerService consumerService;

    @Mock
    private ClusterService clusterService;

    @Mock
    private MQAdminExt mqAdminExt;

    @Mock
    private AdminClient adminClient;

    private static final String DATE = "2026-07-28";

    private Map<String, List<String>> cacheWith(String key, String... lines) {
        Map<String, List<String>> cache = new HashMap<>();
        cache.put(key, Arrays.asList(lines));
        return cache;
    }

    @Test
    public void testQueryBrokerData() {
        Map<String, List<String>> cache = cacheWith("broker-a", "1,2");
        when(dashboardCollectService.getBrokerCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryBrokerData(DATE));
    }

    @Test
    public void testQueryTopicData() {
        Map<String, List<String>> cache = cacheWith("topicA", "1,2");
        when(dashboardCollectService.getTopicCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryTopicData(DATE));
    }

    @Test
    public void testQueryTopicDataByTopicFound() {
        when(dashboardCollectService.getTopicCache(DATE)).thenReturn(cacheWith("topicA", "1,2"));
        assertEquals(Arrays.asList("1,2"), dashboardService.queryTopicData(DATE, "topicA"));
    }

    @Test
    public void testQueryTopicDataByTopicMissing() {
        when(dashboardCollectService.getTopicCache(DATE)).thenReturn(cacheWith("topicA", "1,2"));
        assertTrue(dashboardService.queryTopicData(DATE, "other").isEmpty());
    }

    @Test
    public void testQueryTopicDataByTopicNullCache() {
        when(dashboardCollectService.getTopicCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryTopicData(DATE, "topicA").isEmpty());
    }

    @Test
    public void testQueryTopicCurrentData() {
        when(dashboardCollectService.getTopicCache(anyString()))
            .thenReturn(cacheWith("topicA", "t,1,2,3,100", "t,1,2,3,200"));

        List<String> result = dashboardService.queryTopicCurrentData();
        assertEquals(Collections.singletonList("topicA,200"), result);
    }

    @Test
    public void testQueryAccumulationData() {
        Map<String, List<String>> cache = cacheWith("topicA", "1");
        when(dashboardCollectService.getAccumulationCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryAccumulationData(DATE));
        assertEquals(Arrays.asList("1"), dashboardService.queryAccumulationData(DATE, "topicA"));
        assertTrue(dashboardService.queryAccumulationData(DATE, "missing").isEmpty());
    }

    @Test
    public void testQueryAccumulationDataNullCache() {
        when(dashboardCollectService.getAccumulationCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryAccumulationData(DATE, "topicA").isEmpty());
    }

    @Test
    public void testQueryTransactionData() {
        Map<String, List<String>> cache = cacheWith("topicA", "1");
        when(dashboardCollectService.getTransactionCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryTransactionData(DATE));
        assertEquals(Arrays.asList("1"), dashboardService.queryTransactionData(DATE, "topicA"));
        assertTrue(dashboardService.queryTransactionData(DATE, "missing").isEmpty());
    }

    @Test
    public void testQueryTransactionDataNullCache() {
        when(dashboardCollectService.getTransactionCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryTransactionData(DATE, "topicA").isEmpty());
    }

    @Test
    public void testQueryStorageLatencyData() {
        Map<String, List<String>> cache = cacheWith("topicA", "1");
        when(dashboardCollectService.getStorageLatencyCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryStorageLatencyData(DATE));
        assertEquals(Arrays.asList("1"), dashboardService.queryStorageLatencyData(DATE, "topicA"));
        assertTrue(dashboardService.queryStorageLatencyData(DATE, "missing").isEmpty());
    }

    @Test
    public void testQueryStorageLatencyDataNullCache() {
        when(dashboardCollectService.getStorageLatencyCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryStorageLatencyData(DATE, "topicA").isEmpty());
    }

    @Test
    public void testQueryNetworkThroughputData() {
        Map<String, List<String>> cache = cacheWith("broker-a", "1");
        when(dashboardCollectService.getNetworkThroughputCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryNetworkThroughputData(DATE));
        assertEquals(Arrays.asList("1"), dashboardService.queryNetworkThroughputData(DATE, "broker-a"));
        assertTrue(dashboardService.queryNetworkThroughputData(DATE, "missing").isEmpty());
    }

    @Test
    public void testQueryNetworkThroughputDataNullCache() {
        when(dashboardCollectService.getNetworkThroughputCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryNetworkThroughputData(DATE, "broker-a").isEmpty());
    }

    @Test
    public void testQueryReplicaSyncData() {
        Map<String, List<String>> cache = cacheWith("broker-a", "1");
        when(dashboardCollectService.getReplicaSyncCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryReplicaSyncData(DATE));
        assertEquals(Arrays.asList("1"), dashboardService.queryReplicaSyncData(DATE, "broker-a"));
        assertTrue(dashboardService.queryReplicaSyncData(DATE, "missing").isEmpty());
    }

    @Test
    public void testQueryReplicaSyncDataNullCache() {
        when(dashboardCollectService.getReplicaSyncCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryReplicaSyncData(DATE, "broker-a").isEmpty());
    }

    @Test
    public void testQueryHotTopicData() {
        Map<String, List<String>> cache = cacheWith("topicA", "1");
        when(dashboardCollectService.getHotTopicCache(DATE)).thenReturn(cache);
        assertSame(cache, dashboardService.queryHotTopicData(DATE));
        assertEquals(Arrays.asList("1"), dashboardService.queryHotTopicData(DATE, "topicA"));
        assertTrue(dashboardService.queryHotTopicData(DATE, "missing").isEmpty());
    }

    @Test
    public void testQueryHotTopicDataNullCache() {
        when(dashboardCollectService.getHotTopicCache(DATE)).thenReturn(null);
        assertTrue(dashboardService.queryHotTopicData(DATE, "topicA").isEmpty());
    }

    @Test
    public void testQueryConsumerConcurrencyEmptyGroups() {
        when(consumerService.listConsumerGroups()).thenReturn(Collections.emptyList());
        assertTrue(dashboardService.queryConsumerConcurrency().isEmpty());
    }

    @Test
    public void testQueryConsumerConcurrencyNullGroups() {
        when(consumerService.listConsumerGroups()).thenReturn(null);
        assertTrue(dashboardService.queryConsumerConcurrency().isEmpty());
    }

    @Test
    public void testQueryConsumerConcurrencyWithConnection() throws Exception {
        ConsumerGroupInfo groupInfo = new ConsumerGroupInfo();
        groupInfo.setConsumerGroupName("group-a");
        when(consumerService.listConsumerGroups()).thenReturn(Collections.singletonList(groupInfo));

        ConsumerConnection connection = new ConsumerConnection();
        HashSet<Connection> connectionSet = new HashSet<>();
        Connection conn = new Connection();
        conn.setClientId("client-1");
        connectionSet.add(conn);
        connection.setConnectionSet(connectionSet);
        when(adminClient.getConsumerConnection("group-a")).thenReturn(connection);

        List<Map<String, Object>> result = dashboardService.queryConsumerConcurrency();
        assertEquals(1, result.size());
        assertEquals("group-a", result.get(0).get("groupName"));
        assertEquals(1, result.get(0).get("clientCount"));
        assertEquals(20, result.get(0).get("consumeThreadMax"));
    }

    @Test
    public void testQueryConsumerConcurrencyFallbackToOnlineClientCount() throws Exception {
        ConsumerGroupInfo groupInfo = new ConsumerGroupInfo();
        groupInfo.setConsumerGroupName("group-b");
        groupInfo.setOnlineClientCount(3);
        when(consumerService.listConsumerGroups()).thenReturn(Collections.singletonList(groupInfo));
        when(adminClient.getConsumerConnection("group-b")).thenThrow(new RuntimeException("offline"));

        List<Map<String, Object>> result = dashboardService.queryConsumerConcurrency();
        assertEquals(1, result.size());
        assertEquals(3, result.get(0).get("clientCount"));
    }

    @Test
    public void testQueryConsumerConcurrencyNullConnection() throws Exception {
        ConsumerGroupInfo groupInfo = new ConsumerGroupInfo();
        groupInfo.setConsumerGroupName("group-c");
        when(consumerService.listConsumerGroups()).thenReturn(Collections.singletonList(groupInfo));
        when(adminClient.getConsumerConnection("group-c")).thenReturn(null);

        List<Map<String, Object>> result = dashboardService.queryConsumerConcurrency();
        assertEquals(1, result.size());
        assertEquals(0, result.get(0).get("clientCount"));
    }

    @Test
    public void testQueryConsumerConcurrencyServiceFailure() {
        when(consumerService.listConsumerGroups()).thenThrow(new RuntimeException("boom"));
        assertTrue(dashboardService.queryConsumerConcurrency().isEmpty());
    }

    @Test
    public void testQueryBrokerJvmStats() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(getClusterInfo());

        KVTable kvTable = new KVTable();
        HashMap<String, String> table = new HashMap<>();
        table.put("gcCount", "12");
        table.put("gcTimeMillis", "3456");
        table.put("jvmUptime", "999999");
        table.put("brokerMemoryHeapCommitted", "1024");
        table.put("brokerMemoryHeapUsed", "512");
        table.put("brokerMemoryHeapMax", "2048");
        table.put("brokerMemoryNonHeapUsed", "notANumber");
        table.put("threadCount", "abc");
        kvTable.setTable(table);
        when(mqAdminExt.fetchBrokerRuntimeStats("localhost:10911")).thenReturn(kvTable);

        List<Map<String, Object>> result = dashboardService.queryBrokerJvmStats();
        assertEquals(1, result.size());
        Map<String, Object> entry = result.get(0);
        assertEquals("broker-a", entry.get("brokerName"));
        assertEquals(0L, entry.get("brokerId"));
        assertEquals("localhost:10911", entry.get("brokerAddr"));
        assertEquals(12L, entry.get("gcCount"));
        assertEquals(3456L, entry.get("gcTimeMillis"));
        assertEquals(512L, entry.get("heapUsed"));
        // invalid number falls back to default 0
        assertEquals(0L, entry.get("nonHeapUsed"));
        assertEquals(0L, entry.get("nonHeapCommitted"));
        assertEquals(0, entry.get("threadCount"));
    }

    @Test
    public void testQueryBrokerJvmStatsBrokerFailureSkipped() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenReturn(getClusterInfo());
        when(mqAdminExt.fetchBrokerRuntimeStats(anyString())).thenThrow(new RuntimeException("timeout"));

        assertTrue(dashboardService.queryBrokerJvmStats().isEmpty());
    }

    @Test
    public void testQueryBrokerJvmStatsClusterFailure() throws Exception {
        when(mqAdminExt.examineBrokerClusterInfo()).thenThrow(new RuntimeException("cluster down"));
        assertTrue(dashboardService.queryBrokerJvmStats().isEmpty());
    }
}
