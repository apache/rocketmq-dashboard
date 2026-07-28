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
package org.apache.rocketmq.dashboard.cli.executor;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.cli.AbstractCliTest;
import org.apache.rocketmq.dashboard.cli.context.AdminClientHelper;
import org.apache.rocketmq.dashboard.cli.schema.ParamSchema;
import org.apache.rocketmq.dashboard.cli.schema.ToolDefinition;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.KVTable;
import org.apache.rocketmq.remoting.protocol.body.SubscriptionGroupWrapper;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/**
 * Unit tests for {@link ToolExecutor}. Only branches that do not require a
 * live RocketMQ connection are exercised: pre-connection guards inside
 * {@code execute()}, argument parsing/validation helpers, result converters,
 * and tool implementations invoked with a mocked {@link AdminClientHelper}.
 */
public class ToolExecutorTest extends AbstractCliTest {

    private final ToolExecutor executor = new ToolExecutor();

    @Before
    public void setUp() throws Exception {
        resetConfig();
    }

    // ---- reflection helpers -----------------------------------------------------

    private Object invokeExecutor(String method, Class<?>[] signature, Object... args) throws Exception {
        Method m = ToolExecutor.class.getDeclaredMethod(method, signature);
        m.setAccessible(true);
        try {
            return m.invoke(executor, args);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception) {
                throw (Exception) e.getCause();
            }
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return (Map<String, Object>) value;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> asList(Object value) {
        return (List<Object>) value;
    }

    /**
     * Build a real AdminClientHelper around a mocked MQAdminExt via its private
     * constructor. Mockito-inline 3.3.3 cannot instrument project classes
     * compiled for a newer JVM, so the helper itself is never mocked.
     */
    private static AdminClientHelper newAdmin(MQAdminExt ext) throws Exception {
        Constructor<AdminClientHelper> ctor = AdminClientHelper.class
                .getDeclaredConstructor(MQAdminExt.class, String.class, String.class);
        ctor.setAccessible(true);
        return ctor.newInstance(ext, "DefaultCluster", "127.0.0.1:9876");
    }

    private static ToolDefinition tool(String name, String resource, String verb) {
        return ToolDefinition.builder().name(name).resource(resource).verb(verb).build();
    }

    private static ClusterInfo singleBrokerClusterInfo() {
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "127.0.0.1:10911");
        addrs.put(1L, "127.0.0.1:10912");
        BrokerData bd = new BrokerData("DefaultCluster", "broker-a", addrs);
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", bd);
        HashMap<String, Set<String>> clusterAddrTable = new HashMap<>();
        clusterAddrTable.put("DefaultCluster", new HashSet<>(List.of("broker-a")));
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        clusterInfo.setClusterAddrTable(clusterAddrTable);
        return clusterInfo;
    }

    private static ClusterInfo twoBrokerClusterInfo() {
        HashMap<Long, String> addrsA = new HashMap<>();
        addrsA.put(0L, "127.0.0.1:10911");
        HashMap<Long, String> addrsB = new HashMap<>();
        addrsB.put(0L, "127.0.0.1:10921");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", new BrokerData("DefaultCluster", "broker-a", addrsA));
        brokerAddrTable.put("broker-b", new BrokerData("DefaultCluster", "broker-b", addrsB));
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        return clusterInfo;
    }

    // ---- execute() guards (no connection is opened) -------------------------------

    @Test
    public void testExecuteRejectsNamespaceTools() throws Exception {
        ToolDefinition namespaceTool = tool("rmq.namespace.list", "namespace", "list");
        try {
            executor.execute(namespaceTool, new LinkedHashMap<>());
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertTrue(e.getMessage().contains("Namespace operations are not supported"));
        }
    }

    @Test
    public void testExecuteRejectsNamespaceToolsWithNullArguments() throws Exception {
        ToolDefinition namespaceTool = tool("rmq.namespace.delete", "namespace", "delete");
        try {
            executor.execute(namespaceTool, null);
            Assert.fail("Expected UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            Assert.assertTrue(e.getMessage().contains("Namespace operations are not supported"));
        }
    }

    @Test
    public void testExecuteFailsWhenClusterNotConfigured() throws Exception {
        // No colon in the cluster argument -> resolved as a cluster name from the
        // CLI configuration, which is empty here, so resolution fails offline.
        ToolDefinition topicList = tool("rmq.topic.list", "topic", "list");
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("cluster", "no-such-cluster");
        try {
            executor.execute(topicList, args);
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("not found in configuration"));
        }
    }

    @Test
    public void testExecuteFailsWithoutClusterOrContext() throws Exception {
        ToolDefinition topicList = tool("rmq.topic.list", "topic", "list");
        try {
            executor.execute(topicList, new LinkedHashMap<>());
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No cluster specified"));
        }
    }

    @Test
    public void testExecuteValidatesRequiredArgumentsBeforeConnecting() throws Exception {
        // "topic" is declared required; a missing value must fail fast with
        // IllegalArgumentException instead of the connection-time
        // IllegalStateException (no cluster is configured in this test).
        ToolDefinition topicDescribe = tool("rmq.topic.describe", "topic", "describe");
        topicDescribe.setParams(List.of(
                ParamSchema.builder().name("cluster").type("STRING").required(true).build(),
                ParamSchema.builder().name("topic").type("STRING").required(true).build()));
        try {
            executor.execute(topicDescribe, new LinkedHashMap<>());
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Missing required argument: topic"));
        }
    }

    @Test
    public void testExecuteSkipsClusterInRequiredArgumentValidation() throws Exception {
        // "cluster" is exempt from pre-connection validation because it may be
        // resolved from the CLI context; with all other required args present
        // the failure must come from cluster resolution, not validation.
        ToolDefinition topicDescribe = tool("rmq.topic.describe", "topic", "describe");
        topicDescribe.setParams(List.of(
                ParamSchema.builder().name("cluster").type("STRING").required(true).build(),
                ParamSchema.builder().name("topic").type("STRING").required(true).build()));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "TopicA");
        try {
            executor.execute(topicDescribe, args);
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No cluster specified"));
        }
    }

    @Test
    public void testExamineTopicConfigProbesAllMastersOnNull() throws Exception {
        // A broker that does not host the topic may return null instead of
        // throwing; the helper must keep probing the remaining masters.
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        HashMap<Long, String> addrsA = new HashMap<>();
        addrsA.put(0L, "127.0.0.1:10911");
        HashMap<Long, String> addrsB = new HashMap<>();
        addrsB.put(0L, "127.0.0.1:10921");
        HashMap<String, BrokerData> brokerAddrTable = new HashMap<>();
        brokerAddrTable.put("broker-a", new BrokerData("DefaultCluster", "broker-a", addrsA));
        brokerAddrTable.put("broker-b", new BrokerData("DefaultCluster", "broker-b", addrsB));
        ClusterInfo clusterInfo = new ClusterInfo();
        clusterInfo.setBrokerAddrTable(brokerAddrTable);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(clusterInfo);

        TopicConfig config = new TopicConfig("TopicA");
        Mockito.when(ext.examineTopicConfig("127.0.0.1:10911", "TopicA")).thenReturn(null);
        Mockito.when(ext.examineTopicConfig("127.0.0.1:10921", "TopicA")).thenReturn(config);

        AdminClientHelper admin = newAdmin(ext);
        Assert.assertSame(config, admin.examineTopicConfig("TopicA"));
    }

    // ---- argument helpers ----------------------------------------------------------

    @Test
    public void testGetStringConvertsAndHandlesMissing() throws Exception {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "orders");
        args.put("count", 5);
        Class<?>[] sig = {Map.class, String.class};
        Assert.assertEquals("orders", invokeExecutor("getString", sig, args, "topic"));
        Assert.assertEquals("5", invokeExecutor("getString", sig, args, "count"));
        Assert.assertNull(invokeExecutor("getString", sig, args, "absent"));
    }

    @Test
    public void testRequireStringThrowsWhenMissingOrEmpty() throws Exception {
        Class<?>[] sig = {Map.class, String.class};
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("group", "");
        try {
            invokeExecutor("requireString", sig, args, "topic");
            Assert.fail("Expected IllegalArgumentException for missing key");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Missing required argument: topic", e.getMessage());
        }
        try {
            invokeExecutor("requireString", sig, args, "group");
            Assert.fail("Expected IllegalArgumentException for empty value");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Missing required argument: group", e.getMessage());
        }
        args.put("topic", "orders");
        Assert.assertEquals("orders", invokeExecutor("requireString", sig, args, "topic"));
    }

    @Test
    public void testGetIntParsesNumbersAndStrings() throws Exception {
        Class<?>[] sig = {Map.class, String.class};
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("fromNumber", 8L);
        args.put("fromString", "16");
        args.put("invalid", "not-a-number");
        Assert.assertEquals(8, invokeExecutor("getInt", sig, args, "fromNumber"));
        Assert.assertEquals(16, invokeExecutor("getInt", sig, args, "fromString"));
        Assert.assertNull(invokeExecutor("getInt", sig, args, "absent"));
        try {
            invokeExecutor("getInt", sig, args, "invalid");
            Assert.fail("Expected NumberFormatException");
        } catch (NumberFormatException expected) {
            // parsing failure is propagated to the caller
        }
    }

    @Test
    public void testGetLongParsesNumbersAndStrings() throws Exception {
        Class<?>[] sig = {Map.class, String.class};
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("fromNumber", 42);
        args.put("fromString", "1700000000000");
        Assert.assertEquals(42L, invokeExecutor("getLong", sig, args, "fromNumber"));
        Assert.assertEquals(1700000000000L, invokeExecutor("getLong", sig, args, "fromString"));
        Assert.assertNull(invokeExecutor("getLong", sig, args, "absent"));
    }

    @Test
    public void testRequireLongThrowsWhenMissing() throws Exception {
        Class<?>[] sig = {Map.class, String.class};
        Map<String, Object> args = new LinkedHashMap<>();
        try {
            invokeExecutor("requireLong", sig, args, "timestamp");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Missing required argument: timestamp", e.getMessage());
        }
        args.put("timestamp", "123");
        Assert.assertEquals(123L, invokeExecutor("requireLong", sig, args, "timestamp"));
    }

    @Test
    public void testToAclSubjectAddsUserPrefix() throws Exception {
        Class<?>[] sig = {String.class};
        Assert.assertEquals("User:alice", invokeExecutor("toAclSubject", sig, "alice"));
        Assert.assertEquals("User:bob", invokeExecutor("toAclSubject", sig, "User:bob"));
    }

    @Test
    public void testSplitActionsHandlesDelimitersAndBlanks() throws Exception {
        Class<?>[] sig = {String.class};
        Assert.assertEquals(List.of("PUB", "SUB", "ADMIN"),
                invokeExecutor("splitActions", sig, "PUB|SUB, ADMIN"));
        Assert.assertEquals(List.of("PUB"), invokeExecutor("splitActions", sig, "PUB,,|"));
    }

    // ---- converters ------------------------------------------------------------------

    @Test
    public void testMessageToMapMapsCoreFields() throws Exception {
        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG-1");
        msg.setTopic("orders");
        msg.setTags("tagA");
        msg.setKeys("key1");
        msg.setQueueId(2);
        msg.setQueueOffset(10L);
        msg.setReconsumeTimes(1);
        msg.setBornTimestamp(1000L);
        msg.setStoreTimestamp(2000L);
        msg.setBody("hello".getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = asMap(invokeExecutor("messageToMap",
                new Class<?>[] {MessageExt.class}, msg));
        Assert.assertEquals("MSG-1", result.get("msgId"));
        Assert.assertEquals("orders", result.get("topic"));
        Assert.assertEquals("tagA", result.get("tags"));
        Assert.assertEquals("key1", result.get("keys"));
        Assert.assertEquals(2, result.get("queueId"));
        Assert.assertEquals(10L, result.get("queueOffset"));
        Assert.assertEquals(1, result.get("reconsumeTimes"));
        Assert.assertEquals("hello", result.get("body"));
        Assert.assertEquals(5, result.get("bodyLength"));
    }

    @Test
    public void testMessageToMapTruncatesLargeBody() throws Exception {
        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG-2");
        msg.setTopic("orders");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 5000; i++) {
            sb.append('a');
        }
        msg.setBody(sb.toString().getBytes(StandardCharsets.UTF_8));

        Map<String, Object> result = asMap(invokeExecutor("messageToMap",
                new Class<?>[] {MessageExt.class}, msg));
        String body = (String) result.get("body");
        Assert.assertTrue(body.endsWith("...(truncated)"));
        Assert.assertEquals(4096 + "...(truncated)".length(), body.length());
        Assert.assertEquals(5000, result.get("bodyLength"));
    }

    @Test
    public void testMessageToMapWithoutBodyOmitsBodyFields() throws Exception {
        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG-3");
        msg.setTopic("orders");

        Map<String, Object> result = asMap(invokeExecutor("messageToMap",
                new Class<?>[] {MessageExt.class}, msg));
        Assert.assertFalse(result.containsKey("body"));
        Assert.assertFalse(result.containsKey("bodyLength"));
    }

    @Test
    public void testBrokerDataToMapLabelsMasterAndSlaves() throws Exception {
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "127.0.0.1:10911");
        addrs.put(2L, "127.0.0.1:10921");
        BrokerData bd = new BrokerData("DefaultCluster", "broker-a", addrs);

        Map<String, Object> result = asMap(invokeExecutor("brokerDataToMap",
                new Class<?>[] {BrokerData.class}, bd));
        Assert.assertEquals("broker-a", result.get("brokerName"));
        Assert.assertEquals("DefaultCluster", result.get("cluster"));
        Map<String, Object> addresses = asMap(result.get("addresses"));
        Assert.assertEquals("127.0.0.1:10911", addresses.get("master"));
        Assert.assertEquals("127.0.0.1:10921", addresses.get("slave-2"));
    }

    @Test
    public void testGroupConfigToMapConsumeModes() throws Exception {
        SubscriptionGroupConfig cfg = new SubscriptionGroupConfig();
        cfg.setGroupName("group-a");
        cfg.setConsumeBroadcastEnable(true);
        cfg.setRetryMaxTimes(7);

        Map<String, Object> broadcast = asMap(invokeExecutor("groupConfigToMap",
                new Class<?>[] {SubscriptionGroupConfig.class}, cfg));
        Assert.assertEquals("group-a", broadcast.get("group"));
        Assert.assertEquals("BROADCAST", broadcast.get("consumeMode"));
        Assert.assertEquals(7, broadcast.get("retryMaxTimes"));

        cfg.setConsumeBroadcastEnable(false);
        Map<String, Object> cluster = asMap(invokeExecutor("groupConfigToMap",
                new Class<?>[] {SubscriptionGroupConfig.class}, cfg));
        Assert.assertEquals("CLUSTER", cluster.get("consumeMode"));
    }

    @Test
    public void testConnectionToMapMapsClientFields() throws Exception {
        Connection c = new Connection();
        c.setClientId("client-1");
        c.setClientAddr("10.0.0.1:5000");
        c.setLanguage(LanguageCode.JAVA);
        c.setVersion(401);

        Map<String, Object> result = asMap(invokeExecutor("connectionToMap",
                new Class<?>[] {Connection.class, String.class}, c, "group-a"));
        Assert.assertEquals("client-1", result.get("clientId"));
        Assert.assertEquals("10.0.0.1:5000", result.get("clientAddr"));
        Assert.assertEquals("JAVA", result.get("language"));
        Assert.assertEquals(401, result.get("version"));
        Assert.assertEquals("group-a", result.get("group"));
    }

    @Test
    public void testBrokerTableToListHandlesNullAndPopulatedTables() throws Exception {
        Class<?>[] sig = {ClusterInfo.class};
        Assert.assertTrue(asList(invokeExecutor("brokerTableToList", sig, (ClusterInfo) null)).isEmpty());
        Assert.assertTrue(asList(invokeExecutor("brokerTableToList", sig, new ClusterInfo())).isEmpty());

        List<Object> brokers = asList(invokeExecutor("brokerTableToList", sig, singleBrokerClusterInfo()));
        Assert.assertEquals(1, brokers.size());
        Assert.assertEquals("broker-a", asMap(brokers.get(0)).get("brokerName"));
    }

    // ---- resolveBrokerAddr -------------------------------------------------------------

    @Test
    public void testResolveBrokerAddrFromClusterInfo() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);
        Object addr = invokeExecutor("resolveBrokerAddr",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "broker-a");
        Assert.assertEquals("127.0.0.1:10911", addr);
    }

    @Test
    public void testResolveBrokerAddrAcceptsRawAddress() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(new ClusterInfo());
        AdminClientHelper admin = newAdmin(ext);
        Object addr = invokeExecutor("resolveBrokerAddr",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "10.1.1.1:10911");
        Assert.assertEquals("10.1.1.1:10911", addr);
    }

    @Test
    public void testResolveBrokerAddrThrowsWhenUnknown() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);
        try {
            invokeExecutor("resolveBrokerAddr",
                    new Class<?>[] {AdminClientHelper.class, String.class}, admin, "broker-x");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Broker not found in cluster: broker-x"));
        }
    }

    // ---- cluster / capabilities ----------------------------------------------------------

    @Test
    public void testDescribeClusterMapsClusterInfo() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> result = asMap(invokeExecutor("describeCluster",
                new Class<?>[] {AdminClientHelper.class}, admin));
        List<Object> clusters = asList(result.get("clusters"));
        Assert.assertEquals(1, clusters.size());
        Assert.assertEquals("DefaultCluster", asMap(clusters.get(0)).get("clusterName"));
        Assert.assertEquals(1, asList(result.get("brokers")).size());
        Assert.assertEquals("127.0.0.1:9876", result.get("namesrvAddr"));
    }

    @Test
    public void testDetectCapabilitiesSummarizesCluster() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> result = asMap(invokeExecutor("detectCapabilities",
                new Class<?>[] {AdminClientHelper.class}, admin));
        Assert.assertEquals("127.0.0.1:9876", result.get("namesrvAddr"));
        Assert.assertEquals(List.of("DefaultCluster"), result.get("clusterNames"));
        Assert.assertEquals(1, result.get("brokerCount"));
        Assert.assertEquals(1, asList(result.get("brokers")).size());
    }

    // ---- topic -----------------------------------------------------------------------------

    @Test
    public void testListTopicsSortedAndDeduplicated() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        TopicList topicList = new TopicList();
        topicList.setTopicList(new LinkedHashSet<>(List.of("zeta", "alpha", "mid")));
        Mockito.when(ext.fetchAllTopicList()).thenReturn(topicList);
        AdminClientHelper admin = newAdmin(ext);

        List<Object> topics = asList(invokeExecutor("listTopics",
                new Class<?>[] {AdminClientHelper.class}, admin));
        Assert.assertEquals(List.of("alpha", "mid", "zeta"), topics);
    }

    @Test
    public void testDescribeTopicMapsRouteData() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        // no cluster info stubbed -> examineTopicConfig finds nothing, "config" omitted

        QueueData qd = new QueueData();
        qd.setBrokerName("broker-a");
        qd.setReadQueueNums(4);
        qd.setWriteQueueNums(4);
        qd.setPerm(6);
        HashMap<Long, String> addrs = new HashMap<>();
        addrs.put(0L, "127.0.0.1:10911");
        BrokerData bd = new BrokerData("DefaultCluster", "broker-a", addrs);
        TopicRouteData route = new TopicRouteData();
        route.setQueueDatas(List.of(qd));
        route.setBrokerDatas(List.of(bd));
        Mockito.when(ext.examineTopicRouteInfo("orders")).thenReturn(route);

        Map<String, Object> result = asMap(invokeExecutor("describeTopic",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "orders"));
        Assert.assertEquals("orders", result.get("topic"));
        Assert.assertFalse(result.containsKey("config"));
        List<Object> queues = asList(result.get("queueDatas"));
        Assert.assertEquals(1, queues.size());
        Assert.assertEquals("broker-a", asMap(queues.get(0)).get("brokerName"));
        Assert.assertEquals(1, asList(result.get("brokerDatas")).size());
    }

    @Test
    public void testUpsertTopicCreatesNewConfig() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(twoBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "orders");
        args.put("readQueueNums", 4);
        args.put("writeQueueNums", "8");
        args.put("perm", 6);
        Map<String, Object> result = asMap(invokeExecutor("upsertTopic",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals("orders", result.get("topic"));
        Assert.assertEquals(4, result.get("readQueueNums"));
        Assert.assertEquals(8, result.get("writeQueueNums"));
        Assert.assertEquals(6, result.get("perm"));
        Assert.assertEquals(2, result.get("appliedBrokers"));

        ArgumentCaptor<TopicConfig> captor = ArgumentCaptor.forClass(TopicConfig.class);
        Mockito.verify(ext, Mockito.times(2))
                .createAndUpdateTopicConfig(Mockito.anyString(), captor.capture());
        Assert.assertEquals("orders", captor.getValue().getTopicName());
        Assert.assertEquals(8, captor.getValue().getWriteQueueNums());
    }

    @Test
    public void testUpsertTopicRequiresTopicArgument() throws Exception {
        AdminClientHelper admin = newAdmin(Mockito.mock(MQAdminExt.class));
        try {
            invokeExecutor("upsertTopic",
                    new Class<?>[] {AdminClientHelper.class, Map.class}, admin, new LinkedHashMap<>());
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertEquals("Missing required argument: topic", e.getMessage());
        }
    }

    @Test
    public void testDeleteTopicReportsDeletion() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> result = asMap(invokeExecutor("deleteTopic",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "orders"));
        Assert.assertEquals("orders", result.get("topic"));
        Assert.assertEquals(Boolean.TRUE, result.get("deleted"));
        Mockito.verify(ext).deleteTopicInBroker(Mockito.anySet(), Mockito.eq("orders"));
    }

    // ---- group -----------------------------------------------------------------------------

    @Test
    public void testListGroupsMergesAndSkipsFailedBrokers() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(twoBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        SubscriptionGroupConfig cfgA = new SubscriptionGroupConfig();
        cfgA.setGroupName("group-a");
        SubscriptionGroupConfig cfgB = new SubscriptionGroupConfig();
        cfgB.setGroupName("group-b");
        ConcurrentHashMap<String, SubscriptionGroupConfig> table = new ConcurrentHashMap<>();
        table.put("group-a", cfgA);
        table.put("group-b", cfgB);
        SubscriptionGroupWrapper wrapper = new SubscriptionGroupWrapper();
        wrapper.setSubscriptionGroupTable(table);
        Mockito.when(ext.getAllSubscriptionGroup("127.0.0.1:10911", 10000)).thenReturn(wrapper);
        Mockito.when(ext.getAllSubscriptionGroup("127.0.0.1:10921", 10000))
                .thenThrow(new RuntimeException("broker unreachable"));

        List<Object> groups = asList(invokeExecutor("listGroups",
                new Class<?>[] {AdminClientHelper.class}, admin));
        Assert.assertEquals(2, groups.size());
    }

    @Test
    public void testDescribeGroupThrowsWhenGroupMissing() throws Exception {
        // no cluster info stubbed -> examineSubscriptionGroupConfig resolves to null
        AdminClientHelper admin = newAdmin(Mockito.mock(MQAdminExt.class));
        try {
            invokeExecutor("describeGroup",
                    new Class<?>[] {AdminClientHelper.class, String.class}, admin, "missing");
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Consumer group not found: missing"));
        }
    }

    @Test
    public void testDescribeGroupFallsBackWhenNoOnlineConsumers() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);
        SubscriptionGroupConfig cfg = new SubscriptionGroupConfig();
        cfg.setGroupName("group-a");
        ConcurrentHashMap<String, SubscriptionGroupConfig> groupTable = new ConcurrentHashMap<>();
        groupTable.put("group-a", cfg);
        SubscriptionGroupWrapper groupWrapper = new SubscriptionGroupWrapper();
        groupWrapper.setSubscriptionGroupTable(groupTable);
        Mockito.when(ext.getAllSubscriptionGroup("127.0.0.1:10911", 10000)).thenReturn(groupWrapper);
        Mockito.when(ext.examineConsumerConnectionInfo("group-a"))
                .thenThrow(new RuntimeException("no consumers online"));

        Map<String, Object> result = asMap(invokeExecutor("describeGroup",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "group-a"));
        Assert.assertEquals("group-a", result.get("group"));
        Assert.assertEquals(0, result.get("connectionCount"));
        Assert.assertFalse(result.containsKey("consumeType"));
    }

    @Test
    public void testUpsertGroupAppliesArguments() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("group", "group-a");
        args.put("consumeMode", "broadcast");
        args.put("retryMaxTimes", "5");
        Map<String, Object> result = asMap(invokeExecutor("upsertGroup",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals("group-a", result.get("group"));
        Assert.assertEquals("BROADCAST", result.get("consumeMode"));
        Assert.assertEquals(5, result.get("retryMaxTimes"));
        Assert.assertEquals(1, result.get("appliedBrokers"));
    }

    @Test
    public void testResetOffsetMapsQueues() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        Map<MessageQueue, Long> offsets = new LinkedHashMap<>();
        offsets.put(new MessageQueue("orders", "broker-a", 0), 123L);
        Mockito.when(ext.resetOffsetByTimestamp("orders", "group-a", 1700000000000L, true))
                .thenReturn(offsets);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("group", "group-a");
        args.put("topic", "orders");
        args.put("timestamp", "1700000000000");
        Map<String, Object> result = asMap(invokeExecutor("resetOffset",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals("group-a", result.get("group"));
        Assert.assertEquals(1700000000000L, result.get("timestamp"));
        List<Object> queues = asList(result.get("resetQueues"));
        Assert.assertEquals(1, queues.size());
        Assert.assertEquals(123L, asMap(queues.get(0)).get("offset"));
    }

    @Test
    public void testDeleteGroupReportsAppliedBrokers() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> result = asMap(invokeExecutor("deleteGroup",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "group-a"));
        Assert.assertEquals("group-a", result.get("group"));
        Assert.assertEquals(Boolean.TRUE, result.get("deleted"));
        Assert.assertEquals(1, result.get("appliedBrokers"));
        Mockito.verify(ext).deleteSubscriptionGroup("127.0.0.1:10911", "group-a", true);
    }

    // ---- message ---------------------------------------------------------------------------

    @Test
    public void testQueryMessageByIdMapsMessage() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG-9");
        msg.setTopic("orders");
        Mockito.when(ext.viewMessage("orders", "MSG-9")).thenReturn(msg);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "orders");
        args.put("msgId", "MSG-9");
        Map<String, Object> result = asMap(invokeExecutor("queryMessageById",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));
        Assert.assertEquals("MSG-9", result.get("msgId"));
        Assert.assertEquals("orders", result.get("topic"));
    }

    @Test
    public void testQueryMessageByTimeUsesDefaultMaxNum() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        MessageExt msg = new MessageExt();
        msg.setMsgId("MSG-10");
        msg.setTopic("orders");
        Mockito.when(ext.queryMessage("orders", "*", 32, 1000L, 2000L))
                .thenReturn(new QueryResult(0, List.of(msg)));

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "orders");
        args.put("beginTime", 1000L);
        args.put("endTime", 2000L);
        List<Object> messages = asList(invokeExecutor("queryMessageByTime",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));
        Assert.assertEquals(1, messages.size());
        Assert.assertEquals("MSG-10", asMap(messages.get(0)).get("msgId"));
    }

    @Test
    public void testResendMessageFailsWithoutOnlineConsumers() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        ConsumerConnection conn = new ConsumerConnection();
        conn.setConnectionSet(new HashSet<>());
        Mockito.when(ext.examineConsumerConnectionInfo("group-a")).thenReturn(conn);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("topic", "orders");
        args.put("msgId", "MSG-11");
        args.put("group", "group-a");
        try {
            invokeExecutor("resendMessage",
                    new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args);
            Assert.fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            Assert.assertTrue(e.getMessage().contains("No online consumers in group group-a"));
        }
    }

    // ---- client ----------------------------------------------------------------------------

    @Test
    public void testListClientsMapsConnections() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        Connection c = new Connection();
        c.setClientId("client-1");
        c.setClientAddr("10.0.0.1:5000");
        c.setLanguage(LanguageCode.JAVA);
        ConsumerConnection conn = new ConsumerConnection();
        conn.setConnectionSet(new HashSet<>(List.of(c)));
        Mockito.when(ext.examineConsumerConnectionInfo("group-a")).thenReturn(conn);

        List<Object> clients = asList(invokeExecutor("listClients",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "group-a"));
        Assert.assertEquals(1, clients.size());
        Assert.assertEquals("client-1", asMap(clients.get(0)).get("clientId"));
        Assert.assertEquals("group-a", asMap(clients.get(0)).get("group"));
    }

    @Test
    public void testDescribeClientRequiresGroup() throws Exception {
        AdminClientHelper admin = newAdmin(Mockito.mock(MQAdminExt.class));
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("clientId", "client-1");
        try {
            invokeExecutor("describeClient",
                    new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("'group' argument is required"));
        }
    }

    @Test
    public void testDescribeClientThrowsWhenClientNotFound() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        Connection other = new Connection();
        other.setClientId("other-client");
        ConsumerConnection conn = new ConsumerConnection();
        conn.setConnectionSet(new HashSet<>(List.of(other)));
        Mockito.when(ext.examineConsumerConnectionInfo("group-a")).thenReturn(conn);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("clientId", "client-1");
        args.put("group", "group-a");
        try {
            invokeExecutor("describeClient",
                    new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args);
            Assert.fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Client not found in group group-a: client-1"));
        }
    }

    // ---- acl -------------------------------------------------------------------------------

    @Test
    public void testCreateAclDefaultsToAllow() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(twoBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("username", "alice");
        args.put("resource", "Topic:orders");
        args.put("actions", "PUB|SUB");
        Map<String, Object> result = asMap(invokeExecutor("createAcl",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals("User:alice", result.get("subject"));
        Assert.assertEquals(List.of("Topic:orders"), result.get("resources"));
        Assert.assertEquals(List.of("PUB", "SUB"), result.get("actions"));
        Assert.assertEquals("ALLOW", result.get("decision"));
        Assert.assertEquals(Boolean.TRUE, result.get("created"));
        Mockito.verify(ext).createAcl("127.0.0.1:10911", "User:alice",
                List.of("Topic:orders"), List.of("PUB", "SUB"), null, "ALLOW");
        Mockito.verify(ext).createAcl("127.0.0.1:10921", "User:alice",
                List.of("Topic:orders"), List.of("PUB", "SUB"), null, "ALLOW");
    }

    @Test
    public void testUpdateAclUsesProvidedDecision() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("policyId", "User:bob");
        args.put("decision", "DENY");
        Map<String, Object> result = asMap(invokeExecutor("updateAcl",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals("User:bob", result.get("subject"));
        Assert.assertEquals(List.of(), result.get("resources"));
        Assert.assertEquals(List.of(), result.get("actions"));
        Assert.assertEquals("DENY", result.get("decision"));
        Assert.assertEquals(Boolean.TRUE, result.get("updated"));
        Mockito.verify(ext).updateAcl("127.0.0.1:10911", "User:bob",
                List.of(), List.of(), null, "DENY");
    }

    @Test
    public void testDeleteAclOnAllBrokers() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("policyId", "carol");
        args.put("resource", "Topic:orders");
        Map<String, Object> result = asMap(invokeExecutor("deleteAcl",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals("User:carol", result.get("subject"));
        Assert.assertEquals(Boolean.TRUE, result.get("deleted"));
        Mockito.verify(ext).deleteAcl("127.0.0.1:10911", "User:carol", "Topic:orders");
    }

    // ---- broker ----------------------------------------------------------------------------

    @Test
    public void testDescribeBrokerMapsRuntimeStats() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);
        KVTable stats = new KVTable();
        HashMap<String, String> table = new HashMap<>();
        table.put("putTps", "12.3");
        stats.setTable(table);
        Mockito.when(ext.fetchBrokerRuntimeStats("127.0.0.1:10911")).thenReturn(stats);

        Map<String, Object> result = asMap(invokeExecutor("describeBroker",
                new Class<?>[] {AdminClientHelper.class, String.class}, admin, "broker-a"));
        Assert.assertEquals("broker-a", result.get("brokerName"));
        Assert.assertEquals("127.0.0.1:10911", result.get("brokerAddr"));
        Assert.assertEquals("12.3", asMap(result.get("runtimeStats")).get("putTps"));
    }

    @Test
    public void testBrokerConfigReadsAllPropertiesSorted() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);
        Properties properties = new Properties();
        properties.setProperty("zKey", "z");
        properties.setProperty("aKey", "a");
        Mockito.when(ext.getBrokerConfig("127.0.0.1:10911")).thenReturn(properties);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("brokerName", "broker-a");
        Map<String, Object> result = asMap(invokeExecutor("brokerConfig",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Map<String, Object> config = asMap(result.get("config"));
        Assert.assertEquals(List.of("aKey", "zKey"), new ArrayList<>(config.keySet()));
        Assert.assertEquals("a", config.get("aKey"));
    }

    @Test
    public void testBrokerConfigUpdatesProperty() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        Mockito.when(ext.examineBrokerClusterInfo()).thenReturn(singleBrokerClusterInfo());
        AdminClientHelper admin = newAdmin(ext);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("brokerName", "broker-a");
        args.put("configKey", "fileReservedTime");
        args.put("configValue", "72");
        Map<String, Object> result = asMap(invokeExecutor("brokerConfig",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals(Map.of("fileReservedTime", "72"), result.get("updated"));
        ArgumentCaptor<Properties> captor = ArgumentCaptor.forClass(Properties.class);
        Mockito.verify(ext).updateBrokerConfig(Mockito.eq("127.0.0.1:10911"), captor.capture());
        Assert.assertEquals("72", captor.getValue().getProperty("fileReservedTime"));
    }

    // ---- metrics ---------------------------------------------------------------------------

    @Test
    public void testQueryMetricsRequiresTargetName() throws Exception {
        AdminClientHelper admin = newAdmin(Mockito.mock(MQAdminExt.class));
        for (String metricType : List.of("topic", "consumer", "broker")) {
            Map<String, Object> args = new LinkedHashMap<>();
            args.put("metricType", metricType);
            try {
                invokeExecutor("queryMetrics",
                        new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args);
                Assert.fail("Expected IllegalArgumentException for metricType " + metricType);
            } catch (IllegalArgumentException e) {
                Assert.assertTrue(e.getMessage().contains("targetName"));
            }
        }
    }

    @Test
    public void testQueryMetricsConsumerComputesDiff() throws Exception {
        MQAdminExt ext = Mockito.mock(MQAdminExt.class);
        AdminClientHelper admin = newAdmin(ext);
        ConsumeStats stats = new ConsumeStats();
        stats.setConsumeTps(3.5);
        OffsetWrapper wrapper = new OffsetWrapper();
        wrapper.setBrokerOffset(100L);
        wrapper.setConsumerOffset(60L);
        HashMap<MessageQueue, OffsetWrapper> offsetTable = new HashMap<>();
        offsetTable.put(new MessageQueue("orders", "broker-a", 0), wrapper);
        stats.setOffsetTable(offsetTable);
        Mockito.when(ext.examineConsumeStats("group-a")).thenReturn(stats);

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("metricType", "CONSUMER");
        args.put("targetName", "group-a");
        Map<String, Object> result = asMap(invokeExecutor("queryMetrics",
                new Class<?>[] {AdminClientHelper.class, Map.class}, admin, args));

        Assert.assertEquals(3.5, (Double) result.get("consumeTps"), 0.0001);
        Assert.assertEquals(40L, result.get("totalDiff"));
        List<Object> queues = asList(result.get("data"));
        Assert.assertEquals(1, queues.size());
        Assert.assertEquals(40L, asMap(queues.get(0)).get("diff"));
    }
}
