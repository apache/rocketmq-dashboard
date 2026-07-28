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
package org.apache.rocketmq.dashboard.architecture;

import org.apache.rocketmq.dashboard.model.ACLPolicy;
import org.apache.rocketmq.dashboard.model.ACLUser;
import org.apache.rocketmq.dashboard.model.ClientInstance;
import org.apache.rocketmq.dashboard.model.ConsumerGroupInfo;
import org.apache.rocketmq.dashboard.model.LiteTopicQuota;
import org.apache.rocketmq.dashboard.model.LiteTopicSession;
import org.apache.rocketmq.dashboard.model.LiteTopicSummary;
import org.apache.rocketmq.dashboard.model.MessageInfo;
import org.apache.rocketmq.dashboard.model.NamespaceInfo;
import org.apache.rocketmq.dashboard.model.SubscriptionInfo;
import org.apache.rocketmq.dashboard.model.TopicInfo;
import org.apache.rocketmq.dashboard.model.TopicType;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link MetadataProvider} default methods.
 *
 * <p>Default methods must be exercised through a concrete implementation:
 * mocking the interface would replace the default bodies with stubs.</p>
 */
public class MetadataProviderDefaultMethodsTest {

    private final ClientInstance sentinelClient = new ClientInstance();

    /**
     * Minimal implementation that only overrides abstract methods, leaving
     * every default method with its interface-provided implementation.
     */
    private class TestMetadataProvider implements MetadataProvider {
        @Override
        public List<NamespaceInfo> listNamespaces() {
            return Collections.emptyList();
        }

        @Override
        public Optional<NamespaceInfo> getNamespace(String namespace) {
            return Optional.empty();
        }

        @Override
        public void createNamespace(NamespaceInfo namespace) {
        }

        @Override
        public void updateNamespace(NamespaceInfo namespace) {
        }

        @Override
        public void deleteNamespace(String namespace) {
        }

        @Override
        public List<TopicInfo> listTopics(Optional<String> namespace) {
            return Collections.emptyList();
        }

        @Override
        public Optional<TopicInfo> getTopic(String topic, Optional<String> namespace) {
            return Optional.empty();
        }

        @Override
        public void createTopic(TopicInfo topic) {
        }

        @Override
        public void updateTopic(TopicInfo topic) {
        }

        @Override
        public void deleteTopic(String topic, Optional<String> namespace) {
        }

        @Override
        public boolean validateTopicType(String topic, TopicType expectedType) {
            return false;
        }

        @Override
        public List<LiteTopicSummary> listLiteTopics(String pattern, Optional<String> namespace) {
            return Collections.emptyList();
        }

        @Override
        public LiteTopicSession getLiteTopicSession(String sessionId) {
            return null;
        }

        @Override
        public void extendLiteTopicTTL(String topicPattern, long newTTL) {
        }

        @Override
        public LiteTopicQuota getLiteTopicQuota(Optional<String> namespace) {
            return null;
        }

        @Override
        public List<ConsumerGroupInfo> listConsumerGroups(Optional<String> namespace) {
            return Collections.emptyList();
        }

        @Override
        public Optional<ConsumerGroupInfo> getConsumerGroup(String consumerGroup, Optional<String> namespace) {
            return Optional.empty();
        }

        @Override
        public void createConsumerGroup(ConsumerGroupInfo consumerGroup) {
        }

        @Override
        public void updateConsumerGroup(ConsumerGroupInfo consumerGroup) {
        }

        @Override
        public void deleteConsumerGroup(String consumerGroup, Optional<String> namespace) {
        }

        @Override
        public List<SubscriptionInfo> listSubscriptions(String groupName) {
            return Collections.emptyList();
        }

        @Override
        public void resetConsumerGroupOffset(String groupName, String topic, long timestamp) {
        }

        @Override
        public List<ACLPolicy> listACLPolicy(Optional<String> namespace) {
            return Collections.emptyList();
        }

        @Override
        public List<ACLUser> listACLUsers() {
            return Collections.emptyList();
        }

        @Override
        public void createACLPolicy(ACLPolicy policy) {
        }

        @Override
        public void updateACLPolicy(ACLPolicy policy) {
        }

        @Override
        public void deleteACLPolicy(String policyId) {
        }

        @Override
        public List<ACLPolicy> listACLPolicies(String username) {
            return Collections.emptyList();
        }

        @Override
        public void createACLUser(ACLUser user) {
        }

        @Override
        public void updateACLUser(ACLUser user) {
        }

        @Override
        public void deleteACLUser(String username) {
        }

        @Override
        public void addACLPolicy(ACLPolicy policy) {
        }

        @Override
        public void removeACLPolicy(String username, String policyId) {
        }

        @Override
        public Optional<ACLUser> getACLUser(String username) {
            return Optional.empty();
        }

        @Override
        public boolean checkACLPermission(String username, String resource, String action) {
            return false;
        }

        @Override
        public List<MessageInfo> queryMessageByTopic(String topic, long beginTime, long endTime, int maxNum) {
            return Collections.emptyList();
        }

        @Override
        public List<MessageInfo> queryMessageByTopicAndKey(String topic, String key, long beginTime, long endTime) {
            return Collections.emptyList();
        }

        @Override
        public List<MessageInfo> queryMessageByGroup(String consumerGroup, String topic, long beginTime, long endTime) {
            return Collections.emptyList();
        }

        @Override
        public Optional<MessageInfo> getMessageById(String msgId) {
            return Optional.empty();
        }

        @Override
        public List<MessageInfo> getMessagesByOffset(String topic, String brokerName, int queueId, long offset, int count) {
            return Collections.emptyList();
        }

        @Override
        public long searchOffset(String topic, String brokerName, int queueId, long timestamp) {
            return 0L;
        }

        @Override
        public long getMaxOffset(String topic, String brokerName, int queueId) {
            return 0L;
        }

        @Override
        public long getMinOffset(String topic, String brokerName, int queueId) {
            return 0L;
        }

        @Override
        public void deleteMessage(String topic, String msgId) {
        }

        @Override
        public void resendMessage(String msgId, String newTopic) {
        }

        @Override
        public List<ClientInstance> listClientInstances(Optional<String> topic, Optional<String> group) {
            return Collections.singletonList(sentinelClient);
        }

        @Override
        public Optional<ClientInstance> getClientInstance(String clientId) {
            return Optional.of(sentinelClient);
        }

        @Override
        public List<SubscriptionInfo> getClientSubscriptions(String clientId) {
            return Collections.emptyList();
        }

        @Override
        public String getProviderType() {
            return "TEST";
        }

        @Override
        public boolean supportsCapability(String capability) {
            return false;
        }
    }

    private final MetadataProvider provider = new TestMetadataProvider();

    // ==================== Delegating defaults ====================

    @Test
    public void testListClientsDelegatesToListClientInstances() throws Exception {
        List<ClientInstance> clients = provider.listClients();
        assertEquals(1, clients.size());
        assertEquals(sentinelClient, clients.get(0));
    }

    @Test
    public void testGetClientDelegatesToGetClientInstance() throws Exception {
        Optional<ClientInstance> client = provider.getClient("client-1");
        assertTrue(client.isPresent());
        assertEquals(sentinelClient, client.get());
    }

    // ==================== Unsupported defaults ====================

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClusterMetricsUnsupported() throws Exception {
        provider.getClusterMetrics();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetBrokerMetricsUnsupported() throws Exception {
        provider.getBrokerMetrics("broker-a");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetTopicMetricsUnsupported() throws Exception {
        provider.getTopicMetrics("topicA");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetConsumerGroupMetricsUnsupported() throws Exception {
        provider.getConsumerGroupMetrics("groupA");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllBrokersMetricsUnsupported() throws Exception {
        provider.getAllBrokersMetrics();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllTopicsMetricsUnsupported() throws Exception {
        provider.getAllTopicsMetrics();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClientMetricsUnsupported() throws Exception {
        provider.getClientMetrics();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetSystemMetricsUnsupported() throws Exception {
        provider.getSystemMetrics();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetCustomMetricsUnsupported() throws Exception {
        provider.getCustomMetrics("latency");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConfigureMetricsExportUnsupported() throws Exception {
        provider.configureMetricsExport("{}");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsByProtocolUnsupported() throws Exception {
        provider.listClientsByProtocol("GRPC");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsByTypeUnsupported() throws Exception {
        provider.listClientsByType("PRODUCER");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testListClientsByClusterUnsupported() throws Exception {
        provider.listClientsByCluster("DefaultCluster");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testKillClientUnsupported() throws Exception {
        provider.killClient("client-1", "idle");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testUpdateClientConfigUnsupported() throws Exception {
        provider.updateClientConfig("client-1", "key", "value");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetConnectedClientsUnsupported() throws Exception {
        provider.getConnectedClients("127.0.0.1:10911");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetIdleClientsUnsupported() throws Exception {
        provider.getIdleClients(60000L);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testGetClientsWithIssueUnsupported() throws Exception {
        provider.getClientsWithIssue("SLOW_CONSUME");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testDiagnoseClientUnsupported() throws Exception {
        provider.diagnoseClient("client-1");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void testConsumeMessageDirectlyUnsupported() throws Exception {
        provider.consumeMessageDirectly("topicA", "msgId", "groupA", "client-1");
    }
}
