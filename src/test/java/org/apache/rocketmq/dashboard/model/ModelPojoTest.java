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
package org.apache.rocketmq.dashboard.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.dashboard.config.CredentialEncryptionService;
import org.apache.rocketmq.remoting.protocol.admin.RollbackStats;
import org.apache.rocketmq.remoting.protocol.body.UserInfo;
import org.junit.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class ModelPojoTest {

    // ==================== CloudProviderConfig ====================

    @Test
    public void testCloudProviderConfigGettersSetters() {
        CloudProviderConfig config = new CloudProviderConfig();
        config.setProviderType("cloud-aliyun");
        config.setInstanceId("mq-instance-1");
        config.setAccessKey("ak");
        config.setSecretKey("sk");
        config.setEncryptedAccessKey("enc-ak");
        config.setEncryptedSecretKey("enc-sk");
        config.setCredentialsEncrypted(false);
        config.setRegionId("cn-hangzhou");
        config.setEndpoint("http://endpoint");
        config.setDisplayName("aliyun-cluster");
        config.setEnabled(true);
        Map<String, String> extended = new HashMap<>();
        extended.put("vpcId", "vpc-xxx");
        config.setExtendedConfig(extended);

        assertEquals("cloud-aliyun", config.getProviderType());
        assertEquals("mq-instance-1", config.getInstanceId());
        assertEquals("ak", config.getAccessKey());
        assertEquals("sk", config.getSecretKey());
        assertEquals("enc-ak", config.getEncryptedAccessKey());
        assertEquals("enc-sk", config.getEncryptedSecretKey());
        assertFalse(config.isCredentialsEncrypted());
        assertEquals("cn-hangzhou", config.getRegionId());
        assertEquals("http://endpoint", config.getEndpoint());
        assertEquals("aliyun-cluster", config.getDisplayName());
        assertTrue(config.isEnabled());
        assertEquals("vpc-xxx", config.getExtendedConfig().get("vpcId"));

        assertTrue(config.isAliyun());
        assertFalse(config.isTencent());
        assertFalse(config.isHuawei());
        config.setProviderType("cloud-tencent");
        assertTrue(config.isTencent());
        config.setProviderType("cloud-huawei");
        assertTrue(config.isHuawei());

        String str = config.toString();
        assertTrue(str.contains("cloud-huawei"));
        assertFalse(str.contains("enc-ak"));
        assertFalse(str.contains("sk"));
    }

    @Test
    public void testCloudProviderConfigEncryptDecryptAndMask() {
        CredentialEncryptionService service = new CredentialEncryptionService("unit-test-key");
        CloudProviderConfig config = new CloudProviderConfig();
        config.setAccessKey("my-access-key");
        config.setSecretKey("my-secret-key");

        config.encryptCredentials(service);
        assertNull(config.getAccessKey());
        assertNull(config.getSecretKey());
        assertTrue(config.isCredentialsEncrypted());
        assertNotNull(config.getEncryptedAccessKey());
        assertNotNull(config.getEncryptedSecretKey());

        config.decryptCredentials(service);
        assertEquals("my-access-key", config.getAccessKey());
        assertEquals("my-secret-key", config.getSecretKey());
        assertFalse(config.isCredentialsEncrypted());

        CloudProviderConfig masked = config.maskCredentials();
        assertEquals("********", masked.getAccessKey());
        assertEquals("********", masked.getSecretKey());
        assertNull(masked.getEncryptedAccessKey());
        assertNull(masked.getEncryptedSecretKey());

        // Null credentials remain null after masking
        CloudProviderConfig empty = new CloudProviderConfig();
        CloudProviderConfig maskedEmpty = empty.maskCredentials();
        assertNull(maskedEmpty.getAccessKey());
        assertNull(maskedEmpty.getSecretKey());
    }

    @Test
    public void testCloudProviderConfigEncryptDecryptSkipsEmpty() {
        CredentialEncryptionService service = new CredentialEncryptionService("unit-test-key");
        CloudProviderConfig config = new CloudProviderConfig();
        config.encryptCredentials(service);
        assertNull(config.getEncryptedAccessKey());
        assertNull(config.getEncryptedSecretKey());
        assertTrue(config.isCredentialsEncrypted());

        config.decryptCredentials(service);
        assertNull(config.getAccessKey());
        assertNull(config.getSecretKey());
        assertFalse(config.isCredentialsEncrypted());
    }

    @Test
    public void testCloudProviderConfigValidate() {
        CloudProviderConfig config = new CloudProviderConfig();
        assertValidateFails(config, "providerType");
        config.setProviderType("cloud-aliyun");
        assertValidateFails(config, "instanceId");
        config.setInstanceId("id-1");
        assertValidateFails(config, "regionId");
        config.setRegionId("cn-hangzhou");
        assertValidateFails(config, "accessKey");
        config.setAccessKey("ak");
        assertValidateFails(config, "secretKey");
        config.setSecretKey("sk");
        config.validate();

        // Encrypted variant
        CloudProviderConfig encrypted = new CloudProviderConfig();
        encrypted.setProviderType("cloud-aliyun");
        encrypted.setInstanceId("id-1");
        encrypted.setRegionId("cn-hangzhou");
        encrypted.setCredentialsEncrypted(true);
        assertValidateFails(encrypted, "encryptedAccessKey");
        encrypted.setEncryptedAccessKey("enc-ak");
        assertValidateFails(encrypted, "encryptedSecretKey");
        encrypted.setEncryptedSecretKey("enc-sk");
        encrypted.validate();
    }

    private void assertValidateFails(CloudProviderConfig config, String expectedField) {
        try {
            config.validate();
            fail("Expected IllegalArgumentException for missing " + expectedField);
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains(expectedField));
        }
    }

    // ==================== QueueOffsetInfo ====================

    @Test
    public void testQueueOffsetInfo() {
        MessageQueue queue = new MessageQueue("topicA", "broker-a", 0);
        QueueOffsetInfo info = new QueueOffsetInfo(1, 0L, 100L, 10L, 20L, queue);
        assertEquals(Integer.valueOf(1), info.getIdx());
        assertEquals(Long.valueOf(0L), info.getStart());
        assertEquals(Long.valueOf(100L), info.getEnd());
        assertEquals(Long.valueOf(10L), info.getStartOffset());
        assertEquals(Long.valueOf(20L), info.getEndOffset());
        assertSame(queue, info.getMessageQueues());

        info.incStartOffset();
        assertEquals(Long.valueOf(11L), info.getStartOffset());
        assertEquals(Long.valueOf(21L), info.getEndOffset());

        info.incEndOffset();
        assertEquals(Long.valueOf(22L), info.getEndOffset());

        info.incStartOffset(5);
        assertEquals(Long.valueOf(16L), info.getStartOffset());
        assertEquals(Long.valueOf(27L), info.getEndOffset());

        QueueOffsetInfo other = new QueueOffsetInfo();
        other.setIdx(2);
        other.setStart(1L);
        other.setEnd(2L);
        other.setStartOffset(3L);
        other.setEndOffset(4L);
        other.setMessageQueues(queue);
        assertEquals(Integer.valueOf(2), other.getIdx());
        assertEquals(Long.valueOf(1L), other.getStart());
        assertEquals(Long.valueOf(2L), other.getEnd());
        assertEquals(Long.valueOf(3L), other.getStartOffset());
        assertEquals(Long.valueOf(4L), other.getEndOffset());
    }

    // ==================== ClusterTopology ====================

    @Test
    public void testClusterTopology() {
        ClusterTopology topology = new ClusterTopology();
        topology.setClusterName("DefaultCluster");
        topology.setNamesrvAddresses(Arrays.asList("127.0.0.1:9876"));

        topology.addNode("namesrv-1", 0L, "127.0.0.1:9876", "NAMESRV");
        topology.addNode("broker-a", 0L, "127.0.0.1:10911", "BROKER");
        topology.addNode("broker-a", 1L, "127.0.0.1:10921", "BROKER");
        topology.addNode("proxy-1", 0L, "127.0.0.1:8080", "PROXY");
        topology.addNode("unknown-1", 0L, "127.0.0.1:1234", "OTHER");

        assertEquals("DefaultCluster", topology.getClusterName());
        assertEquals(1, topology.getNamesrvNodes().size());
        assertEquals(2, topology.getBrokerNodes().size());
        assertEquals(1, topology.getProxyNodes().size());
        assertEquals(4, topology.getTotalNodeCount());
        assertEquals(1, topology.getMasterBrokerCount());
        assertEquals(1, topology.getSlaveBrokerCount());
        assertEquals(5, topology.getNodeMap().size());
        assertEquals(Arrays.asList("127.0.0.1:9876"), topology.getNamesrvAddresses());
    }

    @Test
    public void testClusterTopologyNodeInfo() {
        ClusterTopology.NodeInfo node = new ClusterTopology.NodeInfo();
        assertEquals("UNKNOWN", node.getStatus());
        assertNotNull(node.getMetadata());
        assertFalse(node.isMaster());
        assertFalse(node.isOnline());

        node.setNodeName("broker-a");
        node.setNodeId(0L);
        node.setNodeAddress("127.0.0.1:10911");
        node.setNodeType("BROKER");
        node.setClusterName("DefaultCluster");
        node.setStatus("ONLINE");
        node.setVersion(453L);
        assertTrue(node.isMaster());
        assertTrue(node.isOnline());
        assertEquals("broker-a", node.getNodeName());
        assertEquals("BROKER", node.getNodeType());
        assertEquals("DefaultCluster", node.getClusterName());
        assertEquals("127.0.0.1:10911", node.getNodeAddress());
        assertEquals(Long.valueOf(453L), node.getVersion());

        node.setNodeId(1L);
        assertFalse(node.isMaster());
    }

    // ==================== MessageQueryByPage ====================

    @Test
    public void testMessageQueryByPage() {
        MessageQueryByPage query = new MessageQueryByPage(3, 20, "topicA", 100L, 200L);
        assertEquals(2, query.getPageNum());
        assertEquals(20, query.getPageSize());
        assertEquals("topicA", query.getTopic());
        assertEquals(100L, query.getBegin());
        assertEquals(200L, query.getEnd());

        query.setPageNum(0);
        assertEquals(MessageQueryByPage.DEFAULT_PAGE, query.getPageNum());

        query.setPageSize(1);
        assertEquals(MessageQueryByPage.MIN_PAGE_SIZE, query.getPageSize());
        query.setPageSize(1000);
        assertEquals(MessageQueryByPage.MAX_PAGE_SIZE, query.getPageSize());

        query.setTopic("topicB");
        query.setBegin(1L);
        query.setEnd(2L);
        assertEquals("topicB", query.getTopic());
        assertEquals(1L, query.getBegin());
        assertEquals(2L, query.getEnd());

        PageRequest pageRequest = query.page();
        assertEquals(0, pageRequest.getPageNumber());
        assertEquals(MessageQueryByPage.MAX_PAGE_SIZE, pageRequest.getPageSize());

        assertTrue(query.toString().contains("topicB"));
    }

    // ==================== ClientInstance ====================

    @Test
    public void testClientInstance() {
        ClientInstance client = new ClientInstance();
        client.setClientId("client-1");
        client.setClientAddress("127.0.0.1:1234");
        client.setClientType(ClientInstance.ClientType.CONSUMER);
        client.setTopics(Arrays.asList("topicA"));
        client.setClientSubType("sub");
        client.setLanguage("JAVA");
        client.setSdkVersion("5.1.0");
        client.setProtocolType(ClientInstance.ProtocolType.GRPC);
        client.setEndpoint("endpoint");
        client.setActive(true);
        client.setConnectTime(new Date());
        client.setConsumerGroup("group-1");
        client.setProducerGroup("pgroup-1");
        client.setSubscriptions(Collections.emptyList());
        client.setPublishTopics(Collections.emptyList());
        client.setClientVersion("V5_1_0");
        client.setVipChannelEnabled(Boolean.FALSE);
        client.setTelemetrySessionId("session-1");
        client.setLongConnectionActive(Boolean.TRUE);
        client.setSettingsVersion("v1");
        client.setAuthFailureReason(null);
        client.setPopEnabled(Boolean.TRUE);
        client.setPendingAckCount(3);
        client.setSubscriptionCount(2);
        client.setStatus("ONLINE");

        assertEquals("client-1", client.getClientId());
        assertEquals("127.0.0.1:1234", client.getClientAddress());
        assertTrue(client.isConsumer());
        assertFalse(client.isProducer());
        assertTrue(client.isGrpcClient());
        assertFalse(client.isRemotingClient());
        assertTrue(client.isActive());
        assertEquals("5.1.0", client.getVersion());
        client.setVersion("4.9.4");
        assertEquals("4.9.4", client.getSdkVersion());

        // Display name falls back to clientId
        assertEquals("client-1", client.getDisplayName());
        client.setInstanceName("  ");
        assertEquals("client-1", client.getDisplayName());
        client.setInstanceName("instance-1");
        assertEquals("instance-1", client.getDisplayName());

        // Heartbeat delay
        assertEquals(-1, client.getClientDelay());
        client.setLastHeartbeatTime(new Date(System.currentTimeMillis() - 5_000));
        assertTrue(client.getClientDelay() >= 4);

        assertTrue(client.toString().contains("client-1"));
        assertEquals(client, client);
        assertNotEquals(client, new ClientInstance());
    }

    @Test
    public void testClientInstanceConsumerProgress() {
        ClientInstance.ConsumerProgress progress = new ClientInstance.ConsumerProgress();
        progress.setTotalConsumed(100L);
        progress.setTotalBacklog(10L);
        progress.setConsumptionRate(5.5);
        Date lastConsume = new Date();
        progress.setLastConsumeTime(lastConsume);
        progress.setConsumptionMode("POP");
        progress.setOrderlyConsume(Boolean.FALSE);

        assertEquals(Long.valueOf(100L), progress.getTotalConsumed());
        assertEquals(Long.valueOf(10L), progress.getTotalBacklog());
        assertEquals(Double.valueOf(5.5), progress.getConsumptionRate());
        assertEquals(lastConsume, progress.getLastConsumeTime());
        assertEquals("POP", progress.getConsumptionMode());
        assertFalse(progress.getOrderlyConsume());
        assertTrue(progress.toString().contains("POP"));

        ClientInstance client = new ClientInstance();
        client.setConsumerProgress(progress);
        assertSame(progress, client.getConsumerProgress());
    }

    // ==================== ConsumerGroupRollBackStat ====================

    @Test
    public void testConsumerGroupRollBackStat() {
        ConsumerGroupRollBackStat stat = new ConsumerGroupRollBackStat(true);
        assertTrue(stat.isStatus());
        assertNull(stat.getErrMsg());
        assertNotNull(stat.getRollbackStatsList());

        ConsumerGroupRollBackStat failed = new ConsumerGroupRollBackStat(false, "boom");
        assertFalse(failed.isStatus());
        assertEquals("boom", failed.getErrMsg());

        failed.setStatus(true);
        failed.setErrMsg("fixed");
        RollbackStats rollbackStats = new RollbackStats();
        failed.setRollbackStatsList(Collections.singletonList(rollbackStats));
        assertTrue(failed.isStatus());
        assertEquals("fixed", failed.getErrMsg());
        assertEquals(1, failed.getRollbackStatsList().size());
    }

    // ==================== UserInfoDto ====================

    @Test
    public void testUserInfoDto() {
        UserInfoDto dto = new UserInfoDto("admin", "pwd", "Super", "enable");
        assertEquals("admin", dto.getUsername());
        assertEquals("pwd", dto.getPassword());
        assertEquals("Super", dto.getUserType());
        assertEquals("enable", dto.getUserStatus());

        UserInfo userInfo = UserInfo.of("user2", "pwd2", "Normal", "disable");
        UserInfoDto other = new UserInfoDto().setUserInfo(userInfo);
        assertEquals("user2", other.getUsername());
        assertEquals("pwd2", other.getPassword());
        assertEquals("Normal", other.getUserType());
        assertEquals("disable", other.getUserStatus());

        other.setUsername("admin");
        other.setPassword("pwd");
        other.setUserType("Super");
        other.setUserStatus("enable");
        assertEquals(dto, other);
        assertEquals(dto.hashCode(), other.hashCode());
        assertTrue(dto.toString().contains("admin"));
    }

    // ==================== MetricsDataSourceConfig ====================

    @Test
    public void testMetricsDataSourceConfig() {
        MetricsDataSourceConfig config = new MetricsDataSourceConfig();
        assertEquals("PROMETHEUS", config.getProviderType());
        config.setName("prom");
        config.setUrl("http://prometheus:9090");
        config.setAuthType("basic");
        config.setUsername("user");
        config.setPassword("pass");
        config.setBearerToken("token");
        config.setProviderType("THANOS");
        config.setTlsEnabled(true);
        config.setDefaultLabels(Collections.singletonMap("cluster", "broker-a"));
        config.setScrapeInterval(15);
        config.setEnabled(true);

        assertEquals("prom", config.getName());
        assertEquals("http://prometheus:9090", config.getUrl());
        assertEquals("basic", config.getAuthType());
        assertEquals("user", config.getUsername());
        assertEquals("pass", config.getPassword());
        assertEquals("token", config.getBearerToken());
        assertEquals("THANOS", config.getProviderType());
        assertTrue(config.isTlsEnabled());
        assertEquals("broker-a", config.getDefaultLabels().get("cluster"));
        assertEquals(15, config.getScrapeInterval());
        assertTrue(config.isEnabled());
        assertTrue(config.toString().contains("prom"));
        assertNotEquals(config, new MetricsDataSourceConfig());
    }

    // ==================== MessagePageTask ====================

    @Test
    public void testMessagePageTask() {
        Page<MessageView> page = new PageImpl<>(Collections.emptyList());
        QueueOffsetInfo offsetInfo = new QueueOffsetInfo();
        MessagePageTask task = new MessagePageTask(page, Collections.singletonList(offsetInfo));
        assertSame(page, task.getPage());
        assertEquals(1, task.getQueueOffsetInfos().size());

        Page<MessageView> newPage = new PageImpl<>(Collections.emptyList());
        task.setPage(newPage);
        task.setQueueOffsetInfos(Collections.emptyList());
        assertSame(newPage, task.getPage());
        assertTrue(task.getQueueOffsetInfos().isEmpty());
        assertNotNull(task.toString());
    }

    // ==================== MetricsSelfCheckResult / CheckItem ====================

    @Test
    public void testMetricsSelfCheckResultAndCheckItem() {
        CheckItem item = new CheckItem("datasource", true, "INFO", "ok");
        assertEquals("datasource", item.getName());
        assertTrue(item.isPassed());
        assertEquals("INFO", item.getSeverity());
        assertEquals("ok", item.getMessage());

        CheckItem other = new CheckItem();
        other.setName("datasource");
        other.setPassed(true);
        other.setSeverity("INFO");
        other.setMessage("ok");
        assertEquals(item, other);
        assertEquals(item.hashCode(), other.hashCode());
        assertTrue(item.toString().contains("datasource"));

        MetricsSelfCheckResult result = new MetricsSelfCheckResult();
        result.setTimestamp(123L);
        result.setChecks(Collections.singletonList(item));
        result.setTotalChecks(1);
        result.setPassedChecks(1);
        result.setFailedChecks(0);
        result.setHealthy(true);
        result.setSummary("all good");

        assertEquals(123L, result.getTimestamp());
        assertEquals(1, result.getChecks().size());
        assertEquals(1, result.getTotalChecks());
        assertEquals(1, result.getPassedChecks());
        assertEquals(0, result.getFailedChecks());
        assertTrue(result.isHealthy());
        assertEquals("all good", result.getSummary());
        assertTrue(result.toString().contains("all good"));
    }
}
