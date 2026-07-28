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
package org.apache.rocketmq.dashboard.service.client;

import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.util.MockObjectUtil;
import org.apache.rocketmq.remoting.protocol.LanguageCode;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.remoting.protocol.body.ProducerConnection;
import org.apache.rocketmq.remoting.protocol.body.TopicList;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RemotingClientCollector}.
 *
 * <p>The collector reads the MQAdminExt from {@link MQAdminInstance}'s ThreadLocal,
 * which is populated here via {@link MQAdminInstance#setCurrentMQAdminExt}.</p>
 */
@RunWith(MockitoJUnitRunner.Silent.class)
public class RemotingClientCollectorTest {

    @Mock
    private MQAdminExt mqAdminExt;

    private RemotingClientCollector collector;

    @Before
    public void setUp() {
        collector = new RemotingClientCollector();
        MQAdminInstance.setCurrentMQAdminExt(mqAdminExt);
    }

    @After
    public void tearDown() {
        MQAdminInstance.clearCurrentMQAdminExt();
    }

    private TopicList topicListOf(String... names) {
        TopicList topicList = new TopicList();
        topicList.setTopicList(new HashSet<>(Arrays.asList(names)));
        return topicList;
    }

    private Connection connection(String clientId, String addr, LanguageCode lang) {
        Connection conn = new Connection();
        conn.setClientId(clientId);
        conn.setClientAddr(addr);
        conn.setLanguage(lang);
        conn.setVersion(0);
        return conn;
    }

    private ProducerConnection producerConnectionOf(Connection... conns) {
        ProducerConnection connection = new ProducerConnection();
        connection.setConnectionSet(new HashSet<>(Arrays.asList(conns)));
        return connection;
    }

    @Test
    public void testCollectProducersAndConsumers() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topic_test", "%RETRY%groupA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topic_test"))
            .thenReturn(producerConnectionOf(connection("producer-1", "127.0.0.1:1001", LanguageCode.JAVA)));
        when(mqAdminExt.examineConsumerConnectionInfo("groupA"))
            .thenReturn(MockObjectUtil.createConsumerConnection());

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertEquals(2, clients.size());

        ClientInstance producer = clients.stream()
            .filter(c -> c.getClientType() == ClientInstance.ClientType.PRODUCER)
            .findFirst().orElseThrow(AssertionError::new);
        assertEquals("producer-1", producer.getClientId());
        assertEquals("127.0.0.1:1001", producer.getClientAddress());
        assertEquals(ClientInstance.ProtocolType.REMOTING, producer.getProtocolType());
        assertEquals("JAVA", producer.getLanguage());
        assertEquals("DEFAULT_PRODUCER", producer.getProducerGroup());
        assertTrue(producer.getTopics().contains("topic_test"));

        // CONSUME_ACTIVELY maps to PULL_CONSUMER
        ClientInstance consumer = clients.stream()
            .filter(c -> c.getClientType() == ClientInstance.ClientType.PULL_CONSUMER)
            .findFirst().orElseThrow(AssertionError::new);
        assertEquals("clientId", consumer.getClientId());
        assertEquals("groupA", consumer.getConsumerGroup());
        assertEquals(ClientInstance.ProtocolType.REMOTING, consumer.getProtocolType());
        assertTrue(consumer.getTopics().contains("topic_test"));
    }

    @Test
    public void testTopicFilter() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topic_test", "%RETRY%groupA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topic_test"))
            .thenReturn(producerConnectionOf(connection("producer-1", "127.0.0.1:1001", LanguageCode.JAVA)));
        when(mqAdminExt.examineConsumerConnectionInfo("groupA"))
            .thenReturn(MockObjectUtil.createConsumerConnection());

        // Both producer and consumer relate to topic_test
        List<ClientInstance> matched = collector.listClientInstances(Optional.of("topic_test"), Optional.empty());
        assertEquals(2, matched.size());

        // Nothing relates to a non-existent topic
        List<ClientInstance> unmatched = collector.listClientInstances(Optional.of("no_such_topic"), Optional.empty());
        assertTrue(unmatched.isEmpty());
    }

    @Test
    public void testGroupFilterSkipsOtherGroups() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("%RETRY%groupA", "%RETRY%groupB"));

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.of("groupB"));
        assertTrue(clients.isEmpty());
        verify(mqAdminExt, never()).examineConsumerConnectionInfo("groupA");
        verify(mqAdminExt).examineConsumerConnectionInfo("groupB");
    }

    @Test
    public void testProducerDeduplicationAcrossTopics() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA", "topicB"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicA"))
            .thenReturn(producerConnectionOf(connection("producer-1", "127.0.0.1:1001", LanguageCode.JAVA)));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicB"))
            .thenReturn(producerConnectionOf(connection("producer-1", "127.0.0.1:1001", LanguageCode.JAVA)));

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        // Same clientId observed for both topics collapses into one instance
        assertEquals(1, clients.size());
        assertEquals("producer-1", clients.get(0).getClientId());
    }

    @Test
    public void testLanguageMapping() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("topicA"));
        when(mqAdminExt.examineProducerConnectionInfo("DEFAULT_PRODUCER", "topicA"))
            .thenReturn(producerConnectionOf(
                connection("producer-go", "127.0.0.1:1001", LanguageCode.GO),
                connection("producer-oms", "127.0.0.1:1002", LanguageCode.OMS)));

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertEquals(2, clients.size());
        for (ClientInstance client : clients) {
            if ("producer-go".equals(client.getClientId())) {
                assertEquals("GO", client.getLanguage());
            } else {
                // Falls through to default branch which uses enum name
                assertEquals("OMS", client.getLanguage());
            }
        }
    }

    @Test
    public void testNullConsumerConnectionIsSkipped() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("%RETRY%groupA"));
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenReturn(null);

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertTrue(clients.isEmpty());
    }

    @Test
    public void testConsumePassivelyMapsToPushConsumer() throws Exception {
        ConsumerConnection connection = MockObjectUtil.createConsumerConnection();
        connection.setConsumeType(org.apache.rocketmq.remoting.protocol.heartbeat.ConsumeType.CONSUME_PASSIVELY);
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("%RETRY%groupA"));
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenReturn(connection);

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertEquals(1, clients.size());
        assertEquals(ClientInstance.ClientType.PUSH_CONSUMER, clients.get(0).getClientType());
    }

    @Test
    public void testNullConsumeTypeDefaultsToPushConsumer() throws Exception {
        ConsumerConnection connection = new ConsumerConnection();
        connection.setConnectionSet(new HashSet<>(Arrays.asList(
            connection("consumer-1", "127.0.0.1:2001", LanguageCode.JAVA))));
        // consumeType and subscriptionTable left unset
        when(mqAdminExt.fetchAllTopicList()).thenReturn(topicListOf("%RETRY%groupA"));
        when(mqAdminExt.examineConsumerConnectionInfo("groupA")).thenReturn(connection);

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertEquals(1, clients.size());
        assertEquals(ClientInstance.ClientType.PUSH_CONSUMER, clients.get(0).getClientType());
    }

    @Test
    public void testNoThreadLocalAdminReturnsEmptyList() {
        MQAdminInstance.clearCurrentMQAdminExt();

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertTrue(clients.isEmpty());
    }

    @Test
    public void testFetchTopicListFailureReturnsEmptyList() throws Exception {
        when(mqAdminExt.fetchAllTopicList()).thenThrow(new RuntimeException("namesrv unreachable"));

        List<ClientInstance> clients = collector.listClientInstances(Optional.empty(), Optional.empty());
        assertTrue(clients.isEmpty());
    }
}
