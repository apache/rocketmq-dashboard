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

import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.attribute.TopicMessageType;
import org.apache.rocketmq.common.lite.LiteUtil;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.GetBrokerLiteInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.body.GetLiteClientInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.body.GetLiteGroupInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.body.GetParentTopicInfoResponseBody;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.model.LiteTopicQuota;
import org.apache.rocketmq.studio.model.LiteTopicSession;
import org.apache.rocketmq.studio.model.LiteTopicSummary;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQLiteTopicProviderTest {

    private static final String NAMESRV = "127.0.0.1:9876";
    private static final String BROKER_A = "broker-a:10911";
    private static final String PARENT = "chat";
    private static final String GROUP = "lite-group";

    private MQAdminExt admin;
    private RocketMQLiteTopicProvider provider;

    @BeforeEach
    void setUp() {
        admin = mock(MQAdminExt.class);
        MqAdminExtFactory factory = mock(MqAdminExtFactory.class);
        doAnswer(invocation -> {
            MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(2);
            return action.apply(admin);
        }).when(factory).execute(anyString(), nullable(RPCHook.class), any());
        RocketMQProperties properties = new RocketMQProperties();
        properties.setNamesrvAddr(NAMESRV);
        provider = new RocketMQLiteTopicProvider(factory, properties);
    }

    @Test
    void isSupportedIsFalseWithoutConfiguredNameServer() {
        assertThat(new RocketMQLiteTopicProvider(mock(MqAdminExtFactory.class), new RocketMQProperties())
                .isSupported()).isFalse();
    }

    @Test
    void isSupportedIsFalseWhenBrokerRejectsTheLiteProbe() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.getBrokerLiteInfo(BROKER_A)).thenThrow(new IllegalStateException("unsupported"));

        assertThat(provider.isSupported()).isFalse();
    }

    @Test
    void isSupportedIsTrueWhenBrokerAnswersTheLiteProbe() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.getBrokerLiteInfo(BROKER_A)).thenReturn(brokerLiteInfo(2, 40, 1));

        assertThat(provider.isSupported()).isTrue();
    }

    @Test
    void listLiteTopicsAggregatesParentTopicTtlBacklogAndSessions() throws Exception {
        long lastAccess = System.currentTimeMillis() - 1_000;
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.getBrokerLiteInfo(BROKER_A)).thenReturn(brokerLiteInfo(PARENT, 30, 2, GROUP));
        when(admin.getParentTopicInfo(BROKER_A, PARENT)).thenReturn(parentTopicInfo(PARENT, 30, 2));
        when(admin.getLiteGroupInfo(BROKER_A, GROUP, null, 1)).thenReturn(lag(7));
        when(admin.examineConsumerConnectionInfo(GROUP)).thenReturn(consumerConnection("c1", "10.0.0.9:1234"));
        when(admin.getLiteClientInfo(BROKER_A, PARENT, GROUP, "c1")).thenReturn(clientInfo(2, lastAccess));

        List<LiteTopicSummary> summaries = provider.listLiteTopics(null, null);

        assertThat(summaries).singleElement().satisfies(summary -> {
            assertThat(summary.getTopicPattern()).isEqualTo(PARENT);
            assertThat(summary.getNamespace()).isEqualTo(RocketMQLiteTopicProvider.DEFAULT_NAMESPACE);
            assertThat(summary.getTopicCount()).isEqualTo(2);
            assertThat(summary.getConsumerCount()).isEqualTo(1);
            assertThat(summary.getTotalBacklog()).isEqualTo(7L);
            assertThat(summary.getAverageTTL()).isEqualTo(TimeUnit.MINUTES.toMillis(30));
            assertThat(summary.getSessionIds())
                    .containsExactly(RocketMQLiteTopicProvider.encodeSessionId(PARENT, GROUP, "c1"));
            assertThat(summary.getLastActiveTime()).isNotNull();
            assertThat(summary.getTTLStatus()).isEqualTo("ACTIVE");
        });
    }

    @Test
    void listLiteTopicsFiltersByPatternCaseInsensitively() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.getBrokerLiteInfo(BROKER_A)).thenReturn(brokerLiteInfo(PARENT, 30, 1, GROUP));
        when(admin.getParentTopicInfo(BROKER_A, PARENT)).thenReturn(parentTopicInfo(PARENT, 30, 1));
        when(admin.getLiteGroupInfo(BROKER_A, GROUP, null, 1)).thenReturn(lag(0));
        when(admin.examineConsumerConnectionInfo(GROUP)).thenReturn(new ConsumerConnection());
        when(admin.getLiteClientInfo(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(clientInfo(1, System.currentTimeMillis()));

        assertThat(provider.listLiteTopics("CHAT", null)).hasSize(1);
        assertThat(provider.listLiteTopics("orders", null)).isEmpty();
        assertThat(provider.listLiteTopics(null, "default")).hasSize(1);
        assertThat(provider.listLiteTopics(null, "other")).isEmpty();
    }

    @Test
    void getSessionResolvesTheBrokerOwningTheClientAndComputesProgress() throws Exception {
        long lastAccess = System.currentTimeMillis() - 1_000;
        Map<String, BrokerData> brokers = new LinkedHashMap<>();
        brokers.put("broker-a", broker(BROKER_A));
        brokers.put("broker-b", broker("broker-b:10911"));
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(brokers);
        when(admin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        // The first master does not hold this client's lite subscription; the second one does.
        when(admin.getLiteClientInfo(BROKER_A, PARENT, GROUP, "c1")).thenReturn(clientInfo(-1, 0));
        when(admin.getLiteClientInfo("broker-b:10911", PARENT, GROUP, "c1"))
                .thenReturn(clientInfo(1, lastAccess, LiteUtil.toLmqName(PARENT, "bob")));
        when(admin.getParentTopicInfo("broker-b:10911", PARENT)).thenReturn(parentTopicInfo(PARENT, 30, 1));
        when(admin.getLiteGroupInfo("broker-b:10911", GROUP, null, 1)).thenReturn(lag(5));
        when(admin.getLiteGroupInfo("broker-b:10911", GROUP, "bob", 1)).thenReturn(consumed(10, 10));
        when(admin.examineConsumerConnectionInfo(GROUP)).thenReturn(consumerConnection("c1", "10.0.0.9:1234"));

        LiteTopicSession session = provider.getSession(
                RocketMQLiteTopicProvider.encodeSessionId(PARENT, GROUP, "c1"));

        assertThat(session.getSessionId()).isEqualTo("chat~lite-group~c1");
        assertThat(session.getClientId()).isEqualTo("c1");
        assertThat(session.getClientAddress()).isEqualTo("10.0.0.9:1234");
        assertThat(session.getParentTopic()).isEqualTo(PARENT);
        assertThat(session.getConsumerGroup()).isEqualTo(GROUP);
        assertThat(session.getTtl()).isEqualTo(TimeUnit.MINUTES.toMillis(30));
        assertThat(session.getTtlRemaining()).isGreaterThan(0);
        assertThat(session.getStatus()).isEqualTo("ACTIVE");
        assertThat(session.getLiteTopicCreationCount()).isEqualTo(1);
        assertThat(session.getLiteTopics()).containsExactly("bob");
        // pending lag (5) plus the committed offset read back from the lite topic (10).
        assertThat(session.getPendingMessages()).isEqualTo(5L);
        assertThat(session.getConsumedMessages()).isEqualTo(10L);
        assertThat(session.getTotalMessages()).isEqualTo(15L);
    }

    @Test
    void getSessionFailsWhenNoBrokerReportsTheClient() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.getLiteClientInfo(anyString(), anyString(), anyString(), anyString()))
                .thenReturn(clientInfo(-1, 0));

        assertThatThrownBy(() -> provider.getSession("chat~lite-group~c1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(404));
    }

    @Test
    void getSessionRejectsMalformedSessionId() {
        assertThatThrownBy(() -> provider.getSession("not-a-session"))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(400));
    }

    @Test
    void extendTtlConvertsMillisecondsAndUpdatesTheLiteParentTopic() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.examineTopicConfig(BROKER_A, PARENT)).thenReturn(liteTopicConfig(PARENT, 30));

        provider.extendTTL(PARENT, TimeUnit.MINUTES.toMillis(120));

        ArgumentCaptor<TopicConfig> captor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(admin).createAndUpdateTopicConfig(eq(BROKER_A), captor.capture());
        // The update must be expressed in the broker's "+key=value" change protocol and must not
        // re-send the immutable message.type attribute.
        Map<String, String> change = captor.getValue().getAttributes();
        assertThat(change).containsEntry("+lite.topic.expiration", "120");
        assertThat(change).hasSize(1);
    }

    @Test
    void extendTtlClampsToTheProtocolMaximum() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.examineTopicConfig(BROKER_A, PARENT)).thenReturn(liteTopicConfig(PARENT, 30));

        provider.extendTTL(PARENT, TimeUnit.DAYS.toMillis(90));

        ArgumentCaptor<TopicConfig> captor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(admin).createAndUpdateTopicConfig(eq(BROKER_A), captor.capture());
        assertThat(captor.getValue().getAttributes())
                .containsEntry("+lite.topic.expiration",
                        String.valueOf(RocketMQLiteTopicProvider.MAX_LITE_TTL_MINUTES));
    }

    @Test
    void extendTtlFailsForANonLiteTopic() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.examineTopicConfig(BROKER_A, PARENT)).thenReturn(new TopicConfig(PARENT));

        assertThatThrownBy(() -> provider.extendTTL(PARENT, 60_000L))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(404));
        verify(admin, never()).createAndUpdateTopicConfig(anyString(), any());
    }

    @Test
    void getQuotaAggregatesBrokerLiteCapacityAndConfigLimits() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(cluster(BROKER_A));
        when(admin.getBrokerLiteInfo(BROKER_A)).thenReturn(brokerLiteInfo(3, 40, 3));
        Properties brokerConfig = new Properties();
        brokerConfig.setProperty("maxLiteSubscriptionCount", "100000");
        brokerConfig.setProperty("minLiteTTl", "900000");
        when(admin.getBrokerConfig(BROKER_A)).thenReturn(brokerConfig);

        LiteTopicQuota quota = provider.getQuota(null);

        assertThat(quota.getCurrentTopicCount()).isEqualTo(3);
        assertThat(quota.getMaxTopicCount()).isEqualTo(40);
        assertThat(quota.getCurrentSessionCount()).isEqualTo(3);
        assertThat(quota.getMaxSessionCount()).isEqualTo(100_000);
        assertThat(quota.getDefaultTTL()).isEqualTo(900_000L);
        assertThat(quota.getMaxTTL()).isEqualTo(TimeUnit.MINUTES.toMillis(
                RocketMQLiteTopicProvider.MAX_LITE_TTL_MINUTES));
        assertThat(quota.getRemainingQuota()).isEqualTo(37);
    }

    @Test
    void quotaFailsWhenNoBrokerMasterIsReachable() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(new ClusterInfo());

        assertThatThrownBy(() -> provider.getQuota(null))
                .isInstanceOfSatisfying(BusinessException.class,
                        ex -> assertThat(ex.getCode()).isEqualTo(503));
    }

    // ─── Fixtures ─────────────────────────────────────────────────────

    private static ClusterInfo cluster(String... masterAddresses) {
        Map<String, BrokerData> table = new LinkedHashMap<>();
        for (String address : masterAddresses) {
            table.put(address, broker(address));
        }
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(table);
        return clusterInfo;
    }

    private static BrokerData broker(String masterAddress) {
        BrokerData data = new BrokerData();
        data.setBrokerAddrs(new HashMap<>(Map.of(0L, masterAddress)));
        return data;
    }

    private static GetBrokerLiteInfoResponseBody brokerLiteInfo(int currentLmq, int maxLmq,
                                                               int liteSubscriptions) {
        GetBrokerLiteInfoResponseBody body = new GetBrokerLiteInfoResponseBody();
        body.setCurrentLmqNum(currentLmq);
        body.setMaxLmqNum(maxLmq);
        body.setLiteSubscriptionCount(liteSubscriptions);
        return body;
    }

    private static GetBrokerLiteInfoResponseBody brokerLiteInfo(String parentTopic, int ttlMinutes,
                                                                int currentLmq, String group) {
        GetBrokerLiteInfoResponseBody body = brokerLiteInfo(currentLmq, 40, 0);
        Map<String, Integer> topicMeta = new LinkedHashMap<>();
        topicMeta.put(parentTopic, ttlMinutes);
        body.setTopicMeta(topicMeta);
        Map<String, Set<String>> groupMeta = new LinkedHashMap<>();
        groupMeta.put(parentTopic, new HashSet<>(Set.of(group)));
        body.setGroupMeta(groupMeta);
        return body;
    }

    private static GetParentTopicInfoResponseBody parentTopicInfo(String topic, int ttl, int liteTopicCount) {
        GetParentTopicInfoResponseBody body = new GetParentTopicInfoResponseBody();
        body.setTopic(topic);
        body.setTtl(ttl);
        body.setLiteTopicCount(liteTopicCount);
        return body;
    }

    private static GetLiteGroupInfoResponseBody lag(long totalLag) {
        GetLiteGroupInfoResponseBody body = new GetLiteGroupInfoResponseBody();
        body.setTotalLagCount(totalLag);
        return body;
    }

    private static GetLiteGroupInfoResponseBody consumed(long brokerOffset, long consumerOffset) {
        OffsetWrapper wrapper = new OffsetWrapper();
        wrapper.setBrokerOffset(brokerOffset);
        wrapper.setConsumerOffset(consumerOffset);
        GetLiteGroupInfoResponseBody body = new GetLiteGroupInfoResponseBody();
        body.setLiteTopicOffsetWrapper(wrapper);
        return body;
    }

    private static GetLiteClientInfoResponseBody clientInfo(int liteTopicCount, long lastAccessTime,
                                                            String... lmqNames) {
        GetLiteClientInfoResponseBody body = new GetLiteClientInfoResponseBody();
        body.setLiteTopicCount(liteTopicCount);
        body.setLastAccessTime(lastAccessTime);
        body.setLiteTopicSet(new HashSet<>(Set.of(lmqNames)));
        return body;
    }

    private static ConsumerConnection consumerConnection(String clientId, String clientAddr) {
        Connection connection = new Connection();
        connection.setClientId(clientId);
        connection.setClientAddr(clientAddr);
        ConsumerConnection consumerConnection = new ConsumerConnection();
        consumerConnection.setConnectionSet(new HashSet<>(Set.of(connection)));
        return consumerConnection;
    }

    private static TopicConfig liteTopicConfig(String topic, int ttlMinutes) {
        TopicConfig config = new TopicConfig(topic);
        config.setTopicMessageType(TopicMessageType.LITE);
        config.setLiteTopicExpiration(ttlMinutes);
        return config;
    }
}
