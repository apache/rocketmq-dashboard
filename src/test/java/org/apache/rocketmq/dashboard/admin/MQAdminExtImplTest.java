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

package org.apache.rocketmq.dashboard.admin;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.message.MessageRequestMode;
import org.apache.rocketmq.dashboard.service.client.MQAdminExtImpl;
import org.apache.rocketmq.dashboard.service.client.MQAdminInstance;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.exception.RemotingTimeoutException;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.BrokerStatsData;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.AclInfo;
import org.apache.rocketmq.remoting.protocol.body.HARuntimeInfo;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.apache.rocketmq.remoting.protocol.body.ConsumeMessageDirectlyResult;
import org.apache.rocketmq.remoting.protocol.body.ConsumeStatsList;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.remoting.protocol.body.GroupList;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.QueueTimeSpan;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicConfigSerializeWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.store.stats.BrokerStatsManager;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MQAdminExtImplTest {

    @InjectMocks
    private MQAdminExtImpl mqAdminExtImpl;

    @Mock
    private DefaultMQAdminExt defaultMQAdminExt;

    @Mock
    private DefaultMQAdminExtImpl defaultMQAdminExtImpl;

    @Mock
    private MQClientInstance mqClientInstance;

    @Mock
    private MQClientAPIImpl mQClientAPIImpl;

    @Mock
    private RemotingClient remotingClient;

    private String brokerAddr = "127.0.0.1:10911";

    @Before
    public void init() throws Exception {
        Field field = MQAdminInstance.class.getDeclaredField("MQ_ADMIN_EXT_THREAD_LOCAL");
        field.setAccessible(true);
        Object object = field.get(mqAdminExtImpl);
        assertNotNull(object);
        ThreadLocal<DefaultMQAdminExt> threadLocal = (ThreadLocal<DefaultMQAdminExt>) object;
        defaultMQAdminExt = mock(DefaultMQAdminExt.class);
        threadLocal.set(defaultMQAdminExt);

        ReflectionTestUtils.setField(defaultMQAdminExt, "defaultMQAdminExtImpl", defaultMQAdminExtImpl);
        ReflectionTestUtils.setField(defaultMQAdminExtImpl, "mqClientInstance", mqClientInstance);
        ReflectionTestUtils.setField(mqClientInstance, "mQClientAPIImpl", mQClientAPIImpl);
        ReflectionTestUtils.setField(mQClientAPIImpl, "remotingClient", remotingClient);
    }

    @Test
    public void testUpdateBrokerConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        doNothing()
                .doThrow(new MQBrokerException(0, ""))
                .when(defaultMQAdminExt).updateBrokerConfig(anyString(), any());
        mqAdminExtImpl.updateBrokerConfig(brokerAddr, new Properties());
        boolean hasException = false;
        try {
            mqAdminExtImpl.updateBrokerConfig(brokerAddr, new Properties());
        } catch (Exception e) {
            hasException = true;
            assertThat(e).isInstanceOf(MQBrokerException.class);
            assertThat(((MQBrokerException) e).getResponseCode()).isEqualTo(0);
        }
        assertTrue(hasException);
    }

    @Test
    public void testCreateAndUpdateTopicConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        mqAdminExtImpl.createAndUpdateTopicConfig(brokerAddr, new TopicConfig());
    }


    @Test
    public void testQueryConsumerStatus() throws Exception {
        assertNotNull(mqAdminExtImpl);
    }

    @Test
    public void testCreateAndUpdateSubscriptionGroupConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        mqAdminExtImpl.createAndUpdateSubscriptionGroupConfig(brokerAddr, new SubscriptionGroupConfig());
    }

    @Test
    public void testExamineSubscriptionGroupConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);

        // Create valid SubscriptionGroupWrapper with group_test entry
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        ConcurrentMap<String, SubscriptionGroupConfig> subscriptionGroupTable = new ConcurrentHashMap<>();
        SubscriptionGroupConfig config = new SubscriptionGroupConfig();
        config.setGroupName("group_test");
        subscriptionGroupTable.put("group_test", config);
        wrapper.setSubscriptionGroupTable(subscriptionGroupTable);

        // Create successful response
        RemotingCommand successResponse = RemotingCommand.createResponseCommand(null);
        successResponse.setCode(ResponseCode.SUCCESS);
        successResponse.setBody(RemotingSerializable.encode(wrapper));

        // Mock the remote invocation
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenReturn(successResponse);

        // Test successful case
        SubscriptionGroupConfig subscriptionGroupConfig = mqAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "group_test");
        Assert.assertNotNull(subscriptionGroupConfig);
        Assert.assertEquals("group_test", subscriptionGroupConfig.getGroupName());
    }

    @Test
    public void testExamineTopicConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);

        // Create valid TopicConfigSerializeWrapper with topictest entry
        TopicConfig config = new TopicConfig();
        config.setTopicName("topic_test");


        // Create successful response
        RemotingCommand successResponse = RemotingCommand.createResponseCommand(null);
        successResponse.setCode(ResponseCode.SUCCESS);
        successResponse.setBody(RemotingSerializable.encode(config));

        // Mock the remote invocation
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenReturn(successResponse);

        // Test successful case
        TopicConfig topicConfig = mqAdminExtImpl.examineTopicConfig(brokerAddr, "topic_test");
        Assert.assertNotNull(topicConfig);
        Assert.assertEquals("topic_test", topicConfig.getTopicName());
    }


    @Test
    public void testExamineTopicStats() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.examineTopicStats(anyString())).thenReturn(MockObjectUtil.createTopicStatsTable());
        }
        TopicStatsTable topicStatsTable = mqAdminExtImpl.examineTopicStats("topic_test");
        Assert.assertNotNull(topicStatsTable);
        Assert.assertEquals(1, topicStatsTable.getOffsetTable().size());
    }

    @Test
    public void testExamineAllTopicConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);

    }

    @Test
    public void testFetchAllTopicList() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.fetchAllTopicList()).thenReturn(new TopicList());
        }
        TopicList topicList = mqAdminExtImpl.fetchAllTopicList();
        Assert.assertNotNull(topicList);
    }

    @Test
    public void testFetchBrokerRuntimeStats() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.fetchBrokerRuntimeStats(anyString())).thenReturn(new KVTable());
        }
        KVTable kvTable = mqAdminExtImpl.fetchBrokerRuntimeStats(brokerAddr);
        Assert.assertNotNull(kvTable);
    }

    @Test
    public void testExamineConsumeStats() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.examineConsumeStats(anyString())).thenReturn(MockObjectUtil.createConsumeStats());
            when(defaultMQAdminExt.examineConsumeStats(anyString(), anyString())).thenReturn(MockObjectUtil.createConsumeStats());
        }
        ConsumeStats consumeStats = mqAdminExtImpl.examineConsumeStats("group_test");
        ConsumeStats consumeStatsWithTopic = mqAdminExtImpl.examineConsumeStats("group_test", "topic_test");
        Assert.assertNotNull(consumeStats);
        Assert.assertEquals(consumeStats.getOffsetTable().size(), 2);
        Assert.assertNotNull(consumeStatsWithTopic);
        Assert.assertEquals(consumeStatsWithTopic.getOffsetTable().size(), 2);
    }

    @Test
    public void testExamineBrokerClusterInfo() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        }
        ClusterInfo clusterInfo = mqAdminExtImpl.examineBrokerClusterInfo();
        Assert.assertNotNull(clusterInfo);
        Assert.assertEquals(clusterInfo.getBrokerAddrTable().size(), 1);
        Assert.assertEquals(clusterInfo.getClusterAddrTable().size(), 1);
    }

    @Test
    public void testExamineTopicRouteInfo() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.examineTopicRouteInfo(anyString())).thenReturn(MockObjectUtil.createTopicRouteData());
        }
        TopicRouteData topicRouteData = mqAdminExtImpl.examineTopicRouteInfo("topic_test");
        Assert.assertNotNull(topicRouteData);
    }

    @Test
    public void testExamineConsumerConnectionInfo() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.examineConsumerConnectionInfo(anyString())).thenReturn(new ConsumerConnection());
        }
        ConsumerConnection consumerConnection = mqAdminExtImpl.examineConsumerConnectionInfo("group_test");
        Assert.assertNotNull(consumerConnection);
    }

    @Test
    public void testExamineProducerConnectionInfo() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.examineProducerConnectionInfo(anyString(), anyString())).thenReturn(new ProducerConnection());
        }
        ProducerConnection producerConnection = mqAdminExtImpl.examineProducerConnectionInfo("group_test", "topic_test");
        Assert.assertNotNull(producerConnection);
    }

    @Test
    public void testGetNameServerAddressList() {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getNameServerAddressList()).thenReturn(Lists.asList("127.0.0.1:9876", new String[]{"127.0.0.2:9876"}));
        }
        List<String> list = mqAdminExtImpl.getNameServerAddressList();
        Assert.assertEquals(list.size(), 2);
    }

    @Test
    public void testWipeWritePermOfBroker() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.wipeWritePermOfBroker(anyString(), anyString())).thenReturn(6);
        }
        int result = mqAdminExtImpl.wipeWritePermOfBroker("127.0.0.1:9876", "broker-a");
        Assert.assertEquals(result, 6);
    }

    @Test
    public void testPutKVConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).putKVConfig(anyString(), anyString(), anyString());
        }
        mqAdminExtImpl.putKVConfig("namespace", "key", "value");
    }

    @Test
    public void testGetKVConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getKVConfig(anyString(), anyString())).thenReturn("value");
        }
        String value = mqAdminExtImpl.getKVConfig("namespace", "key");
        Assert.assertEquals(value, "value");
    }

    @Test
    public void testGetKVListByNamespace() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getKVListByNamespace(anyString())).thenReturn(new KVTable());
        }
        KVTable kvTable = mqAdminExtImpl.getKVListByNamespace("namespace");
        Assert.assertNotNull(kvTable);
    }

    @Test
    public void testDeleteTopicInBroker() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).deleteTopicInBroker(any(), anyString());
        }
        mqAdminExtImpl.deleteTopicInBroker(Sets.newHashSet("127.0.0.1:10911"), "topic_test");
    }

    @Test
    public void testDeleteTopicInNameServer() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).deleteTopicInNameServer(any(), anyString());
        }
        mqAdminExtImpl.deleteTopicInNameServer(Sets.newHashSet("127.0.0.1:9876", "127.0.0.2:9876"), "topic_test");
    }

    @Test
    public void testDeleteSubscriptionGroup() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).deleteSubscriptionGroup(anyString(), anyString());
            doNothing().when(defaultMQAdminExt).deleteSubscriptionGroup(anyString(), anyString(), anyBoolean());
        }
        mqAdminExtImpl.deleteSubscriptionGroup(brokerAddr, "group_test");
        mqAdminExtImpl.deleteSubscriptionGroup(brokerAddr, "group_test", true);
    }

    @Test
    public void testCreateAndUpdateKvConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).createAndUpdateKvConfig(anyString(), anyString(), anyString());
        }
        mqAdminExtImpl.createAndUpdateKvConfig("namespace", "key", "value");
    }

    @Test
    public void testDeleteKvConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).deleteKvConfig(anyString(), anyString());
        }
        mqAdminExtImpl.deleteKvConfig("namespace", "key");
    }

    @Test
    public void testDeleteConsumerOffset() throws Exception {
        assertNotNull(mqAdminExtImpl);
    }

    @Test
    public void testResetOffsetByTimestampOld() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.resetOffsetByTimestampOld(anyString(), anyString(), anyLong(), anyBoolean())).thenReturn(new ArrayList<RollbackStats>());
        }
        List<RollbackStats> stats = mqAdminExtImpl.resetOffsetByTimestampOld("group_test", "topic_test", 1628495765398L, false);
        Assert.assertNotNull(stats);
    }

    @Test
    public void testResetOffsetByTimestamp() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.resetOffsetByTimestamp(anyString(), anyString(), anyLong(), anyBoolean())).thenReturn(new HashMap<MessageQueue, Long>());
        }
        Map<MessageQueue, Long> map = mqAdminExtImpl.resetOffsetByTimestamp("group_test", "topic_test", 1628495765398L, false);
        Assert.assertNotNull(map);
    }

    @Test
    public void testResetOffsetNew() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).resetOffsetNew(anyString(), anyString(), anyLong());
        }
        mqAdminExtImpl.resetOffsetNew("group_test", "topic_test", 1628495765398L);
    }

    @Test
    public void testGetConsumeStatus() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getConsumeStatus(anyString(), anyString(), anyString())).thenReturn(new HashMap<String, Map<MessageQueue, Long>>());
        }
        mqAdminExtImpl.getConsumeStatus("topic_test", "group_test", "");
    }

    @Test
    public void testCreateOrUpdateOrderConf() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).createOrUpdateOrderConf(anyString(), anyString(), anyBoolean());
        }
        mqAdminExtImpl.createOrUpdateOrderConf("key", "value", false);
    }

    @Test
    public void testQueryTopicConsumeByWho() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.queryTopicConsumeByWho(anyString())).thenReturn(new GroupList());
        }
        GroupList groupList = mqAdminExtImpl.queryTopicConsumeByWho("topic_test");
        Assert.assertNotNull(groupList);
    }

    @Test
    public void testCleanExpiredConsumerQueue() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.cleanExpiredConsumerQueue(anyString())).thenReturn(true);
        }
        boolean result = mqAdminExtImpl.cleanExpiredConsumerQueue("DefaultCluster");
        Assert.assertEquals(result, true);
    }

    @Test
    public void testCleanExpiredConsumerQueueByAddr() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.cleanExpiredConsumerQueueByAddr(anyString())).thenReturn(true);
        }
        boolean result = mqAdminExtImpl.cleanExpiredConsumerQueueByAddr("DefaultCluster");
        Assert.assertEquals(result, true);
    }

    @Test
    public void testGetConsumerRunningInfo() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getConsumerRunningInfo(anyString(), anyString(), anyBoolean())).thenReturn(new ConsumerRunningInfo());
        }
        ConsumerRunningInfo consumerRunningInfo = mqAdminExtImpl.getConsumerRunningInfo("group_test", "", true);
        Assert.assertNotNull(consumerRunningInfo);
    }

    @Test
    public void testConsumeMessageDirectly() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {

            when(defaultMQAdminExt.consumeMessageDirectly(anyString(), anyString(), anyString(), anyString())).thenReturn(new ConsumeMessageDirectlyResult());
        }
        ConsumeMessageDirectlyResult result2 = mqAdminExtImpl.consumeMessageDirectly("group_test", "", "topic_test", "7F000001ACC018B4AAC2116AF6500000");
        Assert.assertNotNull(result2);
    }

    @Test
    public void testMessageTrackDetail() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.messageTrackDetail(any())).thenReturn(new ArrayList<MessageTrack>());
        }
        List<MessageTrack> tracks = mqAdminExtImpl.messageTrackDetail(new MessageExt());
        Assert.assertNotNull(tracks);
    }

    @Test
    public void testCloneGroupOffset() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).cloneGroupOffset(anyString(), anyString(), anyString(), anyBoolean());
        }
        mqAdminExtImpl.cloneGroupOffset("group_test", "group_test1", "topic_test", false);
    }

    @Test
    public void testCreateTopic() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).createTopic(anyString(), anyString(), anyInt(), anyMap());
            doNothing().when(defaultMQAdminExt).createTopic(anyString(), anyString(), anyInt(), anyInt(), anyMap());
        }
        Map<String, String> map = new HashMap<>();
        map.put("message.type", "FIFO");
        mqAdminExtImpl.createTopic("key", "topic_test", 8, map);
        mqAdminExtImpl.createTopic("key", "topic_test", 8, 1, map);
    }

    @Test
    public void testSearchOffset() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.searchOffset(any(), anyLong())).thenReturn(Long.MAX_VALUE);
        }
        long offset = mqAdminExtImpl.searchOffset(new MessageQueue(), 1628495765398L);
        Assert.assertEquals(offset, Long.MAX_VALUE);
    }

    @Test
    public void testMaxOffset() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.maxOffset(any())).thenReturn(Long.MAX_VALUE);
        }
        long offset = mqAdminExtImpl.maxOffset(new MessageQueue());
        Assert.assertEquals(offset, Long.MAX_VALUE);
    }

    @Test
    public void testMinOffset() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.minOffset(any())).thenReturn(Long.MIN_VALUE);
        }
        long offset = mqAdminExtImpl.minOffset(new MessageQueue());
        Assert.assertEquals(offset, Long.MIN_VALUE);
    }

    @Test
    public void testEarliestMsgStoreTime() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.earliestMsgStoreTime(any())).thenReturn(1628495765398L);
        }
        long storeTime = mqAdminExtImpl.earliestMsgStoreTime(new MessageQueue());
        Assert.assertEquals(storeTime, 1628495765398L);
    }


    @Test
    public void testQueryMessage() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong())).thenReturn(mock(QueryResult.class));
        }
        QueryResult result = mqAdminExtImpl.queryMessage("topic_test", "key", 32, 1627804565000L, System.currentTimeMillis());
        Assert.assertNotNull(result);
    }

    @Test
    public void testQueryConsumeTimeSpan() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.queryConsumeTimeSpan(anyString(), anyString())).thenReturn(new ArrayList<QueueTimeSpan>());
        }
        List<QueueTimeSpan> timeSpans = mqAdminExtImpl.queryConsumeTimeSpan("topic_test", "group_test");
        Assert.assertNotNull(timeSpans);
    }


    @Test
    public void testGetBrokerConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getBrokerConfig(anyString())).thenReturn(new Properties());
        }
        Properties brokerConfig = mqAdminExtImpl.getBrokerConfig(brokerAddr);
        Assert.assertNotNull(brokerConfig);
    }

    @Test
    public void testFetchTopicsByCLuster() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.fetchTopicsByCLuster(anyString())).thenReturn(new TopicList());
        }
        TopicList topicList = mqAdminExtImpl.fetchTopicsByCLuster("DefaultCluster");
        Assert.assertNotNull(topicList);
    }

    @Test
    public void testCleanUnusedTopic() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.cleanUnusedTopic(anyString())).thenReturn(true);
            when(defaultMQAdminExt.cleanUnusedTopicByAddr(anyString())).thenReturn(true);
        }
        Boolean result1 = mqAdminExtImpl.cleanUnusedTopic("DefaultCluster");
        Boolean result2 = mqAdminExtImpl.cleanUnusedTopic(brokerAddr);
        Assert.assertEquals(result1, true);
        Assert.assertEquals(result2, true);
    }

    @Test
    public void testViewBrokerStatsData() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.viewBrokerStatsData(anyString(), anyString(), anyString())).thenReturn(new BrokerStatsData());
        }
        BrokerStatsData brokerStatsData = mqAdminExtImpl.viewBrokerStatsData(brokerAddr, BrokerStatsManager.BROKER_ACK_NUMS, "topic_test");
        Assert.assertNotNull(brokerStatsData);
    }

    @Test
    public void testGetClusterList() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getClusterList(anyString())).thenReturn(new HashSet<>());
        }
        Set<String> clusterList = mqAdminExtImpl.getClusterList("topic_test");
        Assert.assertNotNull(clusterList);
    }

    @Test
    public void testFetchConsumeStatsInBroker() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.fetchConsumeStatsInBroker(anyString(), anyBoolean(), anyLong())).thenReturn(new ConsumeStatsList());
        }
        ConsumeStatsList consumeStatsList = mqAdminExtImpl.fetchConsumeStatsInBroker(brokerAddr, false, System.currentTimeMillis());
        Assert.assertNotNull(consumeStatsList);
    }

    @Test
    public void testGetTopicClusterList() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.fetchTopicsByCLuster(anyString())).thenReturn(new TopicList());
        }
        TopicList topicList = mqAdminExtImpl.fetchTopicsByCLuster("DefaultCluster");
        Assert.assertNotNull(topicList);
    }

    @Test
    public void testGetAllSubscriptionGroup() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.getAllSubscriptionGroup(anyString(), anyLong())).thenReturn(new SubscriptionGroupWrapper());
        }
        SubscriptionGroupWrapper wrapper = mqAdminExtImpl.getAllSubscriptionGroup(brokerAddr, 5000L);
        Assert.assertNotNull(wrapper);
    }

    @Test
    public void testUpdateConsumeOffset() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExt).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        }
        mqAdminExtImpl.updateConsumeOffset(brokerAddr, "group_test", new MessageQueue(), 10000L);
    }

    @Test
    public void testUpdateNameServerConfig() {
        assertNotNull(mqAdminExtImpl);
    }

    @Test
    public void testGetNameServerConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        assertNull(mqAdminExtImpl.getNameServerConfig(new ArrayList<>()));
    }

    @Test
    public void testQueryConsumeQueue() throws Exception {
        assertNotNull(mqAdminExtImpl);
        assertNull(mqAdminExtImpl.queryConsumeQueue(brokerAddr, "topic_test", 2, 1, 10, "group_test"));
    }

    @Test
    public void testResumeCheckHalfMessage() throws Exception {
        assertNotNull(mqAdminExtImpl);
        Assert.assertFalse(mqAdminExtImpl.resumeCheckHalfMessage("topic_test", "7F000001ACC018B4AAC2116AF6500000"));
    }

    @Test
    public void testAddWritePermOfBroker() throws Exception {
        assertNotNull(mqAdminExtImpl);
        {
            when(defaultMQAdminExt.addWritePermOfBroker(anyString(), anyString())).thenReturn(6);
        }
        Assert.assertEquals(mqAdminExtImpl.addWritePermOfBroker("127.0.0.1:9876", "broker-a"), 6);
    }

    @Test
    public void testGetUserSubscriptionGroup() throws Exception {
        assertNotNull(mqAdminExtImpl);
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        {
            when(defaultMQAdminExt.getUserSubscriptionGroup(anyString(), anyLong())).thenReturn(wrapper);
        }
        Assert.assertEquals(mqAdminExtImpl.getUserSubscriptionGroup("127.0.0.1:10911", 3000), wrapper);
    }

    @Test
    public void testGetAllTopicConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        TopicConfigSerializeWrapper wrapper = new TopicConfigSerializeWrapper();
        {
            when(defaultMQAdminExt.getAllTopicConfig(anyString(), anyLong())).thenReturn(wrapper);
        }
        Assert.assertEquals(mqAdminExtImpl.getAllTopicConfig("127.0.0.1:10911", 3000), wrapper);
    }

    @Test
    public void testGetUserTopicConfig() throws Exception {
        assertNotNull(mqAdminExtImpl);
        TopicConfigSerializeWrapper wrapper = new TopicConfigSerializeWrapper();
        {
            when(defaultMQAdminExt.getUserTopicConfig(anyString(), anyBoolean(), anyLong())).thenReturn(wrapper);
        }
        Assert.assertEquals(mqAdminExtImpl.getUserTopicConfig("127.0.0.1:10911", true, 3000), wrapper);
    }

    // ==================== Deprecated lifecycle methods ====================

    @Test(expected = IllegalStateException.class)
    public void testStartThrowsIllegalState() throws Exception {
        mqAdminExtImpl.start();
    }

    @Test(expected = IllegalStateException.class)
    public void testShutdownThrowsIllegalState() {
        mqAdminExtImpl.shutdown();
    }

    // ==================== examineSubscriptionGroupConfig / examineTopicConfig error branches ====================

    @Test
    public void testExamineSubscriptionGroupConfigErrorResponse() throws Exception {
        RemotingCommand errorResponse = RemotingCommand.createResponseCommand(null);
        errorResponse.setCode(ResponseCode.SYSTEM_ERROR);
        errorResponse.setRemark("system error");
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenReturn(errorResponse);
        try {
            mqAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "group_test");
            Assert.fail("Expected MQBrokerException");
        } catch (MQBrokerException e) {
            Assert.assertEquals(ResponseCode.SYSTEM_ERROR, e.getResponseCode());
        }
    }

    @Test
    public void testExamineSubscriptionGroupConfigUncheckedExceptionRethrown() throws Exception {
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenThrow(new RuntimeException("network down"));
        try {
            mqAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "group_test");
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertEquals("network down", e.getMessage());
        }
    }

    @Test
    public void testExamineSubscriptionGroupConfigCheckedExceptionWrapped() throws Exception {
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenThrow(new RemotingTimeoutException(brokerAddr));
        try {
            mqAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "group_test");
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getCause() instanceof RemotingTimeoutException);
        }
    }

    @Test
    public void testExamineTopicConfigErrorResponse() throws Exception {
        RemotingCommand errorResponse = RemotingCommand.createResponseCommand(null);
        errorResponse.setCode(ResponseCode.SYSTEM_ERROR);
        errorResponse.setRemark("system error");
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenReturn(errorResponse);
        try {
            mqAdminExtImpl.examineTopicConfig(brokerAddr, "topic_test");
            Assert.fail("Expected MQBrokerException");
        } catch (MQBrokerException e) {
            Assert.assertEquals(ResponseCode.SYSTEM_ERROR, e.getResponseCode());
        }
    }

    @Test
    public void testExamineTopicConfigUncheckedExceptionRethrown() throws Exception {
        when(remotingClient.invokeSync(eq(brokerAddr), any(RemotingCommand.class), anyLong()))
                .thenThrow(new RuntimeException("network down"));
        try {
            mqAdminExtImpl.examineTopicConfig(brokerAddr, "topic_test");
            Assert.fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            Assert.assertEquals("network down", e.getMessage());
        }
    }

    // ==================== Additional delegation methods ====================

    @Test
    public void testDeleteTopic() throws Exception {
        mqAdminExtImpl.deleteTopic("topic_test", "DefaultCluster");
        verify(defaultMQAdminExt).deleteTopic("topic_test", "DefaultCluster");
    }

    @Test
    public void testGetBrokerHAStatus() throws Exception {
        when(defaultMQAdminExt.getBrokerHAStatus(anyString())).thenReturn(new HARuntimeInfo());
        Assert.assertNotNull(mqAdminExtImpl.getBrokerHAStatus(brokerAddr));
    }

    @Test
    public void testExamineConsumeStatsWithBrokerAddr() throws Exception {
        when(defaultMQAdminExt.examineConsumeStats(anyString(), anyString(), anyString(), anyLong()))
                .thenReturn(new ConsumeStats());
        Assert.assertNotNull(mqAdminExtImpl.examineConsumeStats(brokerAddr, "group_test", "topic_test", 3000L));
    }

    @Test
    public void testExamineConsumerConnectionInfoWithBrokerAddr() throws Exception {
        when(defaultMQAdminExt.examineConsumerConnectionInfo(anyString(), anyString()))
                .thenReturn(new ConsumerConnection());
        Assert.assertNotNull(mqAdminExtImpl.examineConsumerConnectionInfo("group_test", brokerAddr));
    }

    @Test
    public void testGetTopicClusterListDelegation() throws Exception {
        when(defaultMQAdminExt.getTopicClusterList(anyString())).thenReturn(new HashSet<>());
        Assert.assertNotNull(mqAdminExtImpl.getTopicClusterList("topic_test"));
    }

    @Test
    public void testCleanUnusedTopicByAddr() throws Exception {
        when(defaultMQAdminExt.cleanUnusedTopicByAddr(anyString())).thenReturn(true);
        assertTrue(mqAdminExtImpl.cleanUnusedTopicByAddr(brokerAddr));
    }

    @Test
    public void testColdDataFlowCtrMethods() throws Exception {
        mqAdminExtImpl.updateColdDataFlowCtrGroupConfig(brokerAddr, new Properties());
        verify(defaultMQAdminExt).updateColdDataFlowCtrGroupConfig(eq(brokerAddr), any(Properties.class));

        mqAdminExtImpl.removeColdDataFlowCtrGroupConfig(brokerAddr, "group_test");
        verify(defaultMQAdminExt).removeColdDataFlowCtrGroupConfig(brokerAddr, "group_test");

        when(defaultMQAdminExt.getColdDataFlowCtrInfo(anyString())).thenReturn("info");
        Assert.assertEquals("info", mqAdminExtImpl.getColdDataFlowCtrInfo(brokerAddr));

        when(defaultMQAdminExt.setCommitLogReadAheadMode(anyString(), anyString())).thenReturn("ok");
        Assert.assertEquals("ok", mqAdminExtImpl.setCommitLogReadAheadMode(brokerAddr, "MADV_NORMAL"));
    }

    @Test
    public void testUserCrudDelegation() throws Exception {
        UserInfo userInfo = new UserInfo();
        mqAdminExtImpl.createUser(brokerAddr, userInfo);
        verify(defaultMQAdminExt).createUser(brokerAddr, userInfo);

        mqAdminExtImpl.createUser(brokerAddr, "user", "pwd", "Normal");
        verify(defaultMQAdminExt).createUser(brokerAddr, "user", "pwd", "Normal");

        mqAdminExtImpl.updateUser(brokerAddr, "user", "pwd", "Normal", "enable");
        verify(defaultMQAdminExt).updateUser(brokerAddr, "user", "pwd", "Normal", "enable");

        mqAdminExtImpl.updateUser(brokerAddr, userInfo);
        verify(defaultMQAdminExt).updateUser(brokerAddr, userInfo);

        mqAdminExtImpl.deleteUser(brokerAddr, "user");
        verify(defaultMQAdminExt).deleteUser(brokerAddr, "user");

        when(defaultMQAdminExt.getUser(anyString(), anyString())).thenReturn(new UserInfo());
        Assert.assertNotNull(mqAdminExtImpl.getUser(brokerAddr, "user"));

        when(defaultMQAdminExt.listUser(anyString(), anyString())).thenReturn(new ArrayList<>());
        Assert.assertNotNull(mqAdminExtImpl.listUser(brokerAddr, ""));
    }

    @Test
    public void testAclCrudDelegation() throws Exception {
        List<String> resources = Lists.newArrayList("Topic:topic_test");
        List<String> actions = Lists.newArrayList("Pub");
        List<String> sourceIps = Lists.newArrayList("192.168.1.1");
        AclInfo aclInfo = new AclInfo();

        mqAdminExtImpl.createAcl(brokerAddr, "User:user", resources, actions, sourceIps, "Allow");
        verify(defaultMQAdminExt).createAcl(brokerAddr, "User:user", resources, actions, sourceIps, "Allow");

        mqAdminExtImpl.createAcl(brokerAddr, aclInfo);
        verify(defaultMQAdminExt).createAcl(brokerAddr, aclInfo);

        mqAdminExtImpl.updateAcl(brokerAddr, "User:user", resources, actions, sourceIps, "Deny");
        verify(defaultMQAdminExt).updateAcl(brokerAddr, "User:user", resources, actions, sourceIps, "Deny");

        mqAdminExtImpl.updateAcl(brokerAddr, aclInfo);
        verify(defaultMQAdminExt).updateAcl(brokerAddr, aclInfo);

        mqAdminExtImpl.deleteAcl(brokerAddr, "User:user", "Topic:topic_test");
        verify(defaultMQAdminExt).deleteAcl(brokerAddr, "User:user", "Topic:topic_test");

        when(defaultMQAdminExt.getAcl(anyString(), anyString())).thenReturn(new AclInfo());
        Assert.assertNotNull(mqAdminExtImpl.getAcl(brokerAddr, "User:user"));

        when(defaultMQAdminExt.listAcl(anyString(), anyString(), anyString())).thenReturn(new ArrayList<>());
        Assert.assertNotNull(mqAdminExtImpl.listAcl(brokerAddr, "", ""));
    }

    @Test
    public void testExportPopRecords() throws Exception {
        mqAdminExtImpl.exportPopRecords(brokerAddr, 3000L);
        verify(defaultMQAdminExt).exportPopRecords(brokerAddr, 3000L);
    }

    // ==================== No-op / default-return methods ====================

    @Test
    public void testNoOpMethodsReturnDefaults() throws Exception {
        // void no-ops must not throw
        mqAdminExtImpl.createAndUpdateTopicConfigList(brokerAddr, new ArrayList<>());
        mqAdminExtImpl.createAndUpdateSubscriptionGroupConfigList(brokerAddr, new ArrayList<>());
        mqAdminExtImpl.updateNameServerConfig(new Properties(), new ArrayList<>());
        mqAdminExtImpl.exportRocksDBConfigToJson(brokerAddr, new ArrayList<>());

        assertNull(mqAdminExtImpl.checkRocksdbCqWriteProgress(brokerAddr, "topic_test", 0L));
        assertNull(mqAdminExtImpl.examineConsumeStats("a", "b", "c"));
        assertTrue(mqAdminExtImpl.resetOffsetByTimestamp("a", "b", "c", 0L, false).isEmpty());
        assertNull(mqAdminExtImpl.consumeMessageDirectly("a", "b", "c", "d", "e"));
        assertNull(mqAdminExtImpl.electMaster("ctrl", "cluster", "broker", 0L));
    }

    // ==================== Unsupported operations ====================

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private void assertUnsupported(ThrowingRunnable runnable) {
        try {
            runnable.run();
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // expected
        } catch (Exception e) {
            Assert.fail("Expected UnsupportedOperationException but got " + e.getClass().getSimpleName());
        }
    }

    @Test
    public void testUnsupportedContainerAndStatsOperations() {
        assertUnsupported(() -> mqAdminExtImpl.addBrokerToContainer(brokerAddr, "config"));
        assertUnsupported(() -> mqAdminExtImpl.removeBrokerFromContainer(brokerAddr, "DefaultCluster", "broker-a", 0L));
        assertUnsupported(() -> mqAdminExtImpl.examineTopicStats(brokerAddr, "topic_test"));
        assertUnsupported(() -> mqAdminExtImpl.examineTopicStatsConcurrent("topic_test"));
        assertUnsupported(() -> mqAdminExtImpl.examineConsumeStatsConcurrent("group_test", "topic_test"));
        assertUnsupported(() -> mqAdminExtImpl.getAllProducerInfo(brokerAddr));
        assertUnsupported(() -> mqAdminExtImpl.deleteTopicInBrokerConcurrent(Sets.newHashSet(brokerAddr), "topic_test"));
        assertUnsupported(() -> mqAdminExtImpl.deleteTopicInNameServer(Sets.newHashSet(brokerAddr), "DefaultCluster", "topic_test"));
        assertUnsupported(() -> mqAdminExtImpl.resetOffsetNewConcurrent("group_test", "topic_test", 0L));
        assertUnsupported(() -> mqAdminExtImpl.queryTopicsByConsumer("group_test"));
        assertUnsupported(() -> mqAdminExtImpl.queryTopicsByConsumerConcurrent("group_test"));
        assertUnsupported(() -> mqAdminExtImpl.querySubscription("group_test", "topic_test"));
        assertUnsupported(() -> mqAdminExtImpl.queryConsumeTimeSpanConcurrent("topic_test", "group_test"));
    }

    @Test
    public void testUnsupportedCommitLogAndOffsetOperations() {
        assertUnsupported(() -> mqAdminExtImpl.deleteExpiredCommitLog("DefaultCluster"));
        assertUnsupported(() -> mqAdminExtImpl.deleteExpiredCommitLogByAddr(brokerAddr));
        assertUnsupported(() -> mqAdminExtImpl.getConsumerRunningInfo("group_test", "clientId", true, true));
        assertUnsupported(() -> mqAdminExtImpl.messageTrackDetailConcurrent(new MessageExt()));
        assertUnsupported(() -> mqAdminExtImpl.setMessageRequestMode(brokerAddr, "topic_test", "group_test", MessageRequestMode.PULL, 8, 3000L));
        assertUnsupported(() -> mqAdminExtImpl.searchOffset(brokerAddr, "topic_test", 0, 0L, 3000L));
        assertUnsupported(() -> mqAdminExtImpl.resetOffsetByQueueId(brokerAddr, "group_test", "topic_test", 0, 0L));
        assertUnsupported(() -> mqAdminExtImpl.createStaticTopic(brokerAddr, "defaultTopic", new TopicConfig(), null, false));
        assertUnsupported(() -> mqAdminExtImpl.updateAndGetGroupReadForbidden(brokerAddr, "group_test", "topic_test", true));
        assertUnsupported(() -> mqAdminExtImpl.queryMessage("DefaultCluster", "topic_test", "msgId"));
    }

    @Test
    public void testUnsupportedControllerOperations() {
        assertUnsupported(() -> mqAdminExtImpl.getInSyncStateData("ctrl:9878", Lists.newArrayList(brokerAddr)));
        assertUnsupported(() -> mqAdminExtImpl.getBrokerEpochCache(brokerAddr));
        assertUnsupported(() -> mqAdminExtImpl.getControllerMetaData("ctrl:9878"));
        assertUnsupported(() -> mqAdminExtImpl.resetMasterFlushOffset(brokerAddr, 0L));
        assertUnsupported(() -> mqAdminExtImpl.getControllerConfig(Lists.newArrayList("ctrl:9878")));
        assertUnsupported(() -> mqAdminExtImpl.updateControllerConfig(new Properties(), Lists.newArrayList("ctrl:9878")));
        assertUnsupported(() -> mqAdminExtImpl.cleanControllerBrokerData("ctrl:9878", "DefaultCluster", "broker-a", brokerAddr, false));
    }
}
