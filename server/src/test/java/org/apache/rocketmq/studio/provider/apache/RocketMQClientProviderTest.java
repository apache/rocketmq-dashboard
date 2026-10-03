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
package org.apache.rocketmq.studio.provider.apache;

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.remoting.exception.RemotingConnectException;
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
import org.apache.rocketmq.studio.cluster.client.ClientController;
import org.apache.rocketmq.studio.cluster.client.ClientService;
import org.apache.rocketmq.studio.cluster.client.ProducerConnectionScanResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.domain.enums.ClientLanguage;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQClientProviderTest {

    @Mock
    private DefaultMQAdminExt adminExt;

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private MqAdminExtFactory adminFactory;

    private RocketMQClientProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        lenient().when(adminExt.examineConsumerConnectionInfo(anyString(), anyString()))
                .thenReturn(new ConsumerConnection());
        provider = new RocketMQClientProvider(runtimeAdminClientResolver, adminFactory,
                new ProxyConsumerResolver(adminFactory, runtimeAdminClientResolver, new RocketMQProperties()));
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>
                        getArgument(1).apply(adminExt));
        lenient().when(adminFactory.execute(anyString(), any(), any(MqAdminExtFactory.AdminAction.class)))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>
                                getArgument(2).apply(adminExt));
    }

    @Test
    void clientsEndpointShowsProxyConsumersFromSelectedNameserverTest() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L))
                .thenReturn(subscriptionGroups("group-a"));
        when(adminExt.examineConsumerConnectionInfo("group-a", "127.0.0.1:10911"))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"));
        when(adminExt.examineConsumerConnectionInfo(
                "CID_DefaultHeartBeatSyncerTopic", "127.0.0.1:10911"))
                .thenReturn(consumerConnections(connection("syncer", "10.0.0.8:40000")));
        Connection proxyClient = connection("proxy-client", "10.0.0.9:50000");
        proxyClient.setVersion(org.apache.rocketmq.common.MQVersion.Version.V5_0_0.ordinal());
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenReturn(consumerConnections(proxyClient));

        org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryService registry =
                org.mockito.Mockito.mock(org.apache.rocketmq.studio.cluster.nameserver.NameserverRegistryService.class);
        when(registry.requireRegisteredAddress("selected:9876")).thenReturn("selected:9876");
        MockMvcBuilders.standaloneSetup(new ClientController(new ClientService(provider, registry))).build()
                .perform(get("/api/clients").param("namesrvAddr", "selected:9876")
                        .param("clusterId", "cluster-a").param("type", "Consumer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].clientId").value("proxy-client"))
                .andExpect(jsonPath("$.data[0].clusterName").value("cluster-a"))
                .andExpect(jsonPath("$.data[0].language").value("Java"))
                .andExpect(jsonPath("$.data[0].protocol").doesNotExist())
                .andExpect(jsonPath("$.data[0].version").doesNotExist());
        verify(adminFactory).execute(eq("selected:9876"), any(), any(MqAdminExtFactory.AdminAction.class));
        verify(runtimeAdminClientResolver, never()).execute(anyString(), any());
    }

    @Test
    void consumerScanMergesDirectAndAllProxyConnectionsTest() throws Exception {
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8", "10.0.0.9");
        Connection direct = connection("direct", "10.0.0.1:40000");
        when(adminExt.examineConsumerConnectionInfo("group-a", "127.0.0.1:10911"))
                .thenReturn(consumerConnections(direct));
        Connection unknown = connection("proxy-two", "10.0.0.3:40000");
        unknown.setLanguage(null);
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenReturn(consumerConnections(direct, connection("proxy-one", "10.0.0.2:40000")));
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.9:8080"))
                .thenReturn(consumerConnections(unknown));

        List<ClientConnectionVO> rows = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(rows).extracting(ClientConnectionVO::getClientId)
                .containsExactlyInAnyOrder("direct", "proxy-one", "proxy-two");
        assertThat(rows).filteredOn(row -> row.getClientId().equals("direct")).singleElement()
                .satisfies(row -> {
                    assertThat(row.getProtocol()).isEqualTo(org.apache.rocketmq.studio.common.domain.enums.Protocol.Remoting);
                    assertThat(row.getVersion()).isEqualTo(org.apache.rocketmq.common.MQVersion.getVersionDesc(direct.getVersion()));
                });
        assertThat(rows).filteredOn(row -> row.getClientId().equals("proxy-two")).singleElement()
                .satisfies(row -> {
                    assertThat(row.getLanguage()).isNull();
                    assertThat(row.getProtocol()).isNull();
                    assertThat(row.getVersion()).isNull();
                    assertThat(row.isPartial()).isFalse();
                });
    }

    @Test
    void proxyDiscoveryCannotLeakAcrossNameserversSharingBrokerAddressesTest() throws Exception {
        DefaultMQAdminExt other = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8");
        prepareProxyGroup(other, "127.0.0.1:10911", "10.1.0.8");
        when(adminFactory.execute(eq("other:9876"), any(), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(other));
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenReturn(consumerConnections(connection("selected-client", "10.0.0.1:40000")));
        when(other.examineConsumerConnectionInfo("group-a", "10.1.0.8:8080"))
                .thenReturn(consumerConnections(connection("other-client", "10.1.0.1:40000")));

        assertThat(provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .extracting(ClientConnectionVO::getClientId).containsExactly("selected-client");
        assertThat(provider.findConnectionsAt("other:9876", null, "Consumer"))
                .extracting(ClientConnectionVO::getClientId).containsExactly("other-client");
        assertThat(provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .extracting(ClientConnectionVO::getClientId).containsExactly("selected-client");
    }

    @Test
    void proxyScanPreservesSameGroupInDifferentClustersAndFiltersTest() throws Exception {
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(Map.of(
                "127.0.0.1:10911", "cluster-a", "127.0.0.2:10911", "cluster-b")));
        when(adminExt.getAllSubscriptionGroup("127.0.0.2:10911", 5000L))
                .thenReturn(subscriptionGroups("group-a"));
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic", "127.0.0.2:10911"))
                .thenReturn(consumerConnections(connection("syncer-b", "10.1.0.8:40000")));
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenReturn(consumerConnections(connection("client-a", "10.0.0.1:40000")));
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.1.0.8:8080"))
                .thenReturn(consumerConnections(connection("client-b", "10.1.0.1:40000")));

        assertThat(provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .extracting(ClientConnectionVO::getClusterName).containsExactlyInAnyOrder("cluster-a", "cluster-b");
        assertThat(provider.findConnectionsAt("selected:9876", "cluster-b", "Consumer")).singleElement()
                .satisfies(row -> {
                    assertThat(row.getClientId()).isEqualTo("client-b");
                    assertThat(row.getClusterName()).isEqualTo("cluster-b");
                });
        verify(adminExt, times(1)).examineConsumerConnectionInfo("group-a", "10.0.0.8:8080");
    }

    @Test
    void proxyFailureKeepsPartialRowsButCannotClaimOfflineTest() throws Exception {
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8");
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenThrow(new RemotingConnectException("proxy down"));
        when(adminExt.examineConsumerConnectionInfo("group-a", "127.0.0.1:10911"))
                .thenReturn(consumerConnections(connection("direct", "10.0.0.1:40000")))
                .thenThrow(new MQBrokerException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"));

        assertThat(provider.findConnectionsAt("selected:9876", null, "Consumer")).singleElement()
                .satisfies(row -> {
                    assertThat(row.getClientId()).isEqualTo("direct");
                    assertThat(row.isPartial()).isTrue();
                });
        assertThatThrownBy(() -> provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .isInstanceOf(BusinessException.class).hasMessage("Failed to query consumer connections from all groups");
        org.mockito.Mockito.doThrow(new MQBrokerException(ResponseCode.CONSUMER_NOT_ONLINE, "offline"))
                .when(adminExt).examineConsumerConnectionInfo("group-a", "10.0.0.8:8080");
        assertThat(provider.findConnectionsAt("selected:9876", null, "Consumer")).isEmpty();
    }

    @Test
    void proxyDiscoveryRetriesFailuresAndIsSharedOnlyWithinRequestTest() throws Exception {
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8");
        when(adminExt.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic", "127.0.0.1:10911"))
                .thenThrow(new RemotingConnectException("discovery down"))
                .thenReturn(consumerConnections(connection("syncer", "10.0.0.8:40000")));
        assertThatThrownBy(() -> provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .isInstanceOf(BusinessException.class);
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L))
                .thenReturn(subscriptionGroups("group-a", "group-b"));
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenReturn(consumerConnections(connection("proxy-client", "10.0.0.1:40000")));
        assertThat(provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .extracting(ClientConnectionVO::getClientId).containsExactly("proxy-client");
        verify(adminExt, times(2)).examineConsumerConnectionInfo(
                "CID_DefaultHeartBeatSyncerTopic", "127.0.0.1:10911");
    }

    @Test
    void directConsumerOnAnotherBrokerInTheClusterRemainsVisibleTest() throws Exception {
        ClusterInfo topology = clusterInfo("127.0.0.1:10911", "127.0.0.2:10911");
        String lastBroker = new java.util.ArrayList<>(topology.getBrokerAddrTable().values())
                .getLast().selectBrokerAddr();
        when(adminExt.examineBrokerClusterInfo()).thenReturn(topology);
        when(adminExt.getAllSubscriptionGroup(anyString(), anyLong())).thenReturn(subscriptionGroups("group-a"));
        when(adminExt.examineConsumerConnectionInfo("group-a", lastBroker))
                .thenReturn(consumerConnections(connection("direct", "10.0.0.1:40000")));
        assertThat(provider.findConnectionsAt("selected:9876", "cluster-a", "Consumer"))
                .extracting(ClientConnectionVO::getClientId).containsExactly("direct");
    }

    @Test
    void emptyPartialProxyInventoryIsAnErrorTest() throws Exception {
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8");
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L))
                .thenReturn(subscriptionGroups("group-a", "group-b"));
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenThrow(new RemotingConnectException("proxy down"));
        assertThatThrownBy(() -> provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void proxyQueriesPreserveInterruptsAndDoNotSwallowProgrammingErrorsTest() throws Exception {
        prepareProxyGroup(adminExt, "127.0.0.1:10911", "10.0.0.8");
        when(adminExt.examineConsumerConnectionInfo("group-a", "10.0.0.8:8080"))
                .thenThrow(new InterruptedException("cancelled"))
                .thenThrow(new IllegalArgumentException("invalid state"));
        try {
            assertThatThrownBy(() -> provider.findConnectionsAt("selected:9876", null, "Consumer"))
                    .isInstanceOf(BusinessException.class).hasMessage("Consumer connection query interrupted");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
        assertThatThrownBy(() -> provider.findConnectionsAt("selected:9876", null, "Consumer"))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("invalid state");
    }

    private void prepareProxyGroup(DefaultMQAdminExt admin, String broker, String... proxies) throws Exception {
        lenient().when(admin.examineConsumerConnectionInfo(anyString(), anyString()))
                .thenReturn(new ConsumerConnection());
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo(broker));
        when(admin.getAllSubscriptionGroup(broker, 5000L)).thenReturn(subscriptionGroups("group-a"));
        Connection[] syncers = java.util.Arrays.stream(proxies)
                .map(proxy -> connection("syncer-" + proxy, proxy + ":40000")).toArray(Connection[]::new);
        when(admin.examineConsumerConnectionInfo("CID_DefaultHeartBeatSyncerTopic", broker))
                .thenReturn(consumerConnections(syncers));
    }

    private static ConsumerConnection consumerConnections(Connection... connections) {
        ConsumerConnection result = new ConsumerConnection();
        result.setConnectionSet(new HashSet<>(List.of(connections)));
        return result;
    }

    @Test
    void findConnectionsAtShouldUseNameserverScopedAdminClientTest() throws Exception {
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(null);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        List<ClientConnectionVO> result = provider.findConnectionsAt("10.0.1.31:9876", null, null);

        assertThat(result).isEmpty();
        verify(adminFactory).execute(eq("10.0.1.31:9876"), any(), any(MqAdminExtFactory.AdminAction.class));
        verify(runtimeAdminClientResolver, never()).execute(anyString(), any());
    }

    @Test
    void connectionVersionShouldBeResolvedFromMQVersionCodeTest() throws Exception {
        Map<String, String> clusters = new HashMap<>();
        clusters.put("10.0.0.11:10911", "cluster-a");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(clusters));
        Map<String, List<ProducerInfo>> data = new HashMap<>();
        data.put("pg-order", List.of(new ProducerInfo(
                "client-1", "10.0.0.21:49152", LanguageCode.JAVA, 500, 1000L)));
        ProducerTableInfo producerTable = new ProducerTableInfo(data);
        when(adminExt.getAllProducerInfo("10.0.0.11:10911")).thenReturn(producerTable);

        List<ClientConnectionVO> connections = provider.findConnectionsAt("10.0.1.31:9876", null, "Producer");

        assertThat(connections).hasSize(1);
        assertThat(connections.get(0).getVersion())
                .isEqualTo(org.apache.rocketmq.common.MQVersion.getVersionDesc(500));
    }

    @Test
    void connectionScanShouldReportEveryStudioClientLanguageTest() throws Exception {
        Map<String, String> clusters = new HashMap<>();
        clusters.put("10.0.0.11:10911", "cluster-a");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(clusters));
        List<LanguageCode> languageCodes = List.of(
                LanguageCode.JAVA, LanguageCode.GO, LanguageCode.PYTHON, LanguageCode.RUST,
                LanguageCode.CPP, LanguageCode.DOTNET, LanguageCode.PHP, LanguageCode.NODE_JS);
        List<ProducerInfo> producers = IntStream.range(0, languageCodes.size())
                .mapToObj(index -> new ProducerInfo("client-" + index, "10.0.0.2" + index + ":49152",
                        languageCodes.get(index), 500, 1000L))
                .toList();
        Map<String, List<ProducerInfo>> data = new HashMap<>();
        data.put("pg-language", producers);
        when(adminExt.getAllProducerInfo("10.0.0.11:10911")).thenReturn(new ProducerTableInfo(data));

        List<ClientConnectionVO> connections = provider.findConnectionsAt("10.0.1.31:9876", null, "Producer");

        assertThat(connections).hasSize(languageCodes.size());
        assertThat(connections)
                .extracting(ClientConnectionVO::getLanguage)
                .containsExactlyInAnyOrder(ClientLanguage.values());
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
    void producerScanPreservesIdenticalConnectionsAcrossClusters() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(Map.of(
                "127.0.0.1:10911", "cluster-a",
                "127.0.0.2:10911", "cluster-b")));
        ProducerInfo shared = producerInfo("producer-client", "10.0.0.1:1000");
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of("pg-order", List.of(shared))));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of("pg-order", List.of(shared))));

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", null, "Producer");

        assertThat(connections).hasSize(2);
        assertThat(connections)
                .extracting(ClientConnectionVO::getClusterName)
                .containsExactlyInAnyOrder("cluster-a", "cluster-b");
    }

    @Test
    void clientScanUsesActualBrokerClustersAndFiltersRequestedCluster() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(Map.of(
                "127.0.0.1:10911", "cluster-a",
                "127.0.0.2:10911", "cluster-b")));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-a", List.of(producerInfo("producer-a", "10.0.0.1:1000")))));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-b", List.of(producerInfo("producer-b", "10.0.0.2:1000")))));

        List<ClientConnectionVO> allConnections = provider.findConnections("instance-a", null, "Producer");
        List<ClientConnectionVO> clusterBConnections = provider.findConnections("instance-a", "cluster-b", "Producer");

        assertThat(allConnections)
                .extracting(ClientConnectionVO::getClusterName)
                .containsExactlyInAnyOrder("cluster-a", "cluster-b");
        assertThat(clusterBConnections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("producer-b");
            assertThat(connection.getClusterName()).isEqualTo("cluster-b");
        });
        verify(adminExt).getAllProducerInfo("127.0.0.1:10911");
        verify(adminExt, times(2)).getAllProducerInfo("127.0.0.2:10911");
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
    void producerGroupSelectorReturnsSortedUniqueBoundedMatches() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        " pg-payment ", List.of(producerInfo("producer-payment", "10.0.0.2:1000")),
                        "pg-order", List.of(producerInfo("producer-order", "10.0.0.1:1000")))));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-order", List.of(producerInfo("producer-order-2", "10.0.0.3:1000")),
                        "pg-shipment", List.of(producerInfo("producer-shipment", "10.0.0.4:1000")),
                        " ", List.of(producerInfo("ignored", "10.0.0.5:1000")))));

        List<String> groups = provider.findProducerGroups("instance-a", "TopicA", "pg", 2);

        assertThat(groups).containsExactly("pg-order", "pg-payment");
        verify(adminExt, never()).examineProducerConnectionInfo(anyString(), anyString());
    }

    @Test
    void producerGroupSelectorFailsWhenEveryBrokerQueryFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllProducerInfo(anyString()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.findProducerGroups("instance-a", "TopicA", "pg", 20))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query producer groups from all brokers")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void producerGroupSelectorKeepsBestEffortResultsWhenOneBrokerFailsTest() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenThrow(new IllegalStateException("broker unavailable"));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-payment", List.of(producerInfo("producer-payment", "10.0.0.2:1000")))));

        List<String> groups = provider.findProducerGroups("instance-a", "TopicA", "pg", 20);

        assertThat(groups).containsExactly("pg-payment");
    }

    @Test
    void exactProducerQueryPassesNonBlankGroupToAdminApi() throws Exception {
        ProducerConnection producerConnection = new ProducerConnection();
        producerConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-client", "10.0.0.1:1000"))));
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA"))
                .thenReturn(producerConnection);

        List<ClientConnectionVO> connections = provider.findProducerConnections("instance-a", "TopicA", "pg-order");

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("producer-client");
            assertThat(connection.getGroupOrTopic()).isEqualTo("TopicA");
            assertThat(connection.getProducerGroup()).isEqualTo("pg-order");
        });
        verify(adminExt).examineProducerConnectionInfo("pg-order", "TopicA");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
    }

    @Test
    void producerQueryWithoutGroupScansActiveProducerGroups() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-order", List.of(producerInfo("producer-order", "10.0.0.1:1000")),
                        "pg-payment", List.of(producerInfo("producer-payment", "10.0.0.2:1000")))));
        ProducerConnection orderConnection = new ProducerConnection();
        orderConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-order", "10.0.0.1:1000"))));
        ProducerConnection paymentConnection = new ProducerConnection();
        paymentConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-payment", "10.0.0.2:1000"))));
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA"))
                .thenReturn(orderConnection);
        when(adminExt.examineProducerConnectionInfo("pg-payment", "TopicA"))
                .thenReturn(paymentConnection);

        List<ClientConnectionVO> connections = provider.findProducerConnections("instance-a", "TopicA", null);

        assertThat(connections)
                .extracting(ClientConnectionVO::getProducerGroup)
                .containsExactly("pg-order", "pg-payment");
        assertThat(connections)
                .extracting(ClientConnectionVO::getGroupOrTopic)
                .containsOnly("TopicA");
        verify(adminExt).getAllProducerInfo("127.0.0.1:10911");
        verify(adminExt).examineProducerConnectionInfo("pg-order", "TopicA");
        verify(adminExt).examineProducerConnectionInfo("pg-payment", "TopicA");
    }

    @Test
    void producerQueryWithoutGroupReturnsPartialResultWhenOneGroupQueryFailsTest() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-order", List.of(producerInfo("producer-order", "10.0.0.1:1000")),
                        "pg-payment", List.of(producerInfo("producer-payment", "10.0.0.2:1000")))));
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA"))
                .thenThrow(new IllegalStateException("broker unavailable"));
        ProducerConnection paymentConnection = new ProducerConnection();
        paymentConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-payment", "10.0.0.2:1000"))));
        when(adminExt.examineProducerConnectionInfo("pg-payment", "TopicA"))
                .thenReturn(paymentConnection);

        ProducerConnectionScanResult result =
                provider.scanProducerConnections("instance-a", "TopicA", null);

        assertThat(result.connections()).singleElement().satisfies(connection ->
                assertThat(connection.getProducerGroup()).isEqualTo("pg-payment"));
        assertThat(result.complete()).isFalse();
        assertThat(result.failedBrokers()).isEmpty();
        assertThat(result.failedProducerGroups()).containsExactly("pg-order");
    }

    @Test
    void producerQueryWithoutGroupReturnsPartialResultWhenOneBrokerGroupDiscoveryFailsTest() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenThrow(new IllegalStateException("broker unavailable"));
        when(adminExt.getAllProducerInfo("127.0.0.2:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-payment", List.of(producerInfo("producer-payment", "10.0.0.2:1000")))));
        ProducerConnection paymentConnection = new ProducerConnection();
        paymentConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-payment", "10.0.0.2:1000"))));
        when(adminExt.examineProducerConnectionInfo("pg-payment", "TopicA"))
                .thenReturn(paymentConnection);

        ProducerConnectionScanResult result =
                provider.scanProducerConnections("instance-a", "TopicA", null);

        assertThat(result.connections()).singleElement().satisfies(connection ->
                assertThat(connection.getProducerGroup()).isEqualTo("pg-payment"));
        assertThat(result.complete()).isFalse();
        assertThat(result.failedBrokers()).containsExactly("127.0.0.1:10911");
        assertThat(result.failedProducerGroups()).isEmpty();
    }

    @Test
    void producerQueryWithoutGroupTreatsOfflineGroupAsCompleteEmptyResultTest() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllProducerInfo("127.0.0.1:10911"))
                .thenReturn(new ProducerTableInfo(Map.of(
                        "pg-offline", List.of(producerInfo("offline-producer", "10.0.0.1:1000")),
                        "pg-payment", List.of(producerInfo("producer-payment", "10.0.0.2:1000")))));
        when(adminExt.examineProducerConnectionInfo("pg-offline", "TopicA"))
                .thenThrow(new MQClientException("Not found the producer group connection", null));
        ProducerConnection paymentConnection = new ProducerConnection();
        paymentConnection.setConnectionSet(new HashSet<>(List.of(
                connection("producer-payment", "10.0.0.2:1000"))));
        when(adminExt.examineProducerConnectionInfo("pg-payment", "TopicA"))
                .thenReturn(paymentConnection);

        ProducerConnectionScanResult result =
                provider.scanProducerConnections("instance-a", "TopicA", null);

        assertThat(result.connections()).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("producer-payment");
            assertThat(connection.getProducerGroup()).isEqualTo("pg-payment");
        });
        assertThat(result.complete()).isTrue();
        assertThat(result.failedBrokers()).isEmpty();
        assertThat(result.failedProducerGroups()).isEmpty();
    }

    @Test
    void exactProducerQueryTranslatesAdminFailureToBadGateway() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA"))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.findProducerConnections("instance-a", "TopicA", "pg-order"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query producer connections: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void exactProducerQueryReturnsEmptyForNonExistentTopic() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "NoSuchTopic"))
                .thenThrow(new MQClientException(ResponseCode.TOPIC_NOT_EXIST,
                        "CAN'T FIND BROKER FOR TOPIC: NoSuchTopic"));

        List<ClientConnectionVO> connections = provider.findProducerConnections(
                "instance-a", "NoSuchTopic", "pg-order");

        assertThat(connections).isEmpty();
        verify(adminExt).examineProducerConnectionInfo("pg-order", "NoSuchTopic");
    }

    @Test
    void exactProducerQueryReturnsEmptyWhenTopicHasNoRoute() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "NoRouteTopic"))
                .thenThrow(new MQClientException(ResponseCode.TOPIC_NOT_EXIST,
                        "connect to ns failed, route info of this topic not found"));

        List<ClientConnectionVO> connections = provider.findProducerConnections(
                "instance-a", "NoRouteTopic", "pg-order");

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
    void consumerScanFiltersSubscriptionGroupsByClusterAndPreservesClusterName() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(Map.of(
                "127.0.0.1:10911", "cluster-a",
                "127.0.0.2:10911", "cluster-b")));
        when(adminExt.getAllSubscriptionGroup("127.0.0.2:10911", 5000L))
                .thenReturn(subscriptionGroups("group-b"));
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(new HashSet<>(List.of(connection("consumer-b", "10.0.0.2:1000"))));
        when(adminExt.examineConsumerConnectionInfo("group-b", "127.0.0.2:10911")).thenReturn(consumerConnection);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-b", "Consumer");

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("consumer-b");
            assertThat(connection.getClusterName()).isEqualTo("cluster-b");
        });
        verify(adminExt, never()).getAllSubscriptionGroup("127.0.0.1:10911", 5000L);
        verify(adminExt).getAllSubscriptionGroup("127.0.0.2:10911", 5000L);
    }

    @Test
    void consumerScanFailsWhenBrokerDiscoveryFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.findConnections("instance-a", "cluster-a", "Consumer"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to discover brokers for consumer connections: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
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
        when(adminExt.examineConsumerConnectionInfo("group-a", "127.0.0.1:10911")).thenReturn(consumerConnection);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(connections).hasSize(1);
        assertThat(connections.get(0).getClientId()).isEqualTo("consumer-client");
    }

    @Test
    void consumerScanFailsWhenEveryBrokerGroupQueryFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo(
                "127.0.0.1:10911", "127.0.0.2:10911"));
        when(adminExt.getAllSubscriptionGroup(anyString(), anyLong())).thenThrow(
                new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> provider.findConnections("instance-a", "cluster-a", "Consumer"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query subscription groups from all brokers")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void consumerScanFailsWhenEveryGroupConnectionQueryFails() throws Exception {
        SubscriptionGroupWrapper wrapper = subscriptionGroups("group-a", "group-b");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);
        when(adminExt.examineConsumerConnectionInfo(anyString(), anyString()))
                .thenThrow(new RemotingConnectException("broker unavailable"));

        assertThatThrownBy(() -> provider.findConnections("instance-a", "cluster-a", "Consumer"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to query consumer connections from all groups")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(502));
    }

    @Test
    void consumerScanWithOnlySystemGroupsReturnsEmptyInsteadOf502() throws Exception {
        SubscriptionGroupWrapper wrapper = subscriptionGroups("%RETRY%group-a", "%DLQ%group-b");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(connections).isEmpty();
        verify(adminExt, never()).examineConsumerConnectionInfo(anyString(), anyString());
    }

    @Test
    void consumerScanReturnsPartialResultsWhenOneGroupQueryFails() throws Exception {
        SubscriptionGroupWrapper wrapper = subscriptionGroups("group-a", "group-b");
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(new HashSet<>(List.of(connection("consumer-client", "10.0.0.2:1000"))));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);
        when(adminExt.examineConsumerConnectionInfo("group-a", "127.0.0.1:10911"))
                .thenThrow(new RemotingConnectException("broker unavailable"));
        when(adminExt.examineConsumerConnectionInfo("group-b", "127.0.0.1:10911")).thenReturn(consumerConnection);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("consumer-client");
            assertThat(connection.getGroupOrTopic()).isEqualTo("group-b");
        });
    }

    @Test
    void consumerScanTreatsOfflineGroupsAsEmptyInsteadOf502() throws Exception {
        SubscriptionGroupWrapper wrapper = subscriptionGroups("group-a", "group-b");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);
        when(adminExt.examineConsumerConnectionInfo(anyString(), anyString())).thenThrow(new MQClientException(
                206, "Not found the consumer group connection"));

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", null);

        assertThat(connections).isEmpty();
    }

    @Test
    void consumerScanReturnsOfflineGroupResultsWhenAnotherGroupFails() throws Exception {
        SubscriptionGroupWrapper wrapper = subscriptionGroups("group-a", "group-b");
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(new HashSet<>(List.of(connection("consumer-client", "10.0.0.2:1000"))));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo("127.0.0.1:10911"));
        when(adminExt.getAllSubscriptionGroup("127.0.0.1:10911", 5000L)).thenReturn(wrapper);
        when(adminExt.examineConsumerConnectionInfo("group-a", "127.0.0.1:10911")).thenThrow(new MQClientException(
                206, "Not found the consumer group connection"));
        when(adminExt.examineConsumerConnectionInfo("group-b", "127.0.0.1:10911")).thenReturn(consumerConnection);

        List<ClientConnectionVO> connections = provider.findConnections("instance-a", "cluster-a", "Consumer");

        assertThat(connections).singleElement().satisfies(connection -> {
            assertThat(connection.getClientId()).isEqualTo("consumer-client");
            assertThat(connection.getGroupOrTopic()).isEqualTo("group-b");
        });
    }

    @Test
    void producerQueryWithExplicitOfflineGroupReturnsEmptyInsteadOf502() throws Exception {
        when(adminExt.examineProducerConnectionInfo("pg-order", "TopicA")).thenThrow(
                new MQClientException("Not found the producer group connection", null));

        List<ClientConnectionVO> connections =
                provider.findProducerConnections("instance-a", "TopicA", "pg-order");

        assertThat(connections).isEmpty();
    }

    private static SubscriptionGroupWrapper subscriptionGroups(String... names) {
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        ConcurrentHashMap<String, SubscriptionGroupConfig> groups = new ConcurrentHashMap<>();
        for (String name : names) {
            groups.put(name, new SubscriptionGroupConfig());
        }
        wrapper.setSubscriptionGroupTable(groups);
        return wrapper;
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
        Map<String, String> clusters = new HashMap<>();
        for (int i = 0; i < brokerAddresses.length; i++) {
            clusters.put(brokerAddresses[i], "cluster-a");
        }
        return clusterInfo(clusters);
    }

    private static ClusterInfo clusterInfo(Map<String, String> clustersByAddress) {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        int i = 0;
        for (Map.Entry<String, String> entry : clustersByAddress.entrySet()) {
            String brokerName = "broker-" + i;
            brokerAddrTable.put(brokerName, new BrokerData(
                    entry.getValue(), brokerName, new HashMap<>(Map.of(0L, entry.getKey()))));
            i++;
        }
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }
}
