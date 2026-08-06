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
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
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
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedConstruction;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
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

    private RocketMQAdminClientImpl adminClient;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        lenient().when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        adminClient = new RocketMQAdminClientImpl(adminFactory, properties, topicMapper, groupMapper, auditService,
                runtimeAdminClientResolver);
    }

    @Test
    void getConsumerGroupReturnsOfflineDetailForConsumerNotOnline() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE,
                        "Not found the consumer group connection"));

        ConsumerGroupVO group = adminClient.getConsumerGroup("orders");

        assertThat(group.getId()).isEqualTo("orders");
        assertThat(group.getOnlineInstances()).isZero();
    }

    @Test
    void resetOffsetShouldUseSelectedInstanceRuntimeClient() {
        adminClient.resetOffset("instance-a", "cg-orders", 1784246400000L, "orders");

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(auditService).record("RESET_OFFSET", "cg-orders",
                "instanceId=instance-a, topic=orders, timestamp=1784246400000", "SUCCESS");
    }

    @Test
    void getConsumerGroupSurfacesAdminTimeout() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new RemotingTimeoutException("broker-0", 3_000));

        assertThatThrownBy(() -> adminClient.getConsumerGroup("orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to get consumer group");
    }

    @Test
    void getConsumerGroupSurfacesBrokerFailures() throws Exception {
        when(adminExt.examineConsumerConnectionInfo("orders"))
                .thenThrow(new MQBrokerException(16, "ACL denied"));

        assertThatThrownBy(() -> adminClient.getConsumerGroup("orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL denied");
    }

    @Test
    void createTopicScopesLookupToCluster() throws Exception {
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
            assertThat(wrapper.getSqlSegment()).contains("cluster_id");
        }
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
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setInstanceId("instance-a");

        adminClient.createConsumerGroup(group);

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(selectedAdmin).createAndUpdateSubscriptionGroupConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), any());
        verify(adminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
    }

    @Test
    void deleteConsumerGroupUsesSelectedInstanceAdmin() throws Exception {
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        ClusterInfo clusterInfo = new ClusterInfo();
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
    }

    @Test
    void sendMessageShouldNotFailWhenAuditRecordingFails() throws Exception {
        when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        doThrow(new RuntimeException("audit db down")).when(auditService)
                .record(anyString(), anyString(), anyString(), anyString());
        try (MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         SendResult sendResult = new SendResult();
                         sendResult.setMsgId("msg-1");
                         sendResult.setOffsetMsgId("offset-1");
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            SendMessageDTO request = new SendMessageDTO();
            request.setTopic("TopicA");
            request.setBody("hello");
            SendMessageVO result = adminClient.sendMessage(request);
            // The message was already delivered; an audit failure must not turn this into an error.
            assertThat(result.getMsgId()).isEqualTo("msg-1");
        }
    }
}
