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

import apache.rocketmq.proxy.admin.v1.BatchConsumeClientDiagnostics;
import apache.rocketmq.proxy.admin.v1.BatchConsumeGroupSummary;
import apache.rocketmq.proxy.admin.v1.DescribeBatchConsumeDiagnosticsResponse;
import apache.rocketmq.proxy.admin.v1.DescribePopReceiptHandlesResponse;
import apache.rocketmq.proxy.admin.v1.PopReceiptHandleGroupSummary;
import apache.rocketmq.proxy.admin.v1.PopReceiptHandleInfo;
import apache.rocketmq.proxy.admin.v1.ProxyRuntimeConfig;
import apache.rocketmq.proxy.admin.v1.UpdateConfigResponse;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.service.ProxyAdminService;
import org.apache.rocketmq.dashboard.service.ProxyAdminService.BatchConsumeDiagnosticsResult;
import org.apache.rocketmq.dashboard.service.ProxyAdminService.PopDiagnosticsResult;
import org.apache.rocketmq.dashboard.service.ProxyAdminService.ProxyAdminStatus;
import org.apache.rocketmq.dashboard.service.client.MultiProxyAdminClient;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class ProxyAdminServiceImplTest {

    @Mock
    private MultiProxyAdminClient multiProxyAdminClient;

    private ProxyAdminServiceImpl proxyAdminService;

    @Before
    public void setUp() throws Exception {
        proxyAdminService = new ProxyAdminServiceImpl();
        // Inject mock via reflection
        Field field = ProxyAdminServiceImpl.class.getDeclaredField("multiProxyAdminClient");
        field.setAccessible(true);
        field.set(proxyAdminService, multiProxyAdminClient);
    }

    // ==================== getProxyConfigs ====================

    @Test
    public void testGetProxyConfigsReturnsEmptyWhenNotAvailable() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(false);

        Map<String, Map<String, Object>> result = proxyAdminService.getProxyConfigs();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetProxyConfigsSuccess() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        ProxyRuntimeConfig config = ProxyRuntimeConfig.newBuilder()
            .setProxyMode("CLUSTER")
            .setRocketmqClusterName("TestCluster")
            .setProxyName("proxy-0")
            .setGrpcServerPort(8081)
            .setProxyAdminEnabled(true)
            .setProxyAdminServerPort(8086)
            .setMaxMessageSize(4194304)
            .build();

        Map<String, ProxyRuntimeConfig> configMap = new LinkedHashMap<>();
        configMap.put("proxy-host1:8082", config);

        when(multiProxyAdminClient.getConfigFromAll()).thenReturn(configMap);

        Map<String, Map<String, Object>> result = proxyAdminService.getProxyConfigs();
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.containsKey("proxy-host1:8082"));

        Map<String, Object> proxyConfig = result.get("proxy-host1:8082");
        assertEquals("CLUSTER", proxyConfig.get("proxyMode"));
        assertEquals("TestCluster", proxyConfig.get("rocketmqClusterName"));
        assertEquals("proxy-0", proxyConfig.get("proxyName"));
        assertEquals(8081, proxyConfig.get("grpcServerPort"));
        assertEquals(true, proxyConfig.get("proxyAdminEnabled"));
        assertEquals(8086, proxyConfig.get("proxyAdminServerPort"));
        assertEquals(4194304, proxyConfig.get("maxMessageSize"));
    }

    @Test
    public void testGetProxyConfigsMultipleProxies() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        ProxyRuntimeConfig config1 = ProxyRuntimeConfig.newBuilder()
            .setProxyName("proxy-0").build();
        ProxyRuntimeConfig config2 = ProxyRuntimeConfig.newBuilder()
            .setProxyName("proxy-1").build();

        Map<String, ProxyRuntimeConfig> configMap = new LinkedHashMap<>();
        configMap.put("host1:8082", config1);
        configMap.put("host2:8082", config2);

        when(multiProxyAdminClient.getConfigFromAll()).thenReturn(configMap);

        Map<String, Map<String, Object>> result = proxyAdminService.getProxyConfigs();
        assertEquals(2, result.size());
        assertEquals("proxy-0", result.get("host1:8082").get("proxyName"));
        assertEquals("proxy-1", result.get("host2:8082").get("proxyName"));
    }

    // ==================== updateProxyConfig ====================

    @Test
    public void testUpdateProxyConfigReturnsEmptyWhenNotAvailable() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(false);

        Map<String, Object> updates = new HashMap<>();
        updates.put("maxMessageSize", 8388608);

        Map<String, List<String>> result = proxyAdminService.updateProxyConfig(updates);
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testUpdateProxyConfigSuccess() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        UpdateConfigResponse response = UpdateConfigResponse.newBuilder()
            .addChangedFields("maxMessageSize")
            .build();

        Map<String, UpdateConfigResponse> responses = new LinkedHashMap<>();
        responses.put("host1:8082", response);

        when(multiProxyAdminClient.updateConfigAll(any(ProxyRuntimeConfig.class))).thenReturn(responses);

        Map<String, Object> updates = new HashMap<>();
        updates.put("maxMessageSize", 8388608);

        Map<String, List<String>> result = proxyAdminService.updateProxyConfig(updates);
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(Arrays.asList("maxMessageSize"), result.get("host1:8082"));
    }

    @Test
    public void testUpdateProxyConfigNullResponse() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        Map<String, UpdateConfigResponse> responses = new LinkedHashMap<>();
        responses.put("host1:8082", null);

        when(multiProxyAdminClient.updateConfigAll(any(ProxyRuntimeConfig.class))).thenReturn(responses);

        Map<String, Object> updates = new HashMap<>();
        updates.put("traceOn", true);

        Map<String, List<String>> result = proxyAdminService.updateProxyConfig(updates);
        assertEquals(1, result.size());
        assertTrue(result.get("host1:8082").isEmpty());
    }

    // ==================== disconnectClient ====================

    @Test
    public void testDisconnectClientReturnsFalseWhenNotAvailable() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(false);
        assertFalse(proxyAdminService.disconnectClient("client1", "test"));
    }

    @Test
    public void testDisconnectClientSuccess() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);
        when(multiProxyAdminClient.disconnectClient("client1", "reason")).thenReturn(true);

        assertTrue(proxyAdminService.disconnectClient("client1", "reason"));
        verify(multiProxyAdminClient).disconnectClient("client1", "reason");
    }

    @Test
    public void testDisconnectClientNotFound() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);
        when(multiProxyAdminClient.disconnectClient(anyString(), anyString())).thenReturn(false);

        assertFalse(proxyAdminService.disconnectClient("unknown", "reason"));
    }

    // ==================== describePopReceiptHandles ====================

    @Test
    public void testDescribePopReceiptHandlesWhenNotAvailable() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(false);

        PopDiagnosticsResult result = proxyAdminService.describePopReceiptHandles("group", "topic", 1, 20);
        assertNotNull(result);
        assertNotNull(result.getHandles());
        assertTrue(result.getHandles().isEmpty());
        assertEquals(0, result.getTotalHandles());
    }

    @Test
    public void testDescribePopReceiptHandlesSuccess() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        PopReceiptHandleGroupSummary summary = PopReceiptHandleGroupSummary.newBuilder()
            .setTotalHandles(5)
            .setTotalMessages(10)
            .setTotalRenewTimes(3)
            .setTotalRenewRetryTimes(1)
            .setExpiredHandles(2)
            .build();

        PopReceiptHandleInfo handle = PopReceiptHandleInfo.newBuilder()
            .setGroup("testGroup")
            .setTopic("testTopic")
            .setQueueId(1)
            .setMessageId("msg001")
            .setQueueOffset(100)
            .setReconsumeTimes(0)
            .setRenewTimes(2)
            .setRenewRetryTimes(0)
            .setConsumeTimestamp(1000L)
            .setReceiptHandle("handle123")
            .setNextVisibleTime(2000L)
            .setInvisibleTime(60000L)
            .setBrokerName("broker-a")
            .setIsExpired(false)
            .build();

        DescribePopReceiptHandlesResponse response = DescribePopReceiptHandlesResponse.newBuilder()
            .setSummary(summary)
            .addHandles(handle)
            .build();

        when(multiProxyAdminClient.describePopReceiptHandlesFromAll(
            anyString(), anyString(), anyInt(), anyInt()))
            .thenReturn(Collections.singletonList(response));

        PopDiagnosticsResult result = proxyAdminService.describePopReceiptHandles("testGroup", "testTopic", 1, 20);
        assertEquals(5, result.getTotalHandles());
        assertEquals(10, result.getTotalMessages());
        assertEquals(3, result.getTotalRenewTimes());
        assertEquals(1, result.getTotalRenewRetryTimes());
        assertEquals(2, result.getExpiredHandles());
        assertEquals(1, result.getTotal());
        assertEquals(1, result.getHandles().size());

        Map<String, Object> handleMap = result.getHandles().get(0);
        assertEquals("testGroup", handleMap.get("group"));
        assertEquals("testTopic", handleMap.get("topic"));
        assertEquals(1, handleMap.get("queueId"));
        assertEquals("msg001", handleMap.get("messageId"));
        assertEquals(false, handleMap.get("isExpired"));
    }

    @Test
    public void testDescribePopReceiptHandlesAggregatesMultiProxy() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        PopReceiptHandleGroupSummary summary1 = PopReceiptHandleGroupSummary.newBuilder()
            .setTotalHandles(3).setTotalMessages(5).build();
        PopReceiptHandleGroupSummary summary2 = PopReceiptHandleGroupSummary.newBuilder()
            .setTotalHandles(2).setTotalMessages(4).build();

        DescribePopReceiptHandlesResponse resp1 = DescribePopReceiptHandlesResponse.newBuilder()
            .setSummary(summary1).build();
        DescribePopReceiptHandlesResponse resp2 = DescribePopReceiptHandlesResponse.newBuilder()
            .setSummary(summary2).build();

        List<DescribePopReceiptHandlesResponse> responses = Arrays.asList(resp1, resp2);
        when(multiProxyAdminClient.describePopReceiptHandlesFromAll(
            anyString(), anyString(), anyInt(), anyInt())).thenReturn(responses);

        PopDiagnosticsResult result = proxyAdminService.describePopReceiptHandles("g", "t", 1, 20);
        assertEquals(5, result.getTotalHandles());
        assertEquals(9, result.getTotalMessages());
    }

    // ==================== describeBatchConsumeDiagnostics ====================

    @Test
    public void testDescribeBatchConsumeDiagnosticsWhenNotAvailable() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(false);

        BatchConsumeDiagnosticsResult result =
            proxyAdminService.describeBatchConsumeDiagnostics("group", "topic", null, 1, 20);
        assertNotNull(result);
        assertNotNull(result.getDiagnostics());
        assertTrue(result.getDiagnostics().isEmpty());
        assertEquals(0, result.getTotalClients());
    }

    @Test
    public void testDescribeBatchConsumeDiagnosticsSuccess() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        BatchConsumeGroupSummary summary = BatchConsumeGroupSummary.newBuilder()
            .setTotalClients(3)
            .setTotalUnackedMessages(100)
            .setTotalUnackedHandles(50)
            .setTotalExpiredHandles(5)
            .setTotalRenewTimes(20)
            .setTotalRenewRetryTimes(2)
            .build();

        BatchConsumeClientDiagnostics diag = BatchConsumeClientDiagnostics.newBuilder()
            .setClientId("client-001")
            .setChannelId("ch-001")
            .setUnackedMessageCount(10)
            .setUnackedHandleCount(5)
            .setTotalRenewTimes(3)
            .setTotalRenewRetryTimes(1)
            .setExpiredHandleCount(0)
            .setConsumeType("PUSH")
            .setMessageModel("CLUSTERING")
            .setReceiveBatchSize(32)
            .setLongPollingTimeoutMs(30000)
            .setLastRttMs(15)
            .setConnectTime(1000L)
            .build();

        DescribeBatchConsumeDiagnosticsResponse response =
            DescribeBatchConsumeDiagnosticsResponse.newBuilder()
                .setSummary(summary)
                .addDiagnostics(diag)
                .build();

        when(multiProxyAdminClient.describeBatchConsumeDiagnosticsFromAll(
            anyString(), anyString(), isNull(), anyInt(), anyInt()))
            .thenReturn(Collections.singletonList(response));

        BatchConsumeDiagnosticsResult result =
            proxyAdminService.describeBatchConsumeDiagnostics("group", "topic", null, 1, 20);

        assertEquals(3, result.getTotalClients());
        assertEquals(100, result.getTotalUnackedMessages());
        assertEquals(50, result.getTotalUnackedHandles());
        assertEquals(5, result.getTotalExpiredHandles());
        assertEquals(20, result.getTotalRenewTimes());
        assertEquals(2, result.getTotalRenewRetryTimes());
        assertEquals(1, result.getTotal());

        Map<String, Object> diagMap = result.getDiagnostics().get(0);
        assertEquals("client-001", diagMap.get("clientId"));
        assertEquals("ch-001", diagMap.get("channelId"));
        assertEquals(10, diagMap.get("unackedMessageCount"));
        assertEquals("PUSH", diagMap.get("consumeType"));
        assertEquals("CLUSTERING", diagMap.get("messageModel"));
    }

    @Test
    public void testDescribeBatchConsumeDiagnosticsAggregatesMultiProxy() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);

        BatchConsumeGroupSummary summary1 = BatchConsumeGroupSummary.newBuilder()
            .setTotalClients(2).setTotalUnackedMessages(30).build();
        BatchConsumeGroupSummary summary2 = BatchConsumeGroupSummary.newBuilder()
            .setTotalClients(1).setTotalUnackedMessages(20).build();

        DescribeBatchConsumeDiagnosticsResponse resp1 =
            DescribeBatchConsumeDiagnosticsResponse.newBuilder().setSummary(summary1).build();
        DescribeBatchConsumeDiagnosticsResponse resp2 =
            DescribeBatchConsumeDiagnosticsResponse.newBuilder().setSummary(summary2).build();

        when(multiProxyAdminClient.describeBatchConsumeDiagnosticsFromAll(
            anyString(), anyString(), isNull(), anyInt(), anyInt()))
            .thenReturn(Arrays.asList(resp1, resp2));

        BatchConsumeDiagnosticsResult result =
            proxyAdminService.describeBatchConsumeDiagnostics("g", "t", null, 1, 20);
        assertEquals(3, result.getTotalClients());
        assertEquals(50, result.getTotalUnackedMessages());
    }

    // ==================== getStatus ====================

    @Test
    public void testGetStatusWhenClientNull() throws Exception {
        Field field = ProxyAdminServiceImpl.class.getDeclaredField("multiProxyAdminClient");
        field.setAccessible(true);
        field.set(proxyAdminService, null);

        ProxyAdminStatus status = proxyAdminService.getStatus();
        assertNotNull(status);
        assertFalse(status.isConnected());
        assertEquals(0, status.getAvailableProxies());
        assertEquals(0, status.getTotalProxies());
        assertEquals(0, status.getProxyAddresses().length);
    }

    @Test
    public void testGetStatusSuccess() {
        when(multiProxyAdminClient.isAvailable()).thenReturn(true);
        when(multiProxyAdminClient.getAvailableCount()).thenReturn(2);
        when(multiProxyAdminClient.getTotalCount()).thenReturn(3);
        when(multiProxyAdminClient.getProxyAddresses())
            .thenReturn(new String[]{"host1:8080", "host2:8080", "host3:8080"});

        ProxyAdminStatus status = proxyAdminService.getStatus();
        assertTrue(status.isConnected());
        assertEquals(2, status.getAvailableProxies());
        assertEquals(3, status.getTotalProxies());
        assertEquals(3, status.getProxyAddresses().length);
    }
}
