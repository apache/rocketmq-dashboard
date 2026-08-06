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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerInfo;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerTableInfo;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.client.ClientConnectionVO;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQClientProviderTest {

    @Mock
    private ObjectProvider<DefaultMQAdminExt> adminExtProvider;

    @Mock
    private DefaultMQAdminExt adminExt;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    private RocketMQClientProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(adminExtProvider.getIfAvailable()).thenReturn(adminExt);
        provider = new RocketMQClientProvider(adminExtProvider, runtimeAdminClientResolver);
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation ->
                invocation.<org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory.AdminAction<Object>>
                        getArgument(1).apply(adminExt));
    }

    @Test
    void producerScanTreatsMissingBrokerMetadataAsEmpty() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(null);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Producer");

        assertThat(connections).isEmpty();
        verify(adminExt).examineBrokerClusterInfo();
        verify(adminExt, never()).getAllProducerInfo(anyString());
        verify(adminExt, never()).examineProducerConnectionInfo(anyString(), anyString());
    }

    @Test
    void producerScanAggregatesAndDeduplicatesBrokerProducerTables() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        ProducerInfo shared = producerInfo("producer-client", "10.0.0.1:1000");
        ProducerInfo another = producerInfo("producer-client-2", "10.0.0.2:1000");
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of("pg-order", List.of(shared))));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-order", List.of(shared),
                        "pg-payment", List.of(another))));

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Producer");

        assertThat(connections).hasSize(2);
        assertThat(connections)
                .extracting(ClientConnectionVO::getProducerGroup)
                .containsExactlyInAnyOrder("pg-order", "pg-payment");
        assertThat(connections)
                .extracting(ClientConnectionVO::getGroupOrTopic)
                .containsExactlyInAnyOrder("pg-order", "pg-payment");
        verify(adminExt, never()).examineProducerConnectionInfo(anyString(), anyString());
    }

    @Test
    void producerScanReturnsPartialResultsWhenOneBrokerFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenThrow(new IllegalStateException("broker unavailable"));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-order", List.of(producerInfo("producer-client", "10.0.0.1:1000")))));

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Producer");

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("producer-client");
            assertThat(connection.getProducerGroup()).isEqualTo("pg-order");
        });
    }

    @Test
    void producerScanFailsWhenEveryBrokerQueryFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllProducerInfo(anyString()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.findConnections("instance-a", "cluster-a", "Producer"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query producer connections from all brokers")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void exactProducerQueryPassesNonBlankGroupToAdminApi() throws Exception {
        ProducerConnection producerConnection = new ProducerConnection();
        producerConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-client", "10.0.0.1:1000"))));
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA"))
                .thenReturn(producerConnection);

        List<ClientConnectionVO> connections = provider.findProducerConnections("TopicA", "pg-order");

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("producer-client");
            assertThat(connection.getGroupOrTopic()).isEqualTo("TopicA");
            assertThat(connection.getProducerGroup()).isEqualTo("pg-order");
        });
        verify(adminExt).examineProducerConnectionInfo("pg-order", "TopicA");
    }

    @Test
    void exactProducerQueryTranslatesAdminFailureToBadGateway() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA"))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.findProducerConnections("TopicA", "pg-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query producer connections: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void exactProducerQueryReturnsEmptyForNonExistentTopic() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "NoSuchTopic"))
                .thenThrow(new MQClientException(ResponseCode.TOPIC_NOT_EXIST,
                        "CAN'T FIND BROKER FOR TOPIC: NoSuchTopic"));

        List<ClientConnectionVO> connections = provider.findProducerConnections("NoSuchTopic", "pg-order");

        assertThat(connections).isEmpty();
        verify(adminExt).examineProducerConnectionInfo("pg-order", "NoSuchTopic");
    }

    @Test
    void exactProducerQueryReturnsEmptyWhenTopicHasNoRoute() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "NoRouteTopic"))
                .thenThrow(new MQClientException(ResponseCode.TOPIC_NOT_EXIST,
                        "connect to ns failed, route info of this topic not found"));

        List<ClientConnectionVO> connections = provider.findProducerConnections("NoRouteTopic", "pg-order");

        assertThat(connections).isEmpty();
    }

    @Test
    void consumerScanSkipsNullBrokerMetadata() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broken-broker", null);
        brokerAddrTable.put("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "127.0.0.1:10911"))));
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        wrapper.setSubscriptionGroupTable(new ConcurrentHashMap<>());
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(connections).isEmpty();
        verify(adminExt).examineBrokerClusterInfo();
        verify(adminExt).getAllSubscriptionGroup("127.0.0.1:10911", 5000L);
    }

    @Test
    void consumerScanSkipsNullConnectionEntries() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(Map.of("broker-a", new BrokerData("cluster-a", "broker-a",
                new HashMap<>(Map.of(0L, "127.0.0.1:10911")))));
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        ConcurrentHashMap<String, SubscriptionGroupConfig> groups = new ConcurrentHashMap<>();
        groups.put("group-a", new SubscriptionGroupConfig());
        wrapper.setSubscriptionGroupTable(groups);
        ConsumerConnection consumerConnection = new ConsumerConnection();
        HashSet<Connection> connectionSet = new HashSet<>();
        connectionSet.add(null);
        connectionSet.add(connection("consumer-client", "10.0.0.2:1000"));
        consumerConnection.setConnectionSet(connectionSet);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);
        when(adminExt.examineConsumerConnectionInfo("group-a")).thenReturn(consumerConnection);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(connections).hasSize(1);
        assertThat(connections.get(0).getClientId()).isEqualTo("consumer-client");
    }

    private static Connection connection(String clientId, String clientAddr) {
        Connection connection = new Connection();
        connection.setClientId(clientId);
        connection.setClientAddr(clientAddr);
        connection.setLanguage(LanguageCode.JAVA);
        connection.setVersion(500);
        return connection;
    }

    private static ProducerInfo producerInfo(String clientId, String remoteIp) {
        return new ProducerInfo(clientId, remoteIp, LanguageCode.JAVA, 500, 1000L);
    }

    private static ClusterInfo clusterInfo(String... brokerAddresses) {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        for (int i = 0; i < brokerAddresses.length; i++) {
            String brokerName = "broker-" + i;
            brokerAddrTable.put(brokerName, new BrokerData(
                    "cluster-a", brokerName, new HashMap<>(Map.of(0L, brokerAddresses[i]))));
        }
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }
}
