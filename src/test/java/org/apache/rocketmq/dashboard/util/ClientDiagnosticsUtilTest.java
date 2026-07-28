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
package org.apache.rocketmq.dashboard.util;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class ClientDiagnosticsUtilTest {

    private ClientDiagnosticsUtil diagnosticsUtil;

    @Before
    public void setUp() {
        diagnosticsUtil = new ClientDiagnosticsUtil();
    }

    private ClientInstance healthyClient() {
        ClientInstance client = new ClientInstance();
        client.setClientId("healthy-client");
        client.setClientAddress("127.0.0.1:1234");
        client.setClientType(ClientInstance.ClientType.PRODUCER);
        client.setProtocolType(ClientInstance.ProtocolType.REMOTING);
        client.setLastHeartbeatTime(new Date());
        client.setVersion("4.9");
        client.setStatus("ONLINE");
        client.setConnectTime(new Date(System.currentTimeMillis() - 600_000));
        return client;
    }

    @Test
    public void testDiagnoseHealthyClient() {
        ClientDiagnosticsUtil.ClientDiagnosisResult result = diagnosticsUtil.diagnoseClient(healthyClient());
        assertEquals("healthy-client", result.getClientId());
        assertEquals("127.0.0.1:1234", result.getClientAddress());
        assertEquals("REMOTING", result.getProtocolType());
        assertTrue(result.getIssues().isEmpty());
        assertEquals("GOOD", result.getOverallHealth());
    }

    @Test
    public void testDiagnoseUnhealthyClient() {
        ClientInstance client = new ClientInstance();
        client.setClientId("bad-client");
        client.setClientType(ClientInstance.ClientType.PUSH_CONSUMER);
        client.setVersion("4.7");
        client.setStatus("OFFLINE");
        client.setSubscriptionCount(150);
        client.setConnectTime(new Date());

        ClientDiagnosisResultAssert(client);
    }

    private void ClientDiagnosisResultAssert(ClientInstance client) {
        ClientDiagnosticsUtil.ClientDiagnosisResult result = diagnosticsUtil.diagnoseClient(client);
        assertEquals("UNKNOWN", result.getProtocolType());
        // no heartbeat + old version + too many subscriptions + offline status
        assertEquals(4, result.getIssues().size());
        assertEquals("POOR", result.getOverallHealth());
        assertFalse(result.getRecommendations().isEmpty());

        result.setClientId("changed");
        result.setClientAddress("addr");
        result.setProtocolType("GRPC");
        result.setOverallHealth("GOOD");
        result.setIssues(Arrays.asList("i"));
        result.setRecommendations(Arrays.asList("r"));
        assertEquals("changed", result.getClientId());
        assertEquals("addr", result.getClientAddress());
        assertEquals("GRPC", result.getProtocolType());
        assertEquals("GOOD", result.getOverallHealth());
        assertEquals(1, result.getIssues().size());
        assertEquals(1, result.getRecommendations().size());
    }

    @Test
    public void testDiagnoseStaleHeartbeat() {
        ClientInstance client = healthyClient();
        client.setLastHeartbeatTime(new Date(System.currentTimeMillis() - 600_000));
        ClientDiagnosticsUtil.ClientDiagnosisResult result = diagnosticsUtil.diagnoseClient(client);
        assertEquals(1, result.getIssues().size());
        assertEquals("FAIR", result.getOverallHealth());
        assertTrue(result.getIssues().get(0).contains("heartbeat"));
    }

    @Test
    public void testGetProtocolCompatibility() {
        ClientDiagnosticsUtil.ProtocolCompatibilityInfo info = diagnosticsUtil.getProtocolCompatibility();
        assertTrue(info.getSupportedProtocols().containsKey("Remoting"));
        assertTrue(info.getSupportedProtocols().containsKey("gRPC"));
        assertTrue(info.getSupportedProtocols().get("gRPC").contains("5.0"));
        assertTrue(info.getProtocolFeatures().get("Remoting").contains("Low latency"));
        assertTrue(info.getProtocolFeatures().get("gRPC").contains("HTTP/2 support"));
    }

    @Test
    public void testClassifyClientsByHealth() {
        ClientInstance healthy = healthyClient();

        ClientInstance warning = healthyClient();
        warning.setClientId("warning-client");
        warning.setStatus("SUSPENDED");

        ClientInstance critical = new ClientInstance();
        critical.setClientId("critical-client");
        critical.setVersion("4.5");
        critical.setStatus("OFFLINE");

        Map<String, List<ClientInstance>> classification =
            diagnosticsUtil.classifyClientsByHealth(Arrays.asList(healthy, warning, critical));
        assertEquals(1, classification.get("HEALTHY").size());
        assertEquals(1, classification.get("WARNING").size());
        assertEquals(1, classification.get("CRITICAL").size());
        assertEquals("healthy-client", classification.get("HEALTHY").get(0).getClientId());
        assertEquals("warning-client", classification.get("WARNING").get(0).getClientId());
        assertEquals("critical-client", classification.get("CRITICAL").get(0).getClientId());
    }

    @Test
    public void testGetClientStatistics() {
        ClientInstance producer = new ClientInstance();
        producer.setClientType(ClientInstance.ClientType.PRODUCER);
        producer.setProtocolType(ClientInstance.ProtocolType.REMOTING);
        producer.setStatus("ONLINE");

        ClientInstance consumer = new ClientInstance();
        consumer.setClientType(ClientInstance.ClientType.SIMPLE_CONSUMER);
        consumer.setProtocolType(ClientInstance.ProtocolType.GRPC);
        consumer.setStatus("OFFLINE");

        ClientInstance pullConsumer = new ClientInstance();
        pullConsumer.setClientType(ClientInstance.ClientType.PULL_CONSUMER);
        pullConsumer.setProtocolType(ClientInstance.ProtocolType.GRPC);
        pullConsumer.setStatus("ONLINE");

        ClientDiagnosticsUtil.ClientStatistics stats =
            diagnosticsUtil.getClientStatistics(Arrays.asList(producer, consumer, pullConsumer));
        assertEquals(3, stats.getTotalCount());
        assertEquals(1, stats.getProducerCount());
        assertEquals(2, stats.getConsumerCount());
        assertEquals(1, stats.getRemotingCount());
        assertEquals(2, stats.getGrpcCount());
        assertEquals(2, stats.getOnlineCount());
        assertEquals(1, stats.getOfflineCount());
    }

    @Test
    public void testClientStatisticsSetters() {
        ClientDiagnosticsUtil.ClientStatistics stats = new ClientDiagnosticsUtil.ClientStatistics();
        stats.setTotalCount(10);
        stats.setProducerCount(4);
        stats.setConsumerCount(6);
        stats.setRemotingCount(5);
        stats.setGrpcCount(5);
        stats.setOnlineCount(9);
        stats.setOfflineCount(1);
        assertEquals(10, stats.getTotalCount());
        assertEquals(4, stats.getProducerCount());
        assertEquals(6, stats.getConsumerCount());
        assertEquals(5, stats.getRemotingCount());
        assertEquals(5, stats.getGrpcCount());
        assertEquals(9, stats.getOnlineCount());
        assertEquals(1, stats.getOfflineCount());
    }

    @Test
    public void testProtocolCompatibilityInfoAccessors() {
        ClientDiagnosticsUtil.ProtocolCompatibilityInfo info = new ClientDiagnosticsUtil.ProtocolCompatibilityInfo();
        info.addSupportedProtocol("Remoting", Arrays.asList("4.9"));
        info.addFeature("Remoting", "Low latency");
        info.addFeature("Remoting", "High throughput");
        assertNotNull(info.getSupportedProtocols().get("Remoting"));
        assertEquals(2, info.getProtocolFeatures().get("Remoting").size());
    }
}
