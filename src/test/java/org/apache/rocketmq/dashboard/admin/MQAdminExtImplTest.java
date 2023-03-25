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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.commons.collections4.MapUtils;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.client.impl.MQClientAPIImpl;
import org.apache.rocketmq.client.impl.factory.MQClientInstance;
import org.apache.rocketmq.common.PlainAccessConfig;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.common.stats.Stats;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.RemotingClient;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.protocol.RemotingSerializable;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.remoting.protocol.admin.TopicStatsTable;
import org.apache.rocketmq.remoting.protocol.body.BrokerStatsData;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
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
import org.apache.rocketmq.tools.admin.DefaultMQAdminExtImpl;
import org.apache.rocketmq.tools.admin.api.MessageTrack;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

@RunWith(MockitoJUnitRunner.Silent.class)
public class MQAdminExtImplTest {

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

        ReflectionTestUtils.setField(defaultMQAdminExtImpl, "mqClientInstance", mqClientInstance);
        ReflectionTestUtils.setField(mqClientInstance, "mQClientAPIImpl", mQClientAPIImpl);
        ReflectionTestUtils.setField(mQClientAPIImpl, "remotingClient", remotingClient);
    }

    @Test
    public void testUpdateBrokerConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        doNothing()
            .doThrow(new MQBrokerException(0, ""))
            .when(defaultMQAdminExtImpl).updateBrokerConfig(anyString(), any());
        defaultMQAdminExtImpl.updateBrokerConfig(brokerAddr, new Properties());
        boolean hasException = false;
        try {
            defaultMQAdminExtImpl.updateBrokerConfig(brokerAddr, new Properties());
        } catch (Exception e) {
            hasException = true;
            assertThat(e).isInstanceOf(MQBrokerException.class);
            assertThat(((MQBrokerException) e).getResponseCode()).isEqualTo(0);
        }
        assertTrue(hasException);
    }

    @Test
    public void testCreateAndUpdateTopicConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        defaultMQAdminExtImpl.createAndUpdateTopicConfig(brokerAddr, new TopicConfig());
    }

    @Test
    public void testDeletePlainAccessConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        defaultMQAdminExtImpl.deletePlainAccessConfig(brokerAddr, "rocketmq");
    }

    @Test
    public void testUpdateGlobalWhiteAddrConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        defaultMQAdminExtImpl.updateGlobalWhiteAddrConfig(brokerAddr, "192.168.*.*");
    }

    @Test
    public void testCreateAndUpdatePlainAccessConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        defaultMQAdminExtImpl.createAndUpdatePlainAccessConfig(brokerAddr, new PlainAccessConfig());
    }

    @Test
    public void testExamineBrokerClusterAclVersionInfo() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        assertNull(defaultMQAdminExtImpl.examineBrokerClusterAclVersionInfo(brokerAddr));
    }

    @Test
    public void testExamineBrokerClusterAclConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        assertNull(defaultMQAdminExtImpl.examineBrokerClusterAclConfig(brokerAddr));
    }

    @Test
    public void testQueryConsumerStatus() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
    }

    @Test
    public void testCreateAndUpdateSubscriptionGroupConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        defaultMQAdminExtImpl.createAndUpdateSubscriptionGroupConfig(brokerAddr, new SubscriptionGroupConfig());
    }

    @Test
    public void testExamineSubscriptionGroupConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            RemotingCommand response1 = RemotingCommand.createResponseCommand(null);
            RemotingCommand response2 = RemotingCommand.createResponseCommand(null);
            response2.setCode(ResponseCode.SUCCESS);
            response2.setBody(RemotingSerializable.encode(MockObjectUtil.createSubscriptionGroupWrapper()));
            when(remotingClient.invokeSync(anyString(), any(), anyLong()))
                .thenThrow(new RuntimeException("invokeSync exception"))
                .thenReturn(response1).thenReturn(response2);
        }
        // invokeSync exception
        try {
            defaultMQAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "topic_test");
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "invokeSync exception");
        }

        // responseCode is not success
        try {
            defaultMQAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "group_test");
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(MQBrokerException.class);
            assertThat(((MQBrokerException) e.getCause()).getResponseCode()).isEqualTo(1);
        }
        // GET_ALL_SUBSCRIPTIONGROUP_CONFIG success
        SubscriptionGroupConfig subscriptionGroupConfig = defaultMQAdminExtImpl.examineSubscriptionGroupConfig(brokerAddr, "group_test");
        Assert.assertEquals(subscriptionGroupConfig.getGroupName(), "group_test");
    }

    @Test
    public void testExamineTopicConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            RemotingCommand response1 = RemotingCommand.createResponseCommand(null);
            RemotingCommand response2 = RemotingCommand.createResponseCommand(null);
            response2.setCode(ResponseCode.SUCCESS);
            response2.setBody(RemotingSerializable.encode(MockObjectUtil.createTopicConfigWrapper()));
            when(remotingClient.invokeSync(anyString(), any(), anyLong()))
                .thenThrow(new RuntimeException("invokeSync exception"))
                .thenReturn(response1).thenReturn(response2);
        }
        // invokeSync exception
        try {
            defaultMQAdminExtImpl.examineTopicConfig(brokerAddr, "topic_test");
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), "invokeSync exception");
        }
        // responseCode is not success
        try {
            defaultMQAdminExtImpl.examineTopicConfig(brokerAddr, "topic_test");
        } catch (Exception e) {
            assertThat(e.getCause()).isInstanceOf(MQBrokerException.class);
            assertThat(((MQBrokerException) e.getCause()).getResponseCode()).isEqualTo(1);
        }
        // GET_ALL_TOPIC_CONFIG success
        TopicConfig topicConfig = defaultMQAdminExtImpl.examineTopicConfig(brokerAddr, "topic_test");
        Assert.assertEquals(topicConfig.getTopicName(), "topic_test");
    }

    @Test
    public void testExamineTopicStats() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.examineTopicStats(anyString())).thenReturn(MockObjectUtil.createTopicStatsTable());
        }
        TopicStatsTable topicStatsTable = defaultMQAdminExtImpl.examineTopicStats("topic_test");
        Assert.assertNotNull(topicStatsTable);
        Assert.assertEquals(topicStatsTable.getOffsetTable().size(), 1);
    }

    @Test
    public void testExamineAllTopicConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);

    }

    @Test
    public void testFetchAllTopicList() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.fetchAllTopicList()).thenReturn(new TopicList());
        }
        TopicList topicList = defaultMQAdminExtImpl.fetchAllTopicList();
        Assert.assertNotNull(topicList);
    }

    @Test
    public void testFetchBrokerRuntimeStats() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.fetchBrokerRuntimeStats(anyString())).thenReturn(new KVTable());
        }
        KVTable kvTable = defaultMQAdminExtImpl.fetchBrokerRuntimeStats(brokerAddr);
        Assert.assertNotNull(kvTable);
    }

    @Test
    public void testExamineConsumeStats() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.examineConsumeStats(anyString())).thenReturn(MockObjectUtil.createConsumeStats());
            when(defaultMQAdminExtImpl.examineConsumeStats(anyString(), anyString())).thenReturn(MockObjectUtil.createConsumeStats());
        }
        ConsumeStats consumeStats = defaultMQAdminExtImpl.examineConsumeStats("group_test");
        ConsumeStats consumeStatsWithTopic = defaultMQAdminExtImpl.examineConsumeStats("group_test", "topic_test");
        Assert.assertNotNull(consumeStats);
        Assert.assertEquals(consumeStats.getOffsetTable().size(), 2);
        Assert.assertNotNull(consumeStatsWithTopic);
        Assert.assertEquals(consumeStatsWithTopic.getOffsetTable().size(), 2);
    }

    @Test
    public void testExamineBrokerClusterInfo() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.examineBrokerClusterInfo()).thenReturn(MockObjectUtil.createClusterInfo());
        }
        ClusterInfo clusterInfo = defaultMQAdminExtImpl.examineBrokerClusterInfo();
        Assert.assertNotNull(clusterInfo);
        Assert.assertEquals(clusterInfo.getBrokerAddrTable().size(), 1);
        Assert.assertEquals(clusterInfo.getClusterAddrTable().size(), 1);
    }

    @Test
    public void testExamineTopicRouteInfo() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.examineTopicRouteInfo(anyString())).thenReturn(MockObjectUtil.createTopicRouteData());
        }
        TopicRouteData topicRouteData = defaultMQAdminExtImpl.examineTopicRouteInfo("topic_test");
        Assert.assertNotNull(topicRouteData);
    }

    @Test
    public void testExamineConsumerConnectionInfo() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.examineConsumerConnectionInfo(anyString())).thenReturn(new ConsumerConnection());
        }
        ConsumerConnection consumerConnection = defaultMQAdminExtImpl.examineConsumerConnectionInfo("group_test");
        Assert.assertNotNull(consumerConnection);
    }

    @Test
    public void testExamineProducerConnectionInfo() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.examineProducerConnectionInfo(anyString(), anyString())).thenReturn(new ProducerConnection());
        }
        ProducerConnection producerConnection = defaultMQAdminExtImpl.examineProducerConnectionInfo("group_test", "topic_test");
        Assert.assertNotNull(producerConnection);
    }

    @Test
    public void testGetNameServerAddressList() {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getNameServerAddressList()).thenReturn(Lists.asList("127.0.0.1:9876", new String[] {"127.0.0.2:9876"}));
        }
        List<String> list = defaultMQAdminExtImpl.getNameServerAddressList();
        Assert.assertEquals(list.size(), 2);
    }

    @Test
    public void testWipeWritePermOfBroker() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.wipeWritePermOfBroker(anyString(), anyString())).thenReturn(6);
        }
        int result = defaultMQAdminExtImpl.wipeWritePermOfBroker("127.0.0.1:9876", "broker-a");
        Assert.assertEquals(result, 6);
    }

    @Test
    public void testPutKVConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).putKVConfig(anyString(), anyString(), anyString());
        }
        defaultMQAdminExtImpl.putKVConfig("namespace", "key", "value");
    }

    @Test
    public void testGetKVConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getKVConfig(anyString(), anyString())).thenReturn("value");
        }
        String value = defaultMQAdminExtImpl.getKVConfig("namespace", "key");
        Assert.assertEquals(value, "value");
    }

    @Test
    public void testGetKVListByNamespace() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getKVListByNamespace(anyString())).thenReturn(new KVTable());
        }
        KVTable kvTable = defaultMQAdminExtImpl.getKVListByNamespace("namespace");
        Assert.assertNotNull(kvTable);
    }

    @Test
    public void testDeleteTopicInBroker() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).deleteTopicInBroker(any(), anyString());
        }
        defaultMQAdminExtImpl.deleteTopicInBroker(Sets.newHashSet("127.0.0.1:10911"), "topic_test");
    }

    @Test
    public void testDeleteTopicInNameServer() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).deleteTopicInNameServer(any(), anyString());
        }
        defaultMQAdminExtImpl.deleteTopicInNameServer(Sets.newHashSet("127.0.0.1:9876", "127.0.0.2:9876"), "topic_test");
    }

    @Test
    public void testDeleteSubscriptionGroup() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).deleteSubscriptionGroup(anyString(), anyString());
            doNothing().when(defaultMQAdminExtImpl).deleteSubscriptionGroup(anyString(), anyString(), anyBoolean());
        }
        defaultMQAdminExtImpl.deleteSubscriptionGroup(brokerAddr, "group_test");
        defaultMQAdminExtImpl.deleteSubscriptionGroup(brokerAddr, "group_test", true);
    }

    @Test
    public void testCreateAndUpdateKvConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).createAndUpdateKvConfig(anyString(), anyString(), anyString());
        }
        defaultMQAdminExtImpl.createAndUpdateKvConfig("namespace", "key", "value");
    }

    @Test
    public void testDeleteKvConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).deleteKvConfig(anyString(), anyString());
        }
        defaultMQAdminExtImpl.deleteKvConfig("namespace", "key");
    }

    @Test
    public void testDeleteConsumerOffset() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
    }

    @Test
    public void testResetOffsetByTimestampOld() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.resetOffsetByTimestampOld(anyString(), anyString(), anyLong(), anyBoolean())).thenReturn(new ArrayList<RollbackStats>());
        }
        List<RollbackStats> stats = defaultMQAdminExtImpl.resetOffsetByTimestampOld("group_test", "topic_test", 1628495765398L, false);
        Assert.assertNotNull(stats);
    }

    @Test
    public void testResetOffsetByTimestamp() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.resetOffsetByTimestamp(anyString(), anyString(), anyLong(), anyBoolean())).thenReturn(new HashMap<MessageQueue, Long>());
        }
        Map<MessageQueue, Long> map = defaultMQAdminExtImpl.resetOffsetByTimestamp("group_test", "topic_test", 1628495765398L, false);
        Assert.assertNotNull(map);
    }

    @Test
    public void testResetOffsetNew() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).resetOffsetNew(anyString(), anyString(), anyLong());
        }
        defaultMQAdminExtImpl.resetOffsetNew("group_test", "topic_test", 1628495765398L);
    }

    @Test
    public void testGetConsumeStatus() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getConsumeStatus(anyString(), anyString(), anyString())).thenReturn(new HashMap<String, Map<MessageQueue, Long>>());
        }
        defaultMQAdminExtImpl.getConsumeStatus("topic_test", "group_test", "");
    }

    @Test
    public void testCreateOrUpdateOrderConf() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).createOrUpdateOrderConf(anyString(), anyString(), anyBoolean());
        }
        defaultMQAdminExtImpl.createOrUpdateOrderConf("key", "value", false);
    }

    @Test
    public void testQueryTopicConsumeByWho() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.queryTopicConsumeByWho(anyString())).thenReturn(new GroupList());
        }
        GroupList groupList = defaultMQAdminExtImpl.queryTopicConsumeByWho("topic_test");
        Assert.assertNotNull(groupList);
    }

    @Test
    public void testCleanExpiredConsumerQueue() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.cleanExpiredConsumerQueue(anyString())).thenReturn(true);
        }
        boolean result = defaultMQAdminExtImpl.cleanExpiredConsumerQueue("DefaultCluster");
        Assert.assertEquals(result, true);
    }

    @Test
    public void testCleanExpiredConsumerQueueByAddr() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.cleanExpiredConsumerQueueByAddr(anyString())).thenReturn(true);
        }
        boolean result = defaultMQAdminExtImpl.cleanExpiredConsumerQueueByAddr("DefaultCluster");
        Assert.assertEquals(result, true);
    }

    @Test
    public void testGetConsumerRunningInfo() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getConsumerRunningInfo(anyString(), anyString(), anyBoolean())).thenReturn(new ConsumerRunningInfo());
        }
        ConsumerRunningInfo consumerRunningInfo = defaultMQAdminExtImpl.getConsumerRunningInfo("group_test", "", true);
        Assert.assertNotNull(consumerRunningInfo);
    }

    @Test
    public void testConsumeMessageDirectly() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.consumeMessageDirectly(anyString(), anyString(), anyString())).thenReturn(new ConsumeMessageDirectlyResult());
            when(defaultMQAdminExtImpl.consumeMessageDirectly(anyString(), anyString(), anyString(), anyString())).thenReturn(new ConsumeMessageDirectlyResult());
        }
        ConsumeMessageDirectlyResult result1 = defaultMQAdminExtImpl.consumeMessageDirectly("group_test", "", "7F000001ACC018B4AAC2116AF6500000");
        ConsumeMessageDirectlyResult result2 = defaultMQAdminExtImpl.consumeMessageDirectly("group_test", "", "topic_test", "7F000001ACC018B4AAC2116AF6500000");
        Assert.assertNotNull(result1);
        Assert.assertNotNull(result2);
    }

    @Test
    public void testMessageTrackDetail() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.messageTrackDetail(any())).thenReturn(new ArrayList<MessageTrack>());
        }
        List<MessageTrack> tracks = defaultMQAdminExtImpl.messageTrackDetail(new MessageExt());
        Assert.assertNotNull(tracks);
    }

    @Test
    public void testCloneGroupOffset() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).cloneGroupOffset(anyString(), anyString(), anyString(), anyBoolean());
        }
        defaultMQAdminExtImpl.cloneGroupOffset("group_test", "group_test1", "topic_test", false);
    }

    @Test
    public void testCreateTopic() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).createTopic(anyString(), anyString(), anyInt(), ArgumentMatchers.anyMap());
            doNothing().when(defaultMQAdminExtImpl).createTopic(anyString(), anyString(), anyInt(), anyInt(), ArgumentMatchers.anyMap());
        }
        defaultMQAdminExtImpl.createTopic("key", "topic_test", 8, new HashMap<>());
        defaultMQAdminExtImpl.createTopic("key", "topic_test", 8, 1, new HashMap<>());
    }

    @Test
    public void testSearchOffset() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.searchOffset(any(), anyLong())).thenReturn(Long.MAX_VALUE);
        }
        long offset = defaultMQAdminExtImpl.searchOffset(new MessageQueue(), 1628495765398L);
        Assert.assertEquals(offset, Long.MAX_VALUE);
    }

    @Test
    public void testMaxOffset() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.maxOffset(any())).thenReturn(Long.MAX_VALUE);
        }
        long offset = defaultMQAdminExtImpl.maxOffset(new MessageQueue());
        Assert.assertEquals(offset, Long.MAX_VALUE);
    }

    @Test
    public void testMinOffset() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.minOffset(any())).thenReturn(Long.MIN_VALUE);
        }
        long offset = defaultMQAdminExtImpl.minOffset(new MessageQueue());
        Assert.assertEquals(offset, Long.MIN_VALUE);
    }

    @Test
    public void testEarliestMsgStoreTime() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.earliestMsgStoreTime(any())).thenReturn(1628495765398L);
        }
        long storeTime = defaultMQAdminExtImpl.earliestMsgStoreTime(new MessageQueue());
        Assert.assertEquals(storeTime, 1628495765398L);
    }

    @Test
    public void testViewMessage() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.viewMessage(anyString())).thenReturn(new MessageExt());
        }
        MessageExt messageExt = defaultMQAdminExtImpl.viewMessage("7F000001ACC018B4AAC2116AF6500000");
        Assert.assertNotNull(messageExt);
    }

    @Test
    public void testQueryMessage() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong())).thenReturn(mock(QueryResult.class));
        }
        QueryResult result = defaultMQAdminExtImpl.queryMessage("topic_test", "key", 32, 1627804565000L, System.currentTimeMillis());
        Assert.assertNotNull(result);
    }

    @Test
    public void testStart() {
        assertNotNull(defaultMQAdminExtImpl);
        try {
            defaultMQAdminExtImpl.start();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void testShutdown() {
        assertNotNull(defaultMQAdminExtImpl);
        try {
            defaultMQAdminExtImpl.shutdown();
        } catch (Exception e) {
            Assert.assertTrue(e instanceof IllegalStateException);
        }
    }

    @Test
    public void testQueryConsumeTimeSpan() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.queryConsumeTimeSpan(anyString(), anyString())).thenReturn(new ArrayList<QueueTimeSpan>());
        }
        List<QueueTimeSpan> timeSpans = defaultMQAdminExtImpl.queryConsumeTimeSpan("topic_test", "group_test");
        Assert.assertNotNull(timeSpans);
    }

    @Test
    public void testViewMessage2() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.viewMessage(anyString())).thenThrow(new RuntimeException("viewMessage exception"));
        }
        defaultMQAdminExtImpl.viewMessage("topic_test", "7F000001ACC018B4AAC2116AF6500000");
    }

    @Test
    public void testGetBrokerConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getBrokerConfig(anyString())).thenReturn(new Properties());
        }
        Properties brokerConfig = defaultMQAdminExtImpl.getBrokerConfig(brokerAddr);
        Assert.assertNotNull(brokerConfig);
    }

    @Test
    public void testFetchTopicsByCLuster() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.fetchTopicsByCLuster(anyString())).thenReturn(new TopicList());
        }
        TopicList topicList = defaultMQAdminExtImpl.fetchTopicsByCLuster("DefaultCluster");
        Assert.assertNotNull(topicList);
    }

    @Test
    public void testCleanUnusedTopic() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.cleanUnusedTopic(anyString())).thenReturn(true);
            when(defaultMQAdminExtImpl.cleanUnusedTopicByAddr(anyString())).thenReturn(true);
        }
        Boolean result1 = defaultMQAdminExtImpl.cleanUnusedTopic("DefaultCluster");
        Boolean result2 = defaultMQAdminExtImpl.cleanUnusedTopic(brokerAddr);
        Assert.assertEquals(result1, true);
        Assert.assertEquals(result2, true);
    }

    @Test
    public void testViewBrokerStatsData() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.viewBrokerStatsData(anyString(), anyString(), anyString())).thenReturn(new BrokerStatsData());
        }
        BrokerStatsData brokerStatsData = defaultMQAdminExtImpl.viewBrokerStatsData(brokerAddr, Stats.TOPIC_PUT_NUMS, "topic_test");
        Assert.assertNotNull(brokerStatsData);
    }

    @Test
    public void testGetClusterList() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getClusterList(anyString())).thenReturn(new HashSet<>());
        }
        Set<String> clusterList = defaultMQAdminExtImpl.getClusterList("topic_test");
        Assert.assertNotNull(clusterList);
    }

    @Test
    public void testFetchConsumeStatsInBroker() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.fetchConsumeStatsInBroker(anyString(), anyBoolean(), anyLong())).thenReturn(new ConsumeStatsList());
        }
        ConsumeStatsList consumeStatsList = defaultMQAdminExtImpl.fetchConsumeStatsInBroker(brokerAddr, false, System.currentTimeMillis());
        Assert.assertNotNull(consumeStatsList);
    }

    @Test
    public void testGetTopicClusterList() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.fetchTopicsByCLuster(anyString())).thenReturn(new TopicList());
        }
        TopicList topicList = defaultMQAdminExtImpl.fetchTopicsByCLuster("DefaultCluster");
        Assert.assertNotNull(topicList);
    }

    @Test
    public void testGetAllSubscriptionGroup() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.getAllSubscriptionGroup(anyString(), anyLong())).thenReturn(new SubscriptionGroupWrapper());
        }
        SubscriptionGroupWrapper wrapper = defaultMQAdminExtImpl.getAllSubscriptionGroup(brokerAddr, 5000L);
        Assert.assertNotNull(wrapper);
    }

    @Test
    public void testUpdateConsumeOffset() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            doNothing().when(defaultMQAdminExtImpl).updateConsumeOffset(anyString(), anyString(), any(), anyLong());
        }
        defaultMQAdminExtImpl.updateConsumeOffset(brokerAddr, "group_test", new MessageQueue(), 10000L);
    }

    @Test
    public void testUpdateNameServerConfig() {
        assertNotNull(defaultMQAdminExtImpl);
    }

    @Test
    public void testGetNameServerConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        assertTrue(MapUtils.isEmpty(defaultMQAdminExtImpl.getNameServerConfig(new ArrayList<>())));
    }

    @Test
    public void testQueryConsumeQueue() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        assertNull(defaultMQAdminExtImpl.queryConsumeQueue(brokerAddr, "topic_test", 2, 1, 10, "group_test"));
    }

    @Test
    public void testResumeCheckHalfMessage() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        Assert.assertFalse(defaultMQAdminExtImpl.resumeCheckHalfMessage("7F000001ACC018B4AAC2116AF6500000"));
        Assert.assertFalse(defaultMQAdminExtImpl.resumeCheckHalfMessage("topic_test", "7F000001ACC018B4AAC2116AF6500000"));
    }

    @Test
    public void testAddWritePermOfBroker() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        {
            when(defaultMQAdminExtImpl.addWritePermOfBroker(anyString(), anyString())).thenReturn(6);
        }
        Assert.assertEquals(defaultMQAdminExtImpl.addWritePermOfBroker("127.0.0.1:9876", "broker-a"), 6);
    }

    @Test
    public void testGetUserSubscriptionGroup() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        {
            when(defaultMQAdminExtImpl.getUserSubscriptionGroup(anyString(), anyLong())).thenReturn(wrapper);
        }
        Assert.assertEquals(defaultMQAdminExtImpl.getUserSubscriptionGroup("127.0.0.1:10911", 3000), wrapper);
    }

    @Test
    public void testGetAllTopicConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        TopicConfigSerializeWrapper wrapper = new TopicConfigSerializeWrapper();
        {
            when(defaultMQAdminExtImpl.getAllTopicConfig(anyString(), anyLong())).thenReturn(wrapper);
        }
        Assert.assertEquals(defaultMQAdminExtImpl.getAllTopicConfig("127.0.0.1:10911", 3000), wrapper);
    }

    @Test
    public void testGetUserTopicConfig() throws Exception {
        assertNotNull(defaultMQAdminExtImpl);
        TopicConfigSerializeWrapper wrapper = new TopicConfigSerializeWrapper();
        {
            when(defaultMQAdminExtImpl.getUserTopicConfig(anyString(), anyBoolean(), anyLong())).thenReturn(wrapper);
        }
        Assert.assertEquals(defaultMQAdminExtImpl.getUserTopicConfig("127.0.0.1:10911", true, 3000), wrapper);
    }
}
