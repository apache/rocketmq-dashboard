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
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.remoting.RPCHook;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.rocketmq.studio.common.domain.enums.TopicPerm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockConstruction;
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

        ConsumerGroupVO group = adminClient.getConsumerGroup(null, "orders");

        assertThat(group.getName()).isEqualTo("orders");
        assertThat(group.getOnlineInstances()).isZero();
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
    void resetOffsetShouldUseRequestedClusterOnSelectedInstance() throws Exception {
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoClusters());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));

        adminClient.resetOffset(
                "instance-a", "cg-orders", 1784246400000L, "orders", "cluster-2");

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(selectedAdmin).resetOffsetByTimestamp(
                "cluster-2", "orders", "cg-orders", 1784246400000L, false);
        verify(auditService).record("RESET_OFFSET", "cg-orders",
                "instanceId=instance-a, topic=orders, timestamp=1784246400000", "SUCCESS");
    }

    @Test
    void mutationsRejectAmbiguousClusterInsteadOfChoosingFirstCluster() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoClusters());
        TopicVO topic = new TopicVO();
        topic.setName("orders");

        assertThatThrownBy(() -> adminClient.createTopic(topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clusterId is required");
        assertThatThrownBy(() -> adminClient.resetOffset(
                null, "cg-orders", 1784246400000L, "orders"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("clusterId is required");

        verify(adminExt, never()).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        verify(adminExt, never()).resetOffsetByTimestamp(
                anyString(), anyString(), anyString(), org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyBoolean());
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
        verify(auditService).record("RESET_OFFSET", "cg-orders",
                "Instance not found: missing-instance", "FAILED");
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
        verify(auditService).record("RESET_OFFSET", "cg-orders", "broker unavailable", "FAILED");
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
                org.mockito.ArgumentMatchers.eq("10.0.0.2:10911"), any(TopicConfig.class));
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
    void topicWritesFailClosedWhenTargetClusterHasNoResolvableBroker() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMissingTargetBroker());
        when(topicMapper.selectOne(any())).thenReturn(null);
        TopicVO topic = new TopicVO();
        topic.setName("orders");
        topic.setClusterId("cluster-1");

        assertThatThrownBy(() -> adminClient.createTopic(topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No broker available to create topic");
        assertThatThrownBy(() -> adminClient.updateTopic(topic))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No broker available to update topic");

        verify(adminExt, never()).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        verify(topicMapper, never()).insert(any(RmqTopic.class));
        verify(topicMapper, never()).updateById(any(RmqTopic.class));
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
    void topicDeleteUsesSelectedInstanceAndScopesBrokersAndMetadataToCluster() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoClusters());
        doNothing().when(selectedAdmin).deleteTopicInBroker(any(), anyString());
        doNothing().when(selectedAdmin).deleteTopicInNameServer(any(), anyString(), anyString());
        when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("10.0.0.2:9876");
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));

        adminClient.deleteTopic("instance-a", "orders", "cluster-2");

        ArgumentCaptor<LambdaQueryWrapper<RmqTopic>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(topicMapper).delete(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("cluster_id", "name");
        verify(selectedAdmin).deleteTopicInBroker(Set.of("10.0.0.2:10911"), "orders");
        verify(selectedAdmin, never()).deleteTopicInBroker(Set.of("10.0.0.1:10911"), "orders");
        verify(selectedAdmin).deleteTopicInNameServer(Set.of("10.0.0.2:9876"), "cluster-2", "orders");
    }

    @Test
    void topicDeleteFailsClosedWhenTargetClusterHasNoResolvableBroker() throws Exception {
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMissingTargetBroker());
        when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("10.0.0.2:9876");
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1)
                        .apply(selectedAdmin));

        assertThatThrownBy(() -> adminClient.deleteTopic("instance-a", "orders", "cluster-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No broker available to delete topic");

        verify(selectedAdmin, never()).deleteTopicInBroker(any(), anyString());
        verify(selectedAdmin, never()).deleteTopicInNameServer(any(), anyString(), anyString());
        verify(topicMapper, never()).delete(any());
    }

    @Test
    void deleteTopicScopesBrokerDeletionToSelectedClusterOnly() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoClusters());
        doNothing().when(adminExt).deleteTopicInBroker(any(), anyString());
        doNothing().when(adminExt).deleteTopicInNameServer(any(), anyString(), anyString());

        adminClient.deleteTopic(null, "orders", "cluster-1");

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
        group.setClusterId("cluster-1");
        group.setInstanceId("open-source-local");

        adminClient.createConsumerGroup(group);

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("open-source-local"), any());
        verify(selectedAdmin).createAndUpdateSubscriptionGroupConfig(
                org.mockito.ArgumentMatchers.eq("10.0.0.1:10911"), any());
        verify(adminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
    }

    @Test
    void createConsumerGroupFailsClosedWhenTargetClusterHasNoResolvableBroker() throws Exception {
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMissingTargetBroker());
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("cg-orders");
        group.setClusterId("cluster-1");

        assertThatThrownBy(() -> adminClient.createConsumerGroup(group))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No broker available to create consumer group");

        verify(adminExt, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verify(groupMapper, never()).insert(any(RmqGroup.class));
        verify(groupMapper, never()).updateById(any(RmqGroup.class));
    }

    @Test
    void deleteConsumerGroupUsesSelectedInstanceAdminAndScopesBrokersToCluster() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoClusters());
        doNothing().when(selectedAdmin).deleteSubscriptionGroup(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        adminClient.deleteConsumerGroup("instance-a", "cg-orders", "cluster-2");

        verify(runtimeAdminClientResolver).execute(org.mockito.ArgumentMatchers.eq("instance-a"), any());
        verify(selectedAdmin).deleteSubscriptionGroup("10.0.0.2:10911", "cg-orders", true);
        verify(selectedAdmin, never()).deleteSubscriptionGroup("10.0.0.1:10911", "cg-orders", true);
        verify(adminExt, never()).deleteSubscriptionGroup(anyString(), anyString(), org.mockito.ArgumentMatchers.anyBoolean());
        ArgumentCaptor<LambdaQueryWrapper<RmqGroup>> captor = ArgumentCaptor.forClass(LambdaQueryWrapper.class);
        verify(groupMapper).delete(captor.capture());
        assertThat(captor.getValue().getSqlSegment()).contains("cluster_id", "name");
    }

    @Test
    void deleteConsumerGroupFailsClosedWhenTargetClusterHasNoResolvableBroker() throws Exception {
        DefaultMQAdminExt selectedAdmin = org.mockito.Mockito.mock(DefaultMQAdminExt.class);
        when(selectedAdmin.examineBrokerClusterInfo()).thenReturn(clusterInfoWithMissingTargetBroker());
        when(runtimeAdminClientResolver.execute(org.mockito.ArgumentMatchers.eq("instance-a"), any()))
                .thenAnswer(invocation -> {
                    MqAdminExtFactory.AdminAction<?> action = invocation.getArgument(1);
                    return action.apply(selectedAdmin);
                });

        assertThatThrownBy(() -> adminClient.deleteConsumerGroup(
                "instance-a", "cg-orders", "cluster-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("No broker available to delete consumer group");

        verify(selectedAdmin, never()).deleteSubscriptionGroup(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());
        verify(groupMapper, never()).delete(any());
    }

    @Test
    void deleteConsumerGroupScopesBrokerDeletionToSelectedClusterOnly() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        when(adminExt.examineBrokerClusterInfo()).thenReturn(clusterInfoWithTwoClusters());
        doNothing().when(adminExt).deleteSubscriptionGroup(anyString(), anyString(),
                org.mockito.ArgumentMatchers.anyBoolean());

        adminClient.deleteConsumerGroup(null, "cg-orders", "cluster-1");

        verify(adminExt).deleteSubscriptionGroup("10.0.0.1:10911", "cg-orders", true);
        verify(adminExt, never()).deleteSubscriptionGroup("10.0.0.2:10911", "cg-orders", true);
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
                .record(anyString(), anyString(), anyString(), anyString());

        TopicVO topic = new TopicVO();
        topic.setName("topicA");

        assertThat(adminClient.createTopic(topic).getId()).isEqualTo(1L);
        verify(auditService).record("CREATE_TOPIC", "topicA", "queues=8/8", "SUCCESS");
    }

    @Test
    void createTopicPreservesRemoteFailureWhenAuditRecordingFails() throws Exception {
        when(adminExt.examineBrokerClusterInfo())
                .thenThrow(new IllegalStateException("broker unavailable"));
        doThrow(new RuntimeException("audit db down")).when(auditService)
                .record(anyString(), anyString(), anyString(), anyString());

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

    private ClusterInfo clusterInfoWithTwoClusters() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new LinkedHashMap<>();
        clusterAddrTable.put("cluster-1", new HashSet<>(List.of("broker-1")));
        clusterAddrTable.put("cluster-2", new HashSet<>(List.of("broker-2")));
        clusterInfo.setClusterAddrTable(clusterAddrTable);

        Map<String, BrokerData> brokerAddrTable = new HashMap<>();
        BrokerData firstBroker = new BrokerData();
        firstBroker.setBrokerName("broker-1");
        firstBroker.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.1:10911")));
        BrokerData secondBroker = new BrokerData();
        secondBroker.setBrokerName("broker-2");
        secondBroker.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.2:10911")));
        brokerAddrTable.put("broker-1", firstBroker);
        brokerAddrTable.put("broker-2", secondBroker);
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    private ClusterInfo clusterInfoWithMissingTargetBroker() {
        ClusterInfo clusterInfo = new ClusterInfo();
        Map<String, Set<String>> clusterAddrTable = new LinkedHashMap<>();
        clusterAddrTable.put("cluster-1", new HashSet<>(List.of("missing-broker")));
        clusterAddrTable.put("cluster-2", new HashSet<>(List.of("broker-2")));
        clusterInfo.setClusterAddrTable(clusterAddrTable);

        BrokerData otherBroker = new BrokerData();
        otherBroker.setBrokerName("broker-2");
        otherBroker.setBrokerAddrs(new HashMap<>(Map.of(0L, "10.0.0.2:10911")));
        clusterInfo.setBrokerAddrTable(new HashMap<>(Map.of("broker-2", otherBroker)));
        return clusterInfo;
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
                         sendResult.setSendStatus(SendStatus.SEND_OK);
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

    @Test
    void sendMessageShouldRejectOversizedUtf8BodyBeforeResolvingEndpointOrCreatingProducer() {
        SendMessageDTO request = new SendMessageDTO();
        request.setInstanceId("instance-a");
        request.setTopic("TopicA");
        request.setBody("\u754c".repeat((4 * 1024 * 1024 / 3) + 1));

        try (MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class)) {
            assertThatThrownBy(() -> adminClient.sendMessage(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("exceeds the maximum")
                    .satisfies(exception -> assertThat(((BusinessException) exception).getCode()).isEqualTo(400));

            assertThat(mockedProducers.constructed()).isEmpty();
        }
        verifyNoInteractions(runtimeAdminClientResolver);
        verify(properties, never()).getNamesrvAddr();
        verify(auditService).record("SEND_MESSAGE", "TopicA",
                "Message body size 4194306 exceeds the maximum of 4194304 bytes", "FAILED");
    }

    @Test
    void sendMessageShouldAllowBodyAtMaximumSize() throws Exception {
        when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        List<List<?>> constructorArguments = new ArrayList<>();
        try (MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         constructorArguments.add(context.arguments());
                         doNothing().when(producer).start();
                         SendResult sendResult = new SendResult();
                         sendResult.setSendStatus(SendStatus.SEND_OK);
                         sendResult.setMsgId("msg-1");
                         sendResult.setOffsetMsgId("offset-1");
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            SendMessageDTO request = new SendMessageDTO();
            request.setTopic("TopicA");
            request.setBody("x".repeat(4 * 1024 * 1024));

            SendMessageVO result = adminClient.sendMessage(request);

            assertThat(result.getMsgId()).isEqualTo("msg-1");
            DefaultMQProducer producer = mockedProducers.constructed().getFirst();
            ArgumentCaptor<Message> messageCaptor = ArgumentCaptor.forClass(Message.class);
            verify(producer).send(messageCaptor.capture());
            assertThat(messageCaptor.getValue().getBody()).hasSize(4 * 1024 * 1024);
            assertThat(constructorArguments).singleElement();
            assertThat(constructorArguments.get(0)).hasSize(2);
            assertThat(constructorArguments.get(0).get(1)).isNull();
        }
    }

    @Test
    void sendMessageUsesSelectedInstanceEndpointAndCredentialHook() throws Exception {
        RPCHook credentialHook = mock(RPCHook.class);
        List<List<?>> constructorArguments = new ArrayList<>();
        when(runtimeAdminClientResolver.resolveEndpoint("instance-a")).thenReturn("10.0.0.2:9876");
        when(runtimeAdminClientResolver.resolveCredentialHook("instance-a")).thenReturn(credentialHook);
        try (MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         constructorArguments.add(context.arguments());
                         doNothing().when(producer).start();
                         SendResult sendResult = new SendResult();
                         sendResult.setSendStatus(SendStatus.SEND_OK);
                         sendResult.setMsgId("msg-1");
                         sendResult.setOffsetMsgId("offset-1");
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            SendMessageDTO request = new SendMessageDTO();
            request.setTopic("TopicA");
            request.setBody("hello");
            request.setInstanceId("instance-a");

            adminClient.sendMessage(request);

            DefaultMQProducer producer = mockedProducers.constructed().getFirst();
            verify(producer).setNamesrvAddr("10.0.0.2:9876");
            assertThat(constructorArguments).singleElement();
            assertThat(constructorArguments.get(0)).hasSize(2);
            assertThat(constructorArguments.get(0).get(1)).isSameAs(credentialHook);
        }
        verify(runtimeAdminClientResolver).resolveCredentialHook("instance-a");
    }

    @Test
    void sendMessageShouldRejectNonSuccessfulSendStatus() throws Exception {
        when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        try (MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         SendResult sendResult = new SendResult();
                         sendResult.setSendStatus(SendStatus.FLUSH_DISK_TIMEOUT);
                         when(producer.send(any(Message.class))).thenReturn(sendResult);
                         doNothing().when(producer).shutdown();
                     })) {
            SendMessageDTO request = new SendMessageDTO();
            request.setTopic("TopicA");
            request.setBody("hello");

            assertThatThrownBy(() -> adminClient.sendMessage(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("FLUSH_DISK_TIMEOUT");

            verify(auditService).record("SEND_MESSAGE", "TopicA",
                    "Message send did not succeed: FLUSH_DISK_TIMEOUT", "FAILED");
        }
    }

    @Test
    void sendMessageShouldRejectNullSendResult() throws Exception {
        when(properties.getNamesrvAddr()).thenReturn("10.0.0.1:9876");
        try (MockedConstruction<DefaultMQProducer> mockedProducers =
                     mockConstruction(DefaultMQProducer.class, (producer, context) -> {
                         doNothing().when(producer).start();
                         when(producer.send(any(Message.class))).thenReturn(null);
                         doNothing().when(producer).shutdown();
                     })) {
            SendMessageDTO request = new SendMessageDTO();
            request.setTopic("TopicA");
            request.setBody("hello");

            assertThatThrownBy(() -> adminClient.sendMessage(request))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("null");

            verify(auditService).record("SEND_MESSAGE", "TopicA",
                    "Message send did not succeed: null", "FAILED");
        }
    }
}
