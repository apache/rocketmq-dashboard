/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package org.apache.rocketmq.studio.provider.apache;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsCommand;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsVO;
import org.apache.rocketmq.studio.instance.group.ConsumerInstanceVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetQueuePreviewVO;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQAdminClientImplTest {

    @Mock
    private MqAdminExtFactory adminFactory;
    @Mock
    private DefaultMQAdminExt adminExt;
    @Mock
    private RocketMQProperties properties;
    @Mock
    private RmqTopicMapper topicMapper;
    @Mock
    private RmqGroupMapper groupMapper;
    @Mock
    private AuditService auditService;
    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;
    @Mock
    private MqClientPool clientPool;
    @Mock
    private DefaultMQProducer sendProducer;

    private RocketMQAdminClientImpl adminClient;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        lenient().when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        lenient().when(clientPool.withProducer(any(), any(), any(), any())).thenAnswer(invocation ->
                invocation.<MqClientPool.ClientAction<DefaultMQProducer, Object>>getArgument(3).apply(sendProducer));
        lenient().when(runtimeAdminClientResolver.executeProducer(any(), any())).thenAnswer(invocation ->
                invocation.<MqClientPool.ClientAction<DefaultMQProducer, Object>>getArgument(1).apply(sendProducer));
        adminClient = new RocketMQAdminClientImpl(adminFactory, properties, topicMapper, groupMapper, auditService,
                runtimeAdminClientResolver, clientPool);
    }

    private String singleClusterTarget() {
        return "prod";
    }

    @Test
    void getTopicUsesFirstUsableQueueData() throws Exception {
        QueueData queueData = new QueueData();
        queueData.setReadQueueNums(4);
        queueData.setWriteQueueNums(6);
        TopicRouteData routeData = new TopicRouteData();
        routeData.setQueueDatas(Arrays.asList(null, queueData));
        when(adminExt.examineTopicRouteInfo("orders")).thenReturn(routeData);

        TopicVO topic = adminClient.getTopic("orders");

        assertThat(topic.getName()).isEqualTo("orders");
        assertThat(topic.getReadQueues()).isEqualTo(4);
        assertThat(topic.getWriteQueues()).isEqualTo(6);
    }

    @Test
    void getTopicReturnsNotFoundWhenAllQueueDataIsNull() throws Exception {
        TopicRouteData routeData = new TopicRouteData();
        routeData.setQueueDatas(java.util.Collections.singletonList(null));
        when(adminExt.examineTopicRouteInfo("orders")).thenReturn(routeData);

        assertThatThrownBy(() -> adminClient.getTopic("orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Topic not found: orders")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));
    }

    @Test
    void getConsumerGroupReturnsOfflineDetailForConsumerNotOnline() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE,
                        "Not found the consumer group connection"));

        ConsumerGroupVO group = adminClient.getConsumerGroup(null, "orders");

        assertThat(group.getName()).isEqualTo("orders");
        assertThat(group.getOnlineInstances()).isZero();
    }

    @Test
    void getConsumerGroupReturnsOfflineDetailWhenCode206OnlySurvivesInTheMessageTest() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQClientException(
                        "CODE: 206  DESC: the consumer group[orders] not online BROKER: 10.0.4.69:10911",
                        (Throwable) null));

        ConsumerGroupVO group = adminClient.getConsumerGroup(null, "orders");

        assertThat(group.getName()).isEqualTo("orders");
        assertThat(group.getOnlineInstances()).isZero();
    }

    @Test
    void getConsumerGroupFillsProxySideConnectionsWhenBrokerReportsOfflineTest() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQClientException(
                        "CODE: 206  DESC: the consumer group[orders] not online BROKER: 10.0.4.69:10911",
                        (Throwable) null));
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation ->
                        invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(adminExt));
        ProxyConsumerResolver resolver = org.mockito.Mockito.mock(ProxyConsumerResolver.class);
        ConsumerConnection viaProxy = new ConsumerConnection();
        java.util.HashSet<org.apache.rocketmq.remoting.protocol.body.Connection> connections = new java.util.HashSet<>();
        org.apache.rocketmq.remoting.protocol.body.Connection connection =
                new org.apache.rocketmq.remoting.protocol.body.Connection();
        connection.setClientId("client-1");
        connection.setClientAddr("10.0.3.104:50124");
        connections.add(connection);
        viaProxy.setConnectionSet(connections);
        java.util.concurrent.ConcurrentHashMap<String,
                org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData> table =
                new java.util.concurrent.ConcurrentHashMap<>();
        org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData subscription =
                new org.apache.rocketmq.remoting.protocol.heartbeat.SubscriptionData();
        subscription.setTopic("studio-normal");
        table.put("studio-normal", subscription);
        viaProxy.setSubscriptionTable(table);
        when(resolver.resolveConsumerConnection("instance-a", "orders")).thenReturn(viaProxy);
        org.springframework.test.util.ReflectionTestUtils.setField(adminClient, "proxyConsumerResolver", resolver);

        ConsumerGroupVO group = adminClient.getConsumerGroup("instance-a", "orders");

        assertThat(group.getOnlineInstances()).isEqualTo(1);
        assertThat(group.getSubscribedTopics()).containsExactly("studio-normal");
    }

    @Test
    void getConsumerGroupComputesLagAndDelayFromConsumeStatsTest() throws Exception {
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        connection.setConnectionSet(new java.util.HashSet<>());
        when(adminExt.examineConsumerConnectionInfo("orders")).thenReturn(connection);

        org.apache.rocketmq.remoting.protocol.admin.ConsumeStats stats =
                new org.apache.rocketmq.remoting.protocol.admin.ConsumeStats();
        org.apache.rocketmq.common.message.MessageQueue queue =
                new org.apache.rocketmq.common.message.MessageQueue("orders-topic", "broker-a", 0);
        org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper wrapper =
                new org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper();
        wrapper.setBrokerOffset(100);
        wrapper.setConsumerOffset(60);
        wrapper.setLastTimestamp(System.currentTimeMillis() - 5_000);
        stats.getOffsetTable().put(queue, wrapper);
        when(adminExt.examineConsumeStats("orders")).thenReturn(stats);

        ConsumerGroupVO group = adminClient.getConsumerGroup(null, "orders");

        assertThat(group.getTotalLag()).isEqualTo(40);
        assertThat(group.getDelaySeconds()).isBetween(4, 30);
    }

    @Test
    void getConsumerGroupReportsUnknownTotalLagWhenAnyQueueOffsetIsUnknownTest() throws Exception {
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        connection.setConnectionSet(new java.util.HashSet<>());
        when(adminExt.examineConsumerConnectionInfo("orders")).thenReturn(connection);

        org.apache.rocketmq.remoting.protocol.admin.ConsumeStats stats =
                new org.apache.rocketmq.remoting.protocol.admin.ConsumeStats();
        stats.getOffsetTable().put(new MessageQueue("orders-topic", "broker-a", 0), offsetWrapper(100L, 60L));
        stats.getOffsetTable().put(new MessageQueue("orders-topic", "broker-b", 1), offsetWrapper(0L, 1L));
        when(adminExt.examineConsumeStats("orders")).thenReturn(stats);

        ConsumerGroupVO group = adminClient.getConsumerGroup(null, "orders");

        assertThat(group.isConsumeStatsAvailable()).isTrue();
        assertThat(group.getTotalLag()).isEqualTo(ConsumerLagResolver.UNKNOWN);
    }

    @Test
    void getConsumerGroupFillsOnlineInstanceListFromConnectionsTest() throws Exception {
        org.apache.rocketmq.remoting.protocol.body.ConsumerConnection connection =
                new org.apache.rocketmq.remoting.protocol.body.ConsumerConnection();
        java.util.HashSet<org.apache.rocketmq.remoting.protocol.body.Connection> connections =
                new java.util.HashSet<>();
        org.apache.rocketmq.remoting.protocol.body.Connection conn =
                new org.apache.rocketmq.remoting.protocol.body.Connection();
        conn.setClientId("client-1");
        conn.setClientAddr("10.0.3.104:50124");
        connections.add(conn);
        connection.setConnectionSet(connections);
        when(adminExt.examineConsumerConnectionInfo("orders")).thenReturn(connection);

        ConsumerGroupVO group = adminClient.getConsumerGroup(null, "orders");

        assertThat(group.getOnlineInstances()).isEqualTo(1);
        assertThat(group.getInstances())
                .extracting(ConsumerInstanceVO::getClientId, ConsumerInstanceVO::getAddress)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("client-1", "10.0.3.104:50124"));
    }

    @Test
    void getConsumerGroupUsesSelectedInstanceAdmin() throws Exception {
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        ConsumerGroupVO group = adminClient.getConsumerGroup("instance-a", "cg-orders");

        assertThat(group.getName()).isEqualTo("cg-orders");
        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(selectedAdmin).examineConsumerConnectionInfo("cg-orders");
        verify(adminExt, never()).examineConsumerConnectionInfo(anyString());
    }

    @Test
    void resetOffsetShouldUseSelectedInstanceRuntimeClient() {
        adminClient.resetOffset("instance-a", "cg-orders", 1784246400000L, "orders");

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(auditService).record(eq("RESET_OFFSET"), eq("GROUP"), eq("cg-orders"),
                eq(null), eq("instanceId=instance-a, topic=orders, timestamp=1784246400000"), eq("SUCCESS"));
    }

    @Test
    void resetOffsetShouldApplyPreviewedTargetsAndSupportOfflineGroups() throws Exception {
        // resetOffsetNew is the API the classic console uses: it forces the broker to
        // apply the searched offset even when the target is ahead of the current offset
        // (the preview explicitly offers "skip unconsumed messages"), and it falls back
        // to direct offset writes when the group is offline instead of failing with
        // CONSUMER_NOT_ONLINE.
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });
        long timestamp = 1784246400000L;

        adminClient.resetOffset("instance-a", "cg-orders", timestamp, "orders");

        verify(selectedAdmin).resetOffsetNew("cg-orders", "orders", timestamp);
        verify(selectedAdmin, never()).resetOffsetByTimestamp(
                anyString(), anyString(), anyString(), anyLong(), anyBoolean());
    }

    @Test
    void resetOffsetWithoutInstanceShouldUseForceAndOfflineFallbackSemantics() throws Exception {
        long timestamp = 1784246400000L;

        adminClient.resetOffset(null, "cg-orders", timestamp, "orders");

        verify(adminExt).resetOffsetNew("cg-orders", "orders", timestamp);
        verify(adminExt, never()).resetOffsetByTimestamp(
                anyString(), anyString(), anyString(), anyLong(), anyBoolean());
    }

    @Test
    void previewResetOffsetShouldComputeQueueImpactWithoutMutatingBrokerOffsets() throws Exception {
        long timestamp = 1784246400000L;
        ConsumeStats stats = new ConsumeStats();
        MessageQueue rewindQueue = new MessageQueue("orders", "broker-a", 0);
        MessageQueue fastForwardQueue = new MessageQueue("orders", "broker-b", 1);
        MessageQueue skippedQueue = new MessageQueue("payments", "broker-a", 0);
        stats.getOffsetTable().put(rewindQueue, offsetWrapper(120L, 90L));
        stats.getOffsetTable().put(fastForwardQueue, offsetWrapper(200L, 150L));
        stats.getOffsetTable().put(skippedQueue, offsetWrapper(50L, 40L));
        when(adminExt.examineConsumeStats("cg-orders")).thenReturn(stats);
        ClusterInfo clusterInfo = clusterInfoWithTwoMasters();
        clusterInfo.getBrokerAddrTable().get("broker-1").setBrokerName("broker-a");
        clusterInfo.getBrokerAddrTable().get("broker-2").setBrokerName("broker-b");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(adminExt.minOffset(rewindQueue)).thenReturn(0L);
        when(adminExt.maxOffset(rewindQueue)).thenReturn(200L);
        when(adminExt.searchOffset("10.0.0.1:10911", "orders", 0, timestamp, 3_000L)).thenReturn(80L);
        when(adminExt.minOffset(fastForwardQueue)).thenReturn(0L);
        when(adminExt.maxOffset(fastForwardQueue)).thenReturn(220L);
        when(adminExt.searchOffset("10.0.0.2:10911", "orders", 1, timestamp, 3_000L)).thenReturn(170L);

        ResetConsumerOffsetPreviewVO preview = adminClient.previewResetOffset(
                null, "cg-orders", timestamp, "orders");

        assertThat(preview.isComplete()).isTrue();
        assertThat(preview.isAllowReset()).isTrue();
        assertThat(preview.getQueueCount()).isEqualTo(2);
        assertThat(preview.getCurrentTotalLag()).isEqualTo(80L);
        assertThat(preview.getProjectedTotalLag()).isEqualTo(70L);
        assertThat(preview.getRewindQueueCount()).isEqualTo(1);
        assertThat(preview.getFastForwardQueueCount()).isEqualTo(1);
        assertThat(preview.getTotalOffsetDelta()).isEqualTo(10L);
        assertThat(preview.getWarnings())
                .contains("1 queue(s) will move forward and may skip unconsumed messages",
                        "1 queue(s) will move backward and may replay consumed messages");
        assertThat(preview.getQueues())
                .extracting(ResetConsumerOffsetQueuePreviewVO::getBroker,
                        ResetConsumerOffsetQueuePreviewVO::getQueueId,
                        ResetConsumerOffsetQueuePreviewVO::getTargetOffset,
                        ResetConsumerOffsetQueuePreviewVO::getOffsetDelta,
                        ResetConsumerOffsetQueuePreviewVO::getProjectedLag)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("broker-a", 0, 80L, -10L, 40L),
                        org.assertj.core.groups.Tuple.tuple("broker-b", 1, 170L, 20L, 30L));
        verify(adminExt, never()).resetOffsetByTimestamp(anyString(), anyString(), anyString(),
                anyLong(), anyBoolean());
    }

    @Test
    void previewResetOffsetShouldMarkFailedQueuesIncomplete() throws Exception {
        long timestamp = 1784246400000L;
        ConsumeStats stats = new ConsumeStats();
        MessageQueue queue = new MessageQueue("orders", "broker-a", 0);
        stats.getOffsetTable().put(queue, offsetWrapper(120L, 90L));
        when(adminExt.examineConsumeStats("cg-orders")).thenReturn(stats);
        when(adminExt.minOffset(queue)).thenThrow(new IllegalStateException("offset unavailable"));

        ResetConsumerOffsetPreviewVO preview = adminClient.previewResetOffset(
                null, "cg-orders", timestamp, "orders");

        assertThat(preview.isComplete()).isFalse();
        assertThat(preview.isAllowReset()).isFalse();
        assertThat(preview.getWarningCount()).isEqualTo(1);
        assertThat(preview.getWarnings())
                .containsExactly("Failed to preview 1 queue(s); retry before applying the reset");
        assertThat(preview.getQueues()).hasSize(1);
        ResetConsumerOffsetQueuePreviewVO row = preview.getQueues().get(0);
        assertThat(row.getRiskLevel()).isEqualTo("ERROR");
        assertThat(row.getTargetOffset()).isEqualTo(90L);
        assertThat(row.getProjectedLag()).isEqualTo(30L);
        assertThat(row.getMessage()).contains("offset unavailable");
        verify(adminExt, never()).resetOffsetByTimestamp(anyString(), anyString(), anyString(),
                anyLong(), anyBoolean());
    }

    @Test
    void previewResetOffsetShouldReturnEmptyPreviewWhenTopicHasNoOffsets() throws Exception {
        ConsumeStats stats = new ConsumeStats();
        stats.getOffsetTable().put(new MessageQueue("payments", "broker-a", 0), offsetWrapper(120L, 90L));
        when(adminExt.examineConsumeStats("cg-orders")).thenReturn(stats);

        ResetConsumerOffsetPreviewVO preview = adminClient.previewResetOffset(
                null, "cg-orders", 1784246400000L, "orders");

        assertThat(preview.isComplete()).isFalse();
        assertThat(preview.isAllowReset()).isFalse();
        assertThat(preview.getQueueCount()).isZero();
        assertThat(preview.getWarnings()).containsExactly("No consume offset data found for topic orders");
        verify(adminExt, never()).resetOffsetByTimestamp(anyString(), anyString(), anyString(),
                anyLong(), anyBoolean());
    }

    @Test
    void previewResetOffsetShouldRejectBlankTopicBeforeResolvingAdmin() {
        assertThatThrownBy(() -> adminClient.previewResetOffset("instance-a", "cg-orders", 1784246400000L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic is required for offset reset preview")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));

        verifyNoInteractions(runtimeAdminClientResolver);
        verify(adminFactory, never()).execute(anyString(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void resetOffsetShouldRejectBlankTopicBeforeResolvingAdmin() {
        assertThatThrownBy(() -> adminClient.resetOffset("instance-a", "cg-orders", 1784246400000L, " "))
                .isInstanceOf(BusinessException.class)
                .hasMessage("topic is required for offset reset")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));

        verifyNoInteractions(runtimeAdminClientResolver);
        verify(adminFactory, never()).execute(anyString(), any(), any());
        verifyNoInteractions(auditService);
    }

    @Test
    void resetOffsetShouldPreserveStructuredResolverFailure() {
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("missing-instance"), any()))
                .thenThrow(new BusinessException(404, "Instance not found: missing-instance"));

        assertThatThrownBy(() -> adminClient.resetOffset(
                "missing-instance", "cg-orders", 1784246400000L, "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Instance not found: missing-instance")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(404));
        verify(auditService).record(eq("RESET_OFFSET"), eq("GROUP"), eq("cg-orders"),
                eq(null), eq("Instance not found: missing-instance"), eq("FAILED"));
    }

    @Test
    void resetOffsetShouldWrapUnexpectedAdminFailure() {
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenThrow(new IllegalStateException("broker unavailable"));

        assertThatThrownBy(() -> adminClient.resetOffset(
                "instance-a", "cg-orders", 1784246400000L, "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Failed to reset offset: broker unavailable")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(500));
        verify(auditService).record(eq("RESET_OFFSET"), eq("GROUP"), eq("cg-orders"),
                eq(null), eq("broker unavailable"), eq("FAILED"));
    }

    @Test
    void getConsumerGroupSurfacesAdminTimeout() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new RemotingTimeoutException("broker-0", 3_000));

        assertThatThrownBy(() -> adminClient.getConsumerGroup(null, "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to get consumer group");
    }

    @Test
    void getConsumerGroupSurfacesBrokerFailures() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQBrokerException(16, "ACL denied"));

        assertThatThrownBy(() -> adminClient.getConsumerGroup(null, "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL denied");
    }

    @Test
    void createTopicScopesLookupToClusterAndLegacyInstanceScope() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("cluster-1", new HashSet<>(List.of("broker-1")));
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        brokerAddrTable.put("broker-1", brokerData);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(topicMapper.selectOne(any())).thenReturn(null);
        doNothing().when(adminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));

        TopicVO topic = new TopicVO();
        topic.setName("topicA");
        adminClient.createTopic(topic);

        ArgumentCaptor<LambdaQueryWrapper<RmqTopic>> captor =
                ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(topicMapper, times(2)).selectOne(captor.capture());
        for (LambdaQueryWrapper<RmqTopic> wrapper : captor.getAllValues()) {
            assertThat(wrapper.getSqlSegment()).contains("cluster_id", "instance_id");
        }
    }

    @Test
    void createTopicShouldOnlyWriteTargetClusterBrokersInMultiClusterTopology() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        ClusterInfo clusterInfo = clusterInfoWithTwoClusters();
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(topicMapper.selectOne(any())).thenReturn(null);
        doNothing().when(adminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));

        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("cluster-1");

        adminClient.createTopic(topic);

        verify(adminExt).createAndUpdateTopicConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), any(TopicConfig.class));
        verify(adminExt, never()).createAndUpdateTopicConfig(
                org.mockito.ArgumentMatchers.eq("10.0.1.1:10911"), any(TopicConfig.class));
    }

    @Test
    void createTopicFallsBackToAllBrokersWhenClusterTopologyIsMissing() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("missing-broker", null);
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        brokerAddrTable.put("broker-1", brokerData);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(topicMapper.selectOne(any())).thenReturn(null);

        TopicVO topic = new TopicVO();
        topic.setName("orders");

        adminClient.createTopic(topic);

        verify(adminExt).createAndUpdateTopicConfig(eq("10.0.0.1:10911"), any(TopicConfig.class));
    }

    @Test
    void configuredGroupCreationKeepsDefaultClusterSelectionAndEmptyMetadataScope() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(runtimeAdminClientResolver.configuredClusterName("configured-target")).thenReturn("configured-target");
        when(runtimeAdminClientResolver.execute(eq("configured-target"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders-consumers");
        group.setInstanceId("configured-target");
        adminClient.createConsumerGroup(group);
        ArgumentCaptor<RmqGroup> saved = ArgumentCaptor.forClass(RmqGroup.class);
        verify(groupMapper).insert(saved.capture());
        assertThat(saved.getValue().getInstanceId()).isEmpty();
        assertThat(saved.getValue().getClusterId()).isEqualTo("cluster-1");
        verifyNoInteractions(adminExt, adminFactory);
    }

    @Test
    void configuredTopicCreationKeepsDefaultClusterSelectionAndEmptyMetadataScope() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(runtimeAdminClientResolver.configuredClusterName("configured-target")).thenReturn("configured-target");
        when(runtimeAdminClientResolver.execute(eq("configured-target"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        adminClient.createTopic("configured-target", topic);
        ArgumentCaptor<RmqTopic> saved = ArgumentCaptor.forClass(RmqTopic.class);
        verify(topicMapper).insert(saved.capture());
        assertThat(saved.getValue().getInstanceId()).isEmpty();
        assertThat(saved.getValue().getClusterId()).isEqualTo("cluster-1");
        verify(selectedAdmin).createAndUpdateTopicConfig(eq("10.0.0.1:10911"), any(TopicConfig.class));
        verifyNoInteractions(adminExt, adminFactory);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void topicWritesBindExplicitInstanceToAdminAndMetadataScope(boolean create) throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(runtimeAdminClientResolver.execute(eq("selected-instance"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));
        RmqTopic existing = new RmqTopic();
        existing.setInstanceId("selected-instance");
        when(topicMapper.selectOne(any())).thenReturn(create ? null : existing);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setInstanceId("other-instance");

        TopicVO result = create ? adminClient.createTopic("selected-instance", topic)
                : adminClient.updateTopic("selected-instance", topic);

        assertThat(result.getInstanceId()).isEqualTo("selected-instance");
        verify(runtimeAdminClientResolver).execute(eq("selected-instance"), any());
        verify(selectedAdmin).createAndUpdateTopicConfig(eq("10.0.0.1:10911"), any(TopicConfig.class));
        verifyNoInteractions(adminExt, adminFactory);
        ArgumentCaptor<LambdaQueryWrapper<RmqTopic>> queries = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(topicMapper, times(create ? 2 : 1)).selectOne(queries.capture());
        for (LambdaQueryWrapper<RmqTopic> query : queries.getAllValues()) {
            assertThat(query.getSqlSegment()).contains("instance_id", "cluster_id", "name");
            assertThat(query.getParamNameValuePairs().values())
                    .contains("selected-instance", "cluster-1", "orders").doesNotContain("other-instance");
        }
        if (create) {
            ArgumentCaptor<RmqTopic> saved = ArgumentCaptor.forClass(RmqTopic.class);
            verify(topicMapper).insert(saved.capture());
            assertThat(saved.getValue().getInstanceId()).isEqualTo("selected-instance");
        } else {
            verify(topicMapper).updateById(existing);
            assertThat(existing.getInstanceId()).isEqualTo("selected-instance");
        }
    }

    @Test
    void topicWritesUseSelectedInstanceAdmin() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(topicMapper.selectOne(any())).thenReturn(null);
        doNothing().when(selectedAdmin).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("open-source-local"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));

        TopicVO topic = new TopicVO();
        topic.setName("topicA");
        topic.setInstanceId("open-source-local");

        adminClient.createTopic(topic);
        adminClient.updateTopic(topic);

        verify(runtimeAdminClientResolver, times(2)).execute(org.mockito.ArgumentMatchers.eq("open-source-local"), any());
        verify(selectedAdmin, times(2)).createAndUpdateTopicConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), any(TopicConfig.class));
        verify(adminExt, never()).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
    }

    @Test
    void updateTopicPersistsTypeAndRemark() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        RmqTopic existing = new RmqTopic();
        existing.setTopicType(TopicType.NORMAL.name());
        existing.setRemark("old remark");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(topicMapper.selectOne(any())).thenReturn(existing);

        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.FIFO);
        topic.setRemark("updated remark");

        adminClient.updateTopic(topic);

        assertThat(existing.getTopicType()).isEqualTo(TopicType.FIFO.name());
        assertThat(existing.getRemark()).isEqualTo("updated remark");
        verify(topicMapper).updateById(existing);
    }

    @Test
    void topicWritesSendMessageTypeAttributeToBroker() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(topicMapper.selectOne(any())).thenReturn(null);
        doNothing().when(adminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));

        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setType(TopicType.FIFO);

        adminClient.createTopic(topic);
        adminClient.updateTopic(topic);

        ArgumentCaptor<TopicConfig> captor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(adminExt, times(2)).createAndUpdateTopicConfig(anyString(), captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(config ->
                assertThat(config.getAttributes()).containsEntry("+message.type", TopicType.FIFO.name()));
    }

    @Test
    void updateTopicWithoutTypePreservesExistingType() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        RmqTopic existing = new RmqTopic();
        existing.setTopicType(TopicType.TRANSACTION.name());
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(topicMapper.selectOne(any())).thenReturn(existing);

        TopicVO topic = new TopicVO();
        topic.setName("orders");

        adminClient.updateTopic(topic);

        ArgumentCaptor<TopicConfig> captor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(adminExt).createAndUpdateTopicConfig(anyString(), captor.capture());
        assertThat(captor.getValue().getAttributes()).doesNotContainKey("+message.type");
        assertThat(existing.getTopicType()).isEqualTo(TopicType.TRANSACTION.name());
    }

    @Test
    void updateTopicPreservesQueueCountsWhenNotSpecified() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        RmqTopic existing = new RmqTopic();
        existing.setWriteQueueNums(16);
        existing.setReadQueueNums(16);
        // RocketMQ perm int: 6 = RW, 4 = RO, 2 = WO.
        existing.setPerm(6);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(topicMapper.selectOne(any())).thenReturn(existing);
        doNothing().when(adminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));

        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setPerm(TopicPerm.RO);

        adminClient.updateTopic(topic);

        // Partial update (perm only) must not reset queues to the default of 8.
        ArgumentCaptor<TopicConfig> topicConfigCaptor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(adminExt).createAndUpdateTopicConfig(anyString(), topicConfigCaptor.capture());
        assertThat(topicConfigCaptor.getValue().getWriteQueueNums()).isEqualTo(16);
        assertThat(topicConfigCaptor.getValue().getReadQueueNums()).isEqualTo(16);
        assertThat(existing.getWriteQueueNums()).isEqualTo(16);
        assertThat(existing.getReadQueueNums()).isEqualTo(16);
    }

    @Test
    void updateTopicAppliesExplicitQueueCounts() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        RmqTopic existing = new RmqTopic();
        existing.setWriteQueueNums(8);
        existing.setReadQueueNums(8);
        existing.setPerm(6);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        when(topicMapper.selectOne(any())).thenReturn(existing);
        doNothing().when(adminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));

        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setWriteQueues(16);
        topic.setReadQueues(12);

        TopicVO updated = adminClient.updateTopic(topic);

        ArgumentCaptor<TopicConfig> topicConfigCaptor = ArgumentCaptor.forClass(TopicConfig.class);
        verify(adminExt).createAndUpdateTopicConfig(anyString(), topicConfigCaptor.capture());
        assertThat(topicConfigCaptor.getValue().getWriteQueueNums()).isEqualTo(16);
        assertThat(topicConfigCaptor.getValue().getReadQueueNums()).isEqualTo(12);
        assertThat(existing.getWriteQueueNums()).isEqualTo(16);
        assertThat(existing.getReadQueueNums()).isEqualTo(12);
        assertThat(updated.getWriteQueues()).isEqualTo(16);
        assertThat(updated.getReadQueues()).isEqualTo(12);
    }

    @Test
    void topicDeleteUsesSelectedInstanceAndScopesMetadataToClusterAndInstance() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        doNothing().when(selectedAdmin).deleteTopicInBroker(any(), anyString());
        doNothing().when(selectedAdmin).deleteTopicInNameServer(any(), anyString(), anyString());
        when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("10.0.0.2:9876");
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));

        adminClient.deleteTopic("instance-a", "orders");

        ArgumentCaptor<LambdaQueryWrapper<RmqTopic>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(topicMapper).delete(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("cluster_id", "instance_id", "name");
        verify(selectedAdmin).deleteTopicInBroker(Set.of("10.0.0.1:10911"), "orders");
        verify(selectedAdmin).deleteTopicInNameServer(Set.of("10.0.0.2:9876"), "cluster-1", "orders");
    }

    @Test
    void deleteTopicScopesBrokerDeletionToSelectedClusterOnly() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        when(runtimeAdminClientResolver.execute(eq("prod"), any())).thenAnswer(call ->
                call.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(adminExt));
        when(runtimeAdminClientResolver.resolveEndpoint("prod")).thenReturn("10.0.0.1:9876");
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        doNothing().when(adminExt).deleteTopicInBroker(any(), anyString());
        doNothing().when(adminExt).deleteTopicInNameServer(any(), anyString(), anyString());

        adminClient.deleteTopic(singleClusterTarget(), "orders");

        verify(adminExt).deleteTopicInBroker(Set.of("10.0.0.1:10911"), "orders");
        verify(adminExt).deleteTopicInNameServer(Set.of("10.0.0.1:9876"), "cluster-1", "orders");
        verify(topicMapper).delete(any());
    }

    @Test
    void createConsumerGroupUsesSelectedInstanceAdmin() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterAddrTable(new HashMap<>(Map.of("cluster-1", new HashSet<>(List.of("broker-1")))));
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        clusterInfo.setBrokerAddrTable(new HashMap<>(Map.of("broker-1", brokerData)));
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(groupMapper.selectOne(any())).thenReturn(null);
        doNothing().when(selectedAdmin).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("open-source-local"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setInstanceId("open-source-local");

        adminClient.createConsumerGroup(group);

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("open-source-local"), any());
        verify(selectedAdmin).createAndUpdateSubscriptionGroupConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), any());
        verify(adminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        ArgumentCaptor<LambdaQueryWrapper<RmqGroup>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(groupMapper).selectOne(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("cluster_id", "instance_id", "name");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    void updateConsumerGroupPreservesEachBrokersConfiguration(int retryMaxTimes) throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        when(runtimeAdminClientResolver.execute(eq("instance-a"), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(selectedAdmin));

        Map<String, SubscriptionGroupConfig> expectedConfigs = new HashMap<>();
        for (int i = 1; i <= 2; i++) {
            String address = "10.0.0." + i + ":10911";
            SubscriptionGroupConfig config = new SubscriptionGroupConfig();
            config.setGroupName("cg-orders");
            config.setConsumeEnable(false);
            config.setConsumeBroadcastEnable(false);
            config.setConsumeMessageOrderly(i == 1);
            config.setConsumeFromMinEnable(false);
            config.setRetryQueueNums(i + 2);
            config.setRetryMaxTimes(16 + i);
            config.setBrokerId(i);
            config.setWhichBrokerWhenConsumeSlowly(i + 1);
            config.setNotifyConsumerIdsChangedEnable(false);
            config.setConsumeTimeoutMinute(30 + i);
            config.setAttributes(Map.of("custom", "broker-" + i));
            SubscriptionGroupConfig expected = new SubscriptionGroupConfig();
            org.springframework.beans.BeanUtils.copyProperties(config, expected);
            expected.setRetryMaxTimes(retryMaxTimes);
            expectedConfigs.put(address, expected);
            when(selectedAdmin.examineSubscriptionGroupConfig(address, "cg-orders")).thenReturn(config);
        }
        RmqGroup entity = new RmqGroup();
        entity.setId(7L);
        when(groupMapper.selectOne(any())).thenReturn(entity);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setInstanceId("instance-a");
        group.setRetryMaxTimes(retryMaxTimes);

        ConsumerGroupVO result = adminClient.updateConsumerGroup(group);

        ArgumentCaptor<String> addresses = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<SubscriptionGroupConfig> configs = ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(selectedAdmin, times(2)).createAndUpdateSubscriptionGroupConfig(addresses.capture(), configs.capture());
        assertThat(addresses.getAllValues()).containsExactlyInAnyOrderElementsOf(expectedConfigs.keySet());
        for (int i = 0; i < addresses.getAllValues().size(); i++) {
            assertThat(configs.getAllValues().get(i)).usingRecursiveComparison()
                    .isEqualTo(expectedConfigs.get(addresses.getAllValues().get(i)));
        }
        verifyNoInteractions(adminExt);
        verify(groupMapper).updateById(entity);
        verify(groupMapper, never()).insert(any(RmqGroup.class));
        assertThat(entity.getMaxRetry()).isEqualTo(retryMaxTimes);
        assertThat(result.getRetryMaxTimes()).isEqualTo(retryMaxTimes);
        assertThat(result.getId()).isEqualTo(7L);
        verify(auditService).record(eq("UPDATE_GROUP"), eq("GROUP"), eq("cg-orders"), isNull(),
                org.mockito.ArgumentMatchers.contains("retryMaxTimes=" + retryMaxTimes), eq("SUCCESS"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "unavailable"})
    void updateConsumerGroupReadsAllConfigurationsBeforeWriting(String failure) throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("cg-orders");
        var reads = when(adminExt.examineSubscriptionGroupConfig(anyString(), eq("cg-orders"))).thenReturn(config);
        if ("missing".equals(failure)) {
            reads.thenReturn(null);
        } else {
            reads.thenThrow(new IllegalStateException("broker unavailable"));
        }
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setRetryMaxTimes(8);

        assertThatThrownBy(() -> adminClient.updateConsumerGroup(group))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("missing".equals(failure) ? "Consumer group not found" : "broker unavailable");

        verify(adminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verifyNoInteractions(groupMapper);
        verify(auditService).record(eq("UPDATE_GROUP"), eq("GROUP"), eq("cg-orders"), isNull(),
                anyString(), eq("FAILED"));
        verify(auditService, never()).record(anyString(), anyString(), anyString(), any(), anyString(), eq("SUCCESS"));
    }

    @Test
    void updateConsumerGroupDoesNotPersistMetadataWhenBrokerWriteFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoMasters());
        when(adminExt.examineSubscriptionGroupConfig(anyString(), eq("cg-orders"))).thenAnswer(invocation -> {
            SubscriptionGroupConfig config = new SubscriptionGroupConfig();
            config.setGroupName("cg-orders");
            return config;
        });
        doNothing().doThrow(new IllegalStateException("broker unavailable")).when(adminExt)
                .createAndUpdateSubscriptionGroupConfig(anyString(), any());
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setRetryMaxTimes(8);

        assertThatThrownBy(() -> adminClient.updateConsumerGroup(group))
                .isInstanceOf(BusinessException.class).hasMessageContaining("broker unavailable");

        verify(adminExt, times(2)).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verifyNoInteractions(groupMapper);
        verify(auditService).record(eq("UPDATE_GROUP"), eq("GROUP"), eq("cg-orders"), isNull(),
                eq("updated 1/2 brokers before failure: broker unavailable"), eq("FAILED"));
        verify(auditService, never()).record(anyString(), anyString(), anyString(), any(), anyString(), eq("SUCCESS"));
    }

    @Test
    void updateConsumerGroupFailsWhenNoBrokerIsAvailable() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(new ClusterInfo());
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setRetryMaxTimes(8);

        assertThatThrownBy(() -> adminClient.updateConsumerGroup(group))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No broker available to update consumer group");

        verify(adminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verifyNoInteractions(groupMapper);
    }

    @Test
    void updateConsumerGroupSettingsPreservesBrokerConfiguration() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterAddrTable(new HashMap<>(Map.of("cluster-1", new HashSet<>(List.of("broker-1")))));
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        clusterInfo.setBrokerAddrTable(new HashMap<>(Map.of("broker-1", brokerData)));
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("cg-orders");
        config.setConsumeEnable(false);
        config.setRetryQueueNums(1);
        config.setRetryMaxTimes(16);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(selectedAdmin.examineSubscriptionGroupConfig("10.0.0.1:10911", "cg-orders")).thenReturn(config);
        when(groupMapper.selectOne(any())).thenReturn(null);
        doNothing().when(selectedAdmin).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        ConsumerGroupSettingsVO settings = adminClient.updateConsumerGroupSettings("instance-a", "cg-orders",
                new ConsumerGroupSettingsCommand(2, 8, null, null, null));

        assertThat(settings.getRetryQueueNums()).isEqualTo(2);
        assertThat(settings.getRetryMaxTimes()).isEqualTo(8);
        ArgumentCaptor<SubscriptionGroupConfig> captor = ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(selectedAdmin).createAndUpdateSubscriptionGroupConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), captor.capture());
        assertThat(captor.getValue().isConsumeEnable()).isFalse();
        assertThat(captor.getValue().getRetryQueueNums()).isEqualTo(2);
        assertThat(captor.getValue().getRetryMaxTimes()).isEqualTo(8);
    }

    @Test
    void updateConsumerGroupSettingsAppliesConsumptionSwitches() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterAddrTable(new HashMap<>(Map.of("cluster-1", new HashSet<>(List.of("broker-1")))));
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        clusterInfo.setBrokerAddrTable(new HashMap<>(Map.of("broker-1", brokerData)));
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("cg-orders");
        config.setConsumeEnable(true);
        config.setConsumeMessageOrderly(false);
        config.setConsumeBroadcastEnable(false);
        config.setRetryQueueNums(1);
        config.setRetryMaxTimes(16);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(selectedAdmin.examineSubscriptionGroupConfig("10.0.0.1:10911", "cg-orders")).thenReturn(config);
        when(groupMapper.selectOne(any())).thenReturn(null);
        doNothing().when(selectedAdmin).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        ConsumerGroupSettingsVO settings = adminClient.updateConsumerGroupSettings("instance-a", "cg-orders",
                new ConsumerGroupSettingsCommand(2, 8, false, true, null));

        assertThat(settings.isConsumeEnable()).isFalse();
        assertThat(settings.isConsumeMessageOrderly()).isTrue();
        assertThat(settings.isConsumeBroadcastEnable()).isFalse();
        ArgumentCaptor<SubscriptionGroupConfig> captor = ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(selectedAdmin).createAndUpdateSubscriptionGroupConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), captor.capture());
        assertThat(captor.getValue().isConsumeEnable()).isFalse();
        assertThat(captor.getValue().isConsumeMessageOrderly()).isTrue();
        assertThat(captor.getValue().isConsumeBroadcastEnable()).isFalse();
        assertThat(captor.getValue().getRetryQueueNums()).isEqualTo(2);
        assertThat(captor.getValue().getRetryMaxTimes()).isEqualTo(8);
    }

    @Test
    void getConsumerGroupSettingsReturnsConsumptionSwitches() throws Exception {
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterAddrTable(new HashMap<>(Map.of("cluster-1", new HashSet<>(List.of("broker-1")))));
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        clusterInfo.setBrokerAddrTable(new HashMap<>(Map.of("broker-1", brokerData)));
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("cg-orders");
        config.setConsumeEnable(false);
        config.setConsumeMessageOrderly(true);
        config.setConsumeBroadcastEnable(false);
        config.setRetryQueueNums(1);
        config.setRetryMaxTimes(16);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(selectedAdmin.examineSubscriptionGroupConfig("10.0.0.1:10911", "cg-orders")).thenReturn(config);
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        ConsumerGroupSettingsVO settings = adminClient.getConsumerGroupSettings("instance-a", "cg-orders");

        assertThat(settings.isConsumeEnable()).isFalse();
        assertThat(settings.isConsumeMessageOrderly()).isTrue();
        assertThat(settings.isConsumeBroadcastEnable()).isFalse();
        assertThat(settings.getRetryQueueNums()).isEqualTo(1);
        assertThat(settings.getRetryMaxTimes()).isEqualTo(16);
    }

    @Test
    void deleteConsumerGroupUsesSelectedInstanceAdmin() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setClusterAddrTable(new HashMap<>(Map.of("cluster-1", Set.of("broker-1"))));
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        clusterInfo.setBrokerAddrTable(new HashMap<>(Map.of("broker-1", brokerData)));
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        doNothing().when(selectedAdmin).deleteSubscriptionGroup(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        adminClient.deleteConsumerGroup("instance-a", "cg-orders");

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(selectedAdmin).deleteSubscriptionGroup("10.0.0.1:10911", "cg-orders", true);
        verify(adminExt, never()).deleteSubscriptionGroup(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        ArgumentCaptor<LambdaQueryWrapper<RmqGroup>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(groupMapper).delete(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("cluster_id", "instance_id", "name");
    }

    @Test
    void deleteConsumerGroupScopesBrokerDeletionToSelectedClusterOnly() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        when(runtimeAdminClientResolver.execute(eq("prod"), any())).thenAnswer(call ->
                call.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(adminExt));
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMaster());
        doNothing().when(adminExt).deleteSubscriptionGroup(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());

        adminClient.deleteConsumerGroup(singleClusterTarget(), "cg-orders");

        verify(adminExt).deleteSubscriptionGroup("10.0.0.1:10911", "cg-orders", true);
        verify(adminExt, never()).deleteSubscriptionGroup("10.0.1.1:10911", "cg-orders", true);
        verify(groupMapper).delete(any());
    }

    @Test
    void createTopicSucceedsWhenAuditRecordingFails() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        ClusterInfo clusterInfo = clusterInfoWithMaster();
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfo);
        when(topicMapper.selectOne(any())).thenReturn(null);
        when(topicMapper.insert(any(RmqTopic.class))).thenAnswer(invocation -> {
            RmqTopic inserted = invocation.getArgument(0);
            inserted.setId(1L);
            return 1;
        });
        doNothing().when(adminExt).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        doThrow(new RuntimeException("audit db down")).when(auditService)
                .record(any(), any(), any(), any(), any(), any());

        TopicVO topic = new TopicVO();
        topic.setName("topicA");

        assertThat(adminClient.createTopic(topic).getId()).isEqualTo(1L);
        verify(auditService).record(eq("CREATE_TOPIC"), eq("TOPIC"), eq("topicA"),
                eq(null), eq("queues=8/8"), eq("SUCCESS"));
    }

    @Test
    void createTopicPreservesRemoteFailureWhenAuditRecordingFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo())
                .thenThrow(new IllegalStateException("broker unavailable"));
        doThrow(new RuntimeException("audit db down")).when(auditService)
                .record(any(), any(), any(), any(), any(), any());

        TopicVO topic = new TopicVO();
        topic.setName("topicA");

        assertThatThrownBy(() -> adminClient.createTopic(topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to create topic: broker unavailable");
    }

    private ClusterInfo clusterInfoWithMaster() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("cluster-1", new HashSet<>(List.of("broker-1")));
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData brokerData = new BrokerData();
        brokerData.setBrokerName("broker-1");
        brokerData.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        brokerAddrTable.put("broker-1", brokerData);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    private ClusterInfo clusterInfoWithTwoMasters() {
        ClusterInfo clusterInfo = clusterInfoWithMaster();
        clusterInfo.getClusterAddrTable().get("cluster-1").add("broker-2");
        BrokerData broker = new BrokerData();
        broker.setBrokerName("broker-2");
        broker.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.2:10911")));
        clusterInfo.getBrokerAddrTable().put("broker-2", broker);
        return clusterInfo;
    }

    private ClusterInfo clusterInfoWithTwoClusters() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("cluster-1", new HashSet<>(List.of("broker-1")));
        clusterAddrTable.put("cluster-2", new HashSet<>(List.of("broker-2")));
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData firstBroker = new BrokerData();
        firstBroker.setBrokerName("broker-1");
        firstBroker.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        BrokerData secondBroker = new BrokerData();
        secondBroker.setBrokerName("broker-2");
        secondBroker.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.1.1:10911")));
        brokerAddrTable.put("broker-1", firstBroker);
        brokerAddrTable.put("broker-2", secondBroker);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    private OffsetWrapper offsetWrapper(long brokerOffset, long consumerOffset) {
        OffsetWrapper wrapper = new OffsetWrapper();
        wrapper.setBrokerOffset(brokerOffset);
        wrapper.setConsumerOffset(consumerOffset);
        return wrapper;
    }

    @Test
    void sendMessageShouldNotFailWhenAuditRecordingFails() throws Exception {
        doThrow(new RuntimeException("audit db down")).when(auditService)
                .record(any(), any(), any(), any(), any(), any());
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("msg-1");
        sendResult.setOffsetMsgId("offset-1");
        when(sendProducer.send(any(Message.class))).thenReturn(sendResult);

        SendMessageDTO request = new SendMessageDTO();
        request.setTopic("TopicA");
        request.setBody("hello");
        SendMessageVO result = adminClient.sendMessage(request);
        // The message was already delivered; an audit failure must not turn this into an error.
        assertThat(result.getMsgId()).isEqualTo("msg-1");
    }

    @Test
    void sendMessageShouldRejectOversizedUtf8BodyBeforeResolvingEndpointOrCreatingProducer() {
        SendMessageDTO request = new SendMessageDTO();
        request.setInstanceId("instance-a");
        request.setTopic("TopicA");
        request.setBody("\u754c".repeat((4 * 1024 * 1024 / 3) + 1));

        assertThatThrownBy(() -> adminClient.sendMessage(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("exceeds the maximum")
                .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));

        verifyNoInteractions(runtimeAdminClientResolver);
        verifyNoInteractions(clientPool);
        verify(properties, never()).getNamesrvAddr();
        verify(auditService).record(eq("SEND_MESSAGE"), eq("MESSAGE"), eq("TopicA"),
                eq(null), eq("Message body size 4194306 exceeds the maximum of 4194304 bytes"), eq("FAILED"));
    }

    @Test
    void sendMessageShouldAllowBodyAtMaximumSize() throws Exception {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("msg-1");
        sendResult.setOffsetMsgId("offset-1");
        when(sendProducer.send(any(Message.class))).thenReturn(sendResult);

        SendMessageDTO request = new SendMessageDTO();
        request.setTopic("TopicA");
        request.setBody("x".repeat(4 * 1024 * 1024));

        SendMessageVO result = adminClient.sendMessage(request);

        assertThat(result.getMsgId()).isEqualTo("msg-1");
        ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
        verify(sendProducer).send(messageCaptor.capture());
        assertThat(messageCaptor.getValue().getBody()).hasSize(4 * 1024 * 1024);
        verify(clientPool).withProducer(eq("10.0.0.1:9876"), isNull(), isNull(), any());
    }

    @Test
    void sendMessageUsesSelectedInstancePooledProducer() throws Exception {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.SEND_OK);
        sendResult.setMsgId("msg-1");
        sendResult.setOffsetMsgId("offset-1");
        when(sendProducer.send(any(Message.class))).thenReturn(sendResult);

        SendMessageDTO request = new SendMessageDTO();
        request.setTopic("TopicA");
        request.setBody("hello");
        request.setInstanceId("instance-a");

        adminClient.sendMessage(request);

        verify(runtimeAdminClientResolver).executeProducer(eq("instance-a"), any());
        verify(clientPool, never()).withProducer(any(), any(), any(), any());
    }

    @Test
    void sendMessageShouldRejectNonSuccessfulSendStatus() throws Exception {
        SendResult sendResult = new SendResult();
        sendResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
        when(sendProducer.send(any(Message.class))).thenReturn(sendResult);

        SendMessageDTO request = new SendMessageDTO();
        request.setTopic("TopicA");
        request.setBody("hello");

        assertThatThrownBy(() -> adminClient.sendMessage(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FLUSH_DISK_TIMEOUT");

        verify(auditService).record(eq("SEND_MESSAGE"), eq("MESSAGE"), eq("TopicA"),
                eq(null), eq("Message send did not succeed: FLUSH_DISK_TIMEOUT"), eq("FAILED"));
    }

    @Test
    void sendMessageShouldRejectNullSendResult() throws Exception {
        when(sendProducer.send(any(Message.class))).thenReturn(null);

        SendMessageDTO request = new SendMessageDTO();
        request.setTopic("TopicA");
        request.setBody("hello");

        assertThatThrownBy(() -> adminClient.sendMessage(request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("null");

        verify(auditService).record(eq("SEND_MESSAGE"), eq("MESSAGE"), eq("TopicA"),
                eq(null), eq("Message send did not succeed: null"), eq("FAILED"));
    }
}
