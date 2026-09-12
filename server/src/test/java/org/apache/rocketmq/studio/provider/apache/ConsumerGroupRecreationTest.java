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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.google.common.collect.ImmutableMap;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.common.SubscriptionGroupAttributes;
import org.apache.rocketmq.common.attribute.AttributeUtil;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupSettingsCommand;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConsumerGroupRecreationTest {
    private static final String BROKER = "10.0.0.1:10911";
    private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
    private final RmqGroupMapper groups = mock(RmqGroupMapper.class);
    private final AuditService audit = mock(AuditService.class);
    private RocketMQAdminClientImpl client;

    @BeforeEach
    void setUp() throws Exception {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
        when(resolver.execute(eq("local"), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(admin));
        client = new RocketMQAdminClientImpl(mock(MqAdminExtFactory.class), mock(RocketMQProperties.class),
                mock(RmqTopicMapper.class), groups, audit, resolver, mock(MqClientPool.class));
        when(admin.examineBrokerClusterInfo()).thenReturn(topology(Map.of("broker-a", BROKER)));
    }

    @Test
    void recreatingAPausedOrderedGroupPreservesItsConsumptionPolicyTest() throws Exception {
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        existing.setConsumeEnable(false);
        existing.setConsumeBroadcastEnable(false);
        existing.setConsumeMessageOrderly(true);
        existing.setRetryQueueNums(4);
        existing.setConsumeTimeoutMinute(45);
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);

        client.createConsumerGroup(request());

        ArgumentCaptor<SubscriptionGroupConfig> applied = ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), applied.capture());
        assertThat(applied.getValue().isConsumeEnable()).isFalse();
        assertThat(applied.getValue().isConsumeBroadcastEnable()).isFalse();
        assertThat(applied.getValue().isConsumeMessageOrderly()).isTrue();
        assertThat(applied.getValue().getRetryQueueNums()).isEqualTo(4);
        assertThat(applied.getValue().getConsumeTimeoutMinute()).isEqualTo(45);
    }

    @Test
    void omittedRetryPreservesExistingRetryPolicyAndMetadataTest() throws Exception {
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        existing.setRetryMaxTimes(0);
        existing.setConsumeFromMinEnable(false);
        existing.setNotifyConsumerIdsChangedEnable(false);
        existing.setBrokerId(2L);
        existing.setWhichBrokerWhenConsumeSlowly(3L);
        existing.setGroupSysFlag(8);
        var retryPolicy = existing.getGroupRetryPolicy();
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);
        RmqGroup stored = new RmqGroup();
        stored.setId(10L);
        stored.setName("orders");
        when(groups.selectOne(any())).thenReturn(stored);
        ConsumerGroupVO request = request();
        request.setRetryMaxTimes(0);

        ConsumerGroupVO result = client.createConsumerGroup(request);

        ArgumentCaptor<SubscriptionGroupConfig> applied = ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), applied.capture());
        assertThat(applied.getValue().getRetryMaxTimes()).isZero();
        assertThat(applied.getValue().getGroupRetryPolicy()).isSameAs(retryPolicy);
        assertThat(applied.getValue().isConsumeFromMinEnable()).isFalse();
        assertThat(applied.getValue().isNotifyConsumerIdsChangedEnable()).isFalse();
        assertThat(applied.getValue().getBrokerId()).isEqualTo(2L);
        assertThat(applied.getValue().getWhichBrokerWhenConsumeSlowly()).isEqualTo(3L);
        assertThat(applied.getValue().getGroupSysFlag()).isEqualTo(8);
        assertThat(result.getRetryMaxTimes()).isZero();
        assertThat(stored.getMaxRetry()).isZero();
        verify(groups).updateById(stored);
        verify(groups, never()).insert(any(RmqGroup.class));
    }

    @Test
    void explicitRetryChangesOnlyTheRequestedSettingTest() throws Exception {
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        existing.setRetryMaxTimes(40);
        existing.setConsumeEnable(false);
        existing.setRetryQueueNums(5);
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);

        ConsumerGroupVO result = client.createConsumerGroup(request());

        assertThat(existing.getRetryMaxTimes()).isEqualTo(7);
        assertThat(existing.isConsumeEnable()).isFalse();
        assertThat(existing.getRetryQueueNums()).isEqualTo(5);
        assertThat(result.getRetryMaxTimes()).isEqualTo(7);
        ArgumentCaptor<RmqGroup> persisted = ArgumentCaptor.forClass(RmqGroup.class);
        verify(groups).insert(persisted.capture());
        assertThat(persisted.getValue().getMaxRetry()).isEqualTo(7);
        assertThat(persisted.getValue().getInstanceId()).isEqualTo("local");
    }

    @Test
    void preservesEachBrokerPolicyInsteadOfCopyingOneAcrossTheClusterTest() throws Exception {
        String otherAddress = "10.0.0.2:10911";
        when(admin.examineBrokerClusterInfo()).thenReturn(topology(Map.of("broker-a", BROKER,
                "broker-b", otherAddress)));
        SubscriptionGroupConfig paused = new SubscriptionGroupConfig();
        paused.setGroupName("orders");
        paused.setConsumeEnable(false);
        paused.setRetryQueueNums(2);
        SubscriptionGroupConfig running = new SubscriptionGroupConfig();
        running.setGroupName("orders");
        running.setConsumeEnable(true);
        running.setRetryQueueNums(6);
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(paused);
        when(admin.examineSubscriptionGroupConfig(otherAddress, "orders")).thenReturn(running);

        client.createConsumerGroup(request());

        verify(admin).createAndUpdateSubscriptionGroupConfig(BROKER, paused);
        verify(admin).createAndUpdateSubscriptionGroupConfig(otherAddress, running);
        assertThat(paused.isConsumeEnable()).isFalse();
        assertThat(paused.getRetryQueueNums()).isEqualTo(2);
        assertThat(running.isConsumeEnable()).isTrue();
        assertThat(running.getRetryQueueNums()).isEqualTo(6);
        verify(audit).record(eq("CREATE_GROUP"), eq("GROUP"), eq("orders"), eq(null),
                contains("brokersUpdated=2/2"), eq("SUCCESS"));
    }

    @Test
    void createsMissingGroupWhenBrokerAutoCreationIsDisabledTest() throws Exception {
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders"))
                .thenThrow(new MQBrokerException(ResponseCode.SUBSCRIPTION_GROUP_NOT_EXIST, "Group not found"));
        ConsumerGroupVO request = request();
        request.setRetryMaxTimes(0);

        ConsumerGroupVO result = client.createConsumerGroup(request);

        ArgumentCaptor<SubscriptionGroupConfig> applied = ArgumentCaptor.forClass(SubscriptionGroupConfig.class);
        verify(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), applied.capture());
        assertThat(applied.getValue().getGroupName()).isEqualTo("orders");
        assertThat(applied.getValue().isConsumeEnable()).isTrue();
        assertThat(applied.getValue().isConsumeBroadcastEnable()).isTrue();
        assertThat(applied.getValue().getRetryQueueNums()).isEqualTo(1);
        assertThat(applied.getValue().getRetryMaxTimes()).isEqualTo(16);
        assertThat(result.getRetryMaxTimes()).isEqualTo(16);
    }

    @Test
    void unavailableConfigurationIsNotTreatedAsAMissingGroupTest() throws Exception {
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders"))
                .thenThrow(new MQBrokerException(ResponseCode.NO_PERMISSION, "Access denied"));

        assertThatThrownBy(() -> client.createConsumerGroup(request())).isInstanceOf(BusinessException.class);

        verify(admin, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verify(groups, never()).insert(any(RmqGroup.class));
        verify(groups, never()).updateById(any(RmqGroup.class));
        verify(audit).record(eq("CREATE_GROUP"), eq("GROUP"), eq("orders"), eq(null),
                contains("updated 0/1"), eq("FAILED"));
    }

    @Test
    void recordsPartialApplicationWhenALaterBrokerCannotBeReadTest() throws Exception {
        String otherAddress = "10.0.0.2:10911";
        when(admin.examineBrokerClusterInfo()).thenReturn(topology(Map.of("broker-a", BROKER,
                "broker-b", otherAddress)));
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);
        when(admin.examineSubscriptionGroupConfig(otherAddress, "orders"))
                .thenThrow(new RemotingTimeoutException("Timed out reading group settings"));

        assertThatThrownBy(() -> client.createConsumerGroup(request())).isInstanceOf(BusinessException.class);

        verify(admin).createAndUpdateSubscriptionGroupConfig(BROKER, existing);
        verify(admin, never()).createAndUpdateSubscriptionGroupConfig(eq(otherAddress), any());
        verify(groups, never()).insert(any(RmqGroup.class));
        verify(audit).record(eq("CREATE_GROUP"), eq("GROUP"), eq("orders"), eq(null),
                contains("updated 1/2"), eq("FAILED"));
    }

    @Test
    void reCreationUsesAnEmptyAttributePatchTest() throws Exception {
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        existing.setAttributes(new HashMap<>(Map.of("priority.factor", "25")));
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);
        Map<String, String> storedAttributes = Map.copyOf(existing.getAttributes());
        doAnswer(invocation -> {
            SubscriptionGroupConfig update = invocation.getArgument(1);
            assertThat(update.getAttributes()).isEmpty();
            // Use the exact Broker patch interpreter, rather than treating the payload as a map replacement.
            assertThat(AttributeUtil.alterCurrentAttributes(false, SubscriptionGroupAttributes.ALL,
                    ImmutableMap.copyOf(storedAttributes), ImmutableMap.copyOf(update.getAttributes())))
                    .isEqualTo(storedAttributes);
            return null;
        }).when(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), any());

        client.createConsumerGroup(request());

        verify(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), any());
    }

    @Test
    void settingsUpdateDoesNotReplayStoredAttributeNamesAsPatchOperationsTest() throws Exception {
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        existing.setConsumeEnable(false);
        existing.setAttributes(new HashMap<>(Map.of("priority.factor", "25")));
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);
        Map<String, String> storedAttributes = Map.copyOf(existing.getAttributes());
        doAnswer(invocation -> {
            SubscriptionGroupConfig update = invocation.getArgument(1);
            assertThat(AttributeUtil.alterCurrentAttributes(false, SubscriptionGroupAttributes.ALL,
                    ImmutableMap.copyOf(storedAttributes), ImmutableMap.copyOf(update.getAttributes())))
                    .isEqualTo(storedAttributes);
            return null;
        }).when(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), any());

        var result = client.updateConsumerGroupSettings("local", "orders",
                new ConsumerGroupSettingsCommand(3, 0, null, null, null));

        assertThat(result.getRetryMaxTimes()).isZero();
        assertThat(result.getRetryQueueNums()).isEqualTo(3);
        assertThat(result.isConsumeEnable()).isFalse();
        verify(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), any());
    }

    @Test
    void missingBrokerDoesNotPersistARecreatedGroupTest() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(topology(Map.of()));

        assertThatThrownBy(() -> client.createConsumerGroup(request()))
                .isInstanceOf(BusinessException.class).hasMessage("No broker available to create consumer group");

        verify(groups, never()).insert(any(RmqGroup.class));
        verify(admin, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verify(audit).record(eq("CREATE_GROUP"), eq("GROUP"), eq("orders"), eq(null),
                contains("updated 0/0"), eq("FAILED"));
    }

    @Test
    void settingsUpdateStopsWhenTheGroupDisappearsOnALaterBrokerTest() throws Exception {
        when(admin.examineBrokerClusterInfo()).thenReturn(topology(Map.of("broker-a", BROKER,
                "broker-b", "10.0.0.2:10911")));
        // Drive the first read by call order because this existing settings path does not sort brokers.
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        when(admin.examineSubscriptionGroupConfig(anyString(), eq("orders")))
                .thenReturn(existing, null);

        assertThatThrownBy(() -> client.updateConsumerGroupSettings("local", "orders",
                new ConsumerGroupSettingsCommand(2, 5, true, true, true)))
                .isInstanceOf(BusinessException.class).hasMessage("Consumer group not found: orders");

        verify(admin).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verify(groups, never()).updateById(any(RmqGroup.class));
        verify(audit).record(eq("UPDATE_GROUP_SETTINGS"), eq("GROUP"), eq("orders"), eq(null),
                contains("updated 1/2"), eq("FAILED"));
    }

    @Test
    void settingsWriteFailureIsReportedWithoutPersistingSuccessTest() throws Exception {
        SubscriptionGroupConfig existing = new SubscriptionGroupConfig();
        existing.setGroupName("orders");
        when(admin.examineSubscriptionGroupConfig(BROKER, "orders")).thenReturn(existing);
        doAnswer(invocation -> {
            throw new RemotingTimeoutException("Broker did not confirm the update");
        }).when(admin).createAndUpdateSubscriptionGroupConfig(eq(BROKER), any());

        assertThatThrownBy(() -> client.updateConsumerGroupSettings("local", "orders",
                new ConsumerGroupSettingsCommand(2, 5, null, null, null)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("Broker did not confirm");

        verify(groups, never()).updateById(any(RmqGroup.class));
        verify(audit).record(eq("UPDATE_GROUP_SETTINGS"), eq("GROUP"), eq("orders"), eq(null),
                contains("updated 0/1"), eq("FAILED"));
    }

    private static ConsumerGroupVO request() {
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("orders");
        group.setInstanceId("local");
        group.setRetryMaxTimes(7);
        return group;
    }

    private static ClusterInfo topology(Map<String, String> brokers) {
        ClusterInfo info = new ClusterInfo();
        info.setClusterAddrTable(new HashMap<>(Map.of("cluster-a", new HashSet<>(brokers.keySet()))));
        HashMap<String, BrokerData> table = new HashMap<>();
        brokers.forEach((name, address) -> {
            BrokerData broker = new BrokerData();
            broker.setBrokerName(name);
            broker.setBrokerAddrs(new HashMap<>(Map.of(0L, address)));
            table.put(name, broker);
        });
        info.setBrokerAddrTable(table);
        return info;
    }
}
