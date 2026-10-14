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
package org.apache.rocketmq.studio.instance;

import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.TopicType;
import org.apache.rocketmq.studio.provider.apache.RocketMQLiteTopicProvider;
import org.apache.rocketmq.remoting.protocol.subscription.SubscriptionGroupConfig;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.JdbcRowValues;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Kind;
import org.apache.rocketmq.studio.instance.ResourceOwnershipGuard.Resource;
import org.apache.rocketmq.studio.provider.apache.RocketMQDefaultClusterResolver;
import org.apache.rocketmq.studio.provider.apache.RocketMQAdminClientImpl;
import org.apache.rocketmq.studio.provider.apache.RocketMQMessageProvider;
import org.apache.rocketmq.studio.provider.apache.RocketMQProperties;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.ops.audit.AuditService;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.entity.RmqTopic;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.instance.topic.SendMessageDTO;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.instance.message.DirectConsumeMessageDTO;
import org.apache.rocketmq.common.TopicConfig;
import org.apache.rocketmq.remoting.protocol.body.ClusterInfo;
import org.apache.rocketmq.remoting.protocol.route.BrokerData;
import org.apache.rocketmq.remoting.protocol.route.QueueData;
import org.apache.rocketmq.remoting.protocol.route.TopicRouteData;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

class ResourceOwnershipGuardTest {
    private JdbcDataSource dataSource;
    private JdbcTemplate jdbc;
    private DataSourceTransactionManager transactions;
    private ResourceOwnershipGuard guard;
    private InstanceResolver resolver;
    private RocketMQDefaultClusterResolver defaultClusters;
    private InstanceVO first;
    private InstanceVO second;
    private final Resource topic = new Resource(Kind.TOPIC, "orders");

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:ownership-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;LOCK_TIMEOUT=5000");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        transactions = new DataSourceTransactionManager(dataSource);
        resolver = mock(InstanceResolver.class);
        defaultClusters = mock(RocketMQDefaultClusterResolver.class);
        guard = new ResourceOwnershipGuard(jdbc, transactions, resolver);
        jdbc.update("INSERT INTO rmq_nameserver (name, namesrv_addr, status) VALUES ('ns-test', 'same:9876', 'healthy')");
        jdbc.update("INSERT INTO rmq_instance (name, type, endpoint, vendor, nameserver_id) VALUES ('first', 'DIRECT', 'same:9876', 'APACHE', 1)");
        jdbc.update("INSERT INTO rmq_instance (name, type, endpoint, vendor, nameserver_id) VALUES ('second', 'DIRECT', 'same:9876', 'APACHE', 1)");
        first = instance("first");
        second = instance("second");
    }

    @Test
    void missingInstanceAndCanonicalIdentifierTest() {
        assertCode(() -> guard.requireInstance("  "), 400);
        when(resolver.findByIdentifier("1")).thenReturn(Optional.of(first));
        assertThat(guard.requireInstance(" 1 ").getName()).isEqualTo("first");
    }

    @Test
    void sameNameserverDifferentInstanceConflictBeforeRemoteTest() {
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        AtomicInteger remote = new AtomicInteger();
        assertCode(() -> guard.write(second, topic, "cluster", true, "NORMAL", remote::incrementAndGet), 409);
        assertThat(remote).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isEqualTo(1);
    }

    @Test
    void partialFailureKeepsCommittedReservationAndAllowsOwnerRetryTest() {
        AtomicInteger remote = new AtomicInteger();
        assertThatThrownBy(() -> guard.write(first, topic, "cluster", true, "FIFO", () -> {
            remote.incrementAndGet();
            throw new BusinessException(502, "Second broker failed");
        })).isInstanceOf(BusinessException.class);
        assertThat(jdbc.queryForObject("SELECT status FROM rmq_instance_topic", String.class)).isEqualTo("PENDING");
        assertCode(() -> guard.write(second, topic, "cluster", true, "NORMAL", remote::incrementAndGet), 409);
        guard.write(first, topic, "cluster", true, "FIFO", remote::incrementAndGet);
        assertThat(remote).hasValue(2);
    }

    @Test
    void concurrentClaimsPermitOnlyOneInstancesRemoteCallsTest() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger remote = new AtomicInteger();
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> claim(start, first, remote));
            var b = executor.submit(() -> claim(start, second, remote));
            start.countDown();
            assertThat(List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(200, 409);
        }
        assertThat(remote).hasValue(1);
    }

    @Test
    void instanceDeletionWaitsForClaimAndCannotDropReservationTest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var write = executor.submit(() -> guard.write(first, topic, "cluster", true, "NORMAL", () -> {
                entered.countDown();
                await(release);
                return null;
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var deletion = executor.submit(() -> {
                assertCode(() -> new TransactionTemplate(transactions).executeWithoutResult(status -> {
                    guard.lockForInstanceDeletion(first.getId());
                    jdbc.update("DELETE FROM rmq_instance WHERE id = ?", first.getId());
                }), 409);
            });
            release.countDown();
            write.get(10, TimeUnit.SECONDS);
            deletion.get(10, TimeUnit.SECONDS);
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance", Integer.class)).isEqualTo(2);
    }

    @Test
    void deletePartialFailureDoesNotReleaseOwnerTest() {
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        assertThatThrownBy(() -> guard.write(first, topic, "cluster", false, null, () -> {
            jdbc.update("DELETE FROM rmq_instance_topic WHERE name = 'orders'");
            throw new BusinessException(502, "NameServer failed");
        })).isInstanceOf(BusinessException.class);
        assertCode(() -> guard.checkSlot(second, topic, null, true), 409);
    }

    @Test
    void derivedTopicsUseGroupOwnershipWithoutTopicRegistrationTest() {
        Resource group = new Resource(Kind.GROUP, "buyers");
        guard.write(first, group, "cluster", true, null, () -> null);
        assertThat(guard.topicResource("%DLQ%buyers")).isEqualTo(group);
        assertThat(guard.topicResource("%RETRY%buyers")).isEqualTo(group);
        assertCode(() -> guard.withOwned(second, List.of(guard.topicResource("%DLQ%buyers")), () -> null), 409);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isZero();
    }

    @Test
    void sameNameInDifferentDomainsCoexistsTest() {
        jdbc.update("INSERT INTO rmq_nameserver (name, namesrv_addr, status) VALUES ('ns-other', 'other:9876', 'healthy')");
        jdbc.update("INSERT INTO rmq_instance (name, type, endpoint, vendor, nameserver_id)"
                + " VALUES ('third', 'DIRECT', 'other:9876', 'APACHE', 2)");
        InstanceVO third = instance("third");
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        guard.write(third, topic, "cluster", true, "NORMAL", () -> null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isEqualTo(2);
    }

    @Test
    void sameNameOnDifferentClustersOfOneDomainCoexistsTest() {
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        guard.write(second, topic, "cluster-b", true, "NORMAL", () -> null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isEqualTo(2);
        assertThat(guard.slots(first, topic)).hasSize(2);
    }

    @Test
    void sameSlotSecondInstanceIsRejectedTest() {
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        assertCode(() -> guard.write(second, topic, "cluster-a", true, "NORMAL", () -> null), 409);
    }

    @Test
    void sameSlotSameInstanceIsAnUpsertTest() {
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isEqualTo(1);
    }

    @Test
    void releasedSlotCanBeClaimedByAnotherInstanceTest() {
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        jdbc.update("DELETE FROM rmq_instance_topic WHERE name = 'orders'");
        guard.write(second, topic, "cluster-a", true, "NORMAL", () -> null);
        assertThat(jdbc.queryForObject("SELECT instance_id FROM rmq_instance_topic", String.class)).isEqualTo("second");
    }

    @Test
    void foreignOwnerInSameDomainIsRejectedBeforeAnyWriteTest() {
        jdbc.update("INSERT INTO rmq_instance_topic (name, cluster_id, domain, instance_id) VALUES ('orders', 'cluster', 'ns:1', 'first')");
        assertCode(() -> guard.checkSlot(second, topic, null, true), 409);
        assertThat(guard.checkSlot(first, topic, null, true).instanceId()).isEqualTo("first");
    }

    @Test
    void migrationIsIdempotentAndLegacyIndexesAreReplacedTest() throws Exception {
        legacyIndexes();
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        jdbc.execute("DROP TABLE rmq_instance_ownership_lock");
        var migration = new ResourceOwnershipSchemaMigration(dataSource);
        migration.afterPropertiesSet();
        migration.afterPropertiesSet();
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        // The slot key still rejects a second owner of the very same (domain, cluster, name)...
        assertThatThrownBy(() -> jdbc.update("INSERT INTO rmq_instance_topic (name, instance_id, cluster_id, domain)"
                + " VALUES ('orders', 'second', 'cluster', 'ns:1')")).isInstanceOf(org.springframework.dao.DuplicateKeyException.class);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isEqualTo(1);
        // ...while the same name on another broker cluster of the domain is exactly what the model allows.
        jdbc.update("INSERT INTO rmq_instance_topic (name, instance_id, cluster_id, domain)"
                + " VALUES ('orders', 'second', 'other', 'ns:1')");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isEqualTo(2);
    }

    @Test
    void migrationRejectsDuplicatesWithoutDiscardingAnyRowsTest() {
        legacyIndexes();
        jdbc.update("INSERT INTO rmq_instance_group (name, instance_id, cluster_id, domain) VALUES ('buyers', 'first', 'a', 'ns:1')");
        jdbc.update("INSERT INTO rmq_instance_group (name, instance_id, cluster_id, domain) VALUES ('buyers', 'second', 'a', 'ns:1')");
        assertThatThrownBy(() -> new ResourceOwnershipSchemaMigration(dataSource).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("manual resolution");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_group", Integer.class)).isEqualTo(2);
    }

    @Test
    void migrationRejectsUnknownLegacyOwnerWithoutChangingItTest() {
        jdbc.update("INSERT INTO rmq_instance_topic (name, cluster_id) VALUES ('orders', 'unknown')");
        assertThatThrownBy(() -> new ResourceOwnershipSchemaMigration(dataSource).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be determined");
        assertThat(jdbc.queryForObject("SELECT instance_id FROM rmq_instance_topic", String.class)).isEmpty();
    }

    @Test
    void apacheCrudUsesCommittedOwnershipAndOnlySecondClusterMasterTest() throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        jdbc.update("INSERT INTO rmq_instance (name, type, endpoint, vendor, nameserver_id) VALUES ('prod-b', 'DIRECT', 'same:9876', 'APACHE', 1)");
        InstanceVO registered = instance("prod-b");
        when(resolver.findByIdentifier("prod-b")).thenReturn(Optional.of(registered));
        when(broker.examineBrokerClusterInfo()).thenReturn(twoClusters());
        TopicVO request = new TopicVO();
        request.setName("orders");
        request.setClusterId("cluster-b");
        admin.createTopic("prod-b", request);

        assertThat(guard.checkSlot(registered, topic, null, true).clusterId()).isEqualTo("cluster-b");
        verify(broker).createAndUpdateTopicConfig(eq("10.0.1.1:10911"), any(TopicConfig.class));
        verify(broker, never()).createAndUpdateTopicConfig(eq("10.0.0.1:10911"), any(TopicConfig.class));
        verify(broker, never()).createAndUpdateTopicConfig(eq("10.0.1.2:10911"), any(TopicConfig.class));
    }

    @Test
    void apacheWritesRejectForeignOwnerBeforeAnyBrokerOrProducerCallTest() throws Exception {
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        guard.write(first, new Resource(Kind.GROUP, "buyers"), "cluster-a", true, null, () -> null);
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("second")).thenReturn(Optional.of(second));
        TopicVO request = new TopicVO();
        request.setName("orders");
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("buyers");
        group.setInstanceId("second");
        assertCode(() -> admin.createTopic("second", request), 409);
        assertCode(() -> admin.updateTopic("second", request), 409);
        assertCode(() -> admin.deleteTopic("second", "orders"), 409);
        assertCode(() -> admin.createConsumerGroup(group), 409);
        assertCode(() -> admin.updateConsumerGroup(group), 409);
        assertCode(() -> admin.deleteConsumerGroup("second", "buyers"), 409);
        assertCode(() -> admin.sendMessage(SendMessageDTO.builder()
                .instanceId("second").topic("orders").body("hello").build()), 409);
        assertCode(() -> admin.resetOffset("second", "buyers", 1L, "orders"), 409);
        DirectConsumeMessageDTO direct = new DirectConsumeMessageDTO();
        direct.setInstanceId("second");
        direct.setTopic("orders");
        direct.setConsumerGroup("buyers");
        direct.setClientId("client");
        direct.setMsgId("msg");
        assertCode(() -> new RocketMQMessageProvider(runtime, guard).consumeMessageDirectly(direct), 409);
        verify(broker, never()).createAndUpdateTopicConfig(anyString(), any());
        verify(broker, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        verify(broker, never()).deleteTopicInBroker(any(), anyString());
        verify(broker, never()).deleteSubscriptionGroup(anyString(), anyString(), anyBoolean());
    }

    @Test
    void registeredInstanceRejectsAmbiguousAndIncompleteTopologyTest() throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        TopicVO request = new TopicVO();
        request.setName("orders");
        ClusterInfo topology = twoClusters();
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        assertCode(() -> admin.createTopic("first", request), 409);
        topology.getClusterAddrTable().remove("cluster-b");
        topology.getBrokerAddrTable().get("broker-a").getBrokerAddrs().remove(0L);
        assertCode(() -> admin.createTopic("first", request), 409);
        verify(broker, never()).createAndUpdateTopicConfig(anyString(), any(TopicConfig.class));
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isZero();
    }

    @Test
    void incompleteClusterTableCannotMasqueradeAsUniqueTopologyTest() throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        ClusterInfo topology = twoClusters();
        topology.getClusterAddrTable().remove("cluster-a");
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        TopicVO request = new TopicVO();
        request.setName("orders");
        assertCode(() -> admin.createTopic("first", request), 409);
        topology.getBrokerAddrTable().get("broker-a").setCluster("cluster-b");
        assertCode(() -> admin.createTopic("first", request), 409);
        verify(broker, never()).createAndUpdateTopicConfig(anyString(), any());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isZero();
    }

    @Test
    void offsetResetUsesCanonicalNamesWithRealOwnershipTest() throws Exception {
        guard.write(first, topic, "cluster-b", true, "NORMAL", () -> null);
        guard.write(first, new Resource(Kind.GROUP, "buyers"), "cluster-b", true, null, () -> null);
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        ClusterInfo topology = twoClusters();
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        TopicRouteData route = new TopicRouteData();
        route.setBrokerDatas(List.of(topology.getBrokerAddrTable().get("broker-b")));
        QueueData queue = new QueueData();
        queue.setBrokerName("broker-b");
        route.setQueueDatas(List.of(queue));
        when(broker.examineTopicRouteInfo("orders")).thenReturn(route);
        admin.resetOffset(" first ", " buyers ", 123L, " orders ");
        verify(broker).resetOffsetNew("buyers", "orders", 123L);
    }

    @Test
    void occupiedInstanceCannotChangeConnectionButMayRotateCredentialTest() {
        guard.write(first, topic, "cluster-a", true, "NORMAL", () -> null);
        InstanceVO updated = instance("first");
        updated.setEndpoint("other:9876");
        assertCode(() -> new TransactionTemplate(transactions).executeWithoutResult(status ->
                guard.lockForInstanceUpdate(first, updated)), 409);
        updated.setEndpoint(first.getEndpoint());
        updated.setAdminCredentialRef("rotated");
        new TransactionTemplate(transactions).executeWithoutResult(status -> guard.lockForInstanceUpdate(first, updated));
    }

    @Test
    void migrationRejectsMissingExplicitOwnerWithoutDataLossTest() {
        jdbc.update("INSERT INTO rmq_instance_topic (name, instance_id, cluster_id) VALUES ('orders', 'gone', 'cluster')");
        assertThatThrownBy(() -> new ResourceOwnershipSchemaMigration(dataSource).afterPropertiesSet())
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be determined");
        assertThat(jdbc.queryForObject("SELECT instance_id FROM rmq_instance_topic", String.class)).isEqualTo("gone");
    }

    @Test
    void importExistingTopicRegistersWithoutOverwritingAndRejectsDifferencesTest() throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        ClusterInfo topology = twoClusters();
        topology.getClusterAddrTable().remove("cluster-a");
        topology.getBrokerAddrTable().remove("broker-a");
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        TopicConfig physical = new TopicConfig("orders", 8, 8, 6);
        when(broker.examineTopicConfig("10.0.1.1:10911", "orders")).thenReturn(physical);
        TopicVO request = new TopicVO();
        request.setName("orders");
        request.setType(TopicType.NORMAL);
        admin.importTopic(" first ", request);
        assertThat(guard.checkSlot(first, topic, null, true).clusterId()).isEqualTo("cluster-b");
        assertThat(jdbc.queryForObject("SELECT status FROM rmq_instance_topic", String.class)).isEqualTo("ACTIVE");
        request.setReadQueues(16);
        assertCode(() -> admin.importTopic("first", request), 409);
        assertThat(physical.getReadQueueNums()).isEqualTo(8);
        verify(broker, never()).createAndUpdateTopicConfig(anyString(), any());
        assertThat(jdbc.queryForObject("SELECT read_queue_nums FROM rmq_instance_topic", Integer.class)).isEqualTo(8);
    }

    @ParameterizedTest
    @EnumSource(TopicType.class)
    void importEveryTopicTypeUsesPhysicalReadAttributeInsteadOfWriteAttributeTest(TopicType type) throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        ClusterInfo topology = twoClusters();
        topology.getClusterAddrTable().remove("cluster-a");
        topology.getBrokerAddrTable().remove("broker-a");
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        TopicConfig physical = new TopicConfig("orders", 8, 8, 6);
        physical.setAttributes(Map.of("message.type", type.name()));
        when(broker.examineTopicConfig("10.0.1.1:10911", "orders")).thenReturn(physical);
        TopicVO request = new TopicVO();
        request.setName("orders");
        request.setType(type);
        admin.importTopic("first", request);
        assertThat(guard.checkSlot(first, topic, null, true).topicType()).isEqualTo(type.name());
        physical.setAttributes(Map.of("message.type", type == TopicType.NORMAL ? "FIFO" : "NORMAL"));
        assertCode(() -> admin.importTopic("first", request), 409);
        verify(broker, never()).createAndUpdateTopicConfig(anyString(), any());
    }

    @Test
    void importNewTopicCreatesOnlyAfterCommittedClaimAndRetainsPartialFailureTest() throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        when(resolver.findByIdentifier("second")).thenReturn(Optional.of(second));
        ClusterInfo topology = twoClusters();
        topology.getClusterAddrTable().remove("cluster-a");
        topology.getBrokerAddrTable().remove("broker-a");
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        org.mockito.Mockito.doAnswer(call -> {
            assertThat(guard.checkSlot(first, topic, null, true).instanceId()).isEqualTo("first");
            throw new BusinessException(502, "Remote already partially succeeded");
        }).when(broker).createAndUpdateTopicConfig(anyString(), any());
        TopicVO request = new TopicVO();
        request.setName("orders");
        assertCode(() -> admin.importTopic("first", request), 502);
        assertCode(() -> admin.importTopic("second", request), 409);
        assertThat(jdbc.queryForObject("SELECT status FROM rmq_instance_topic", String.class)).isEqualTo("PENDING");
        verify(broker).createAndUpdateTopicConfig(anyString(), any());
    }

    @Test
    void importExistingGroupPreservesPhysicalSettingsAndRejectsDifferencesTest() throws Exception {
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var admin = apacheAdmin(runtime, broker);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        ClusterInfo topology = twoClusters();
        topology.getClusterAddrTable().remove("cluster-a");
        topology.getBrokerAddrTable().remove("broker-a");
        when(broker.examineBrokerClusterInfo()).thenReturn(topology);
        SubscriptionGroupConfig physical = new SubscriptionGroupConfig();
        physical.setGroupName("buyers");
        physical.setRetryMaxTimes(16);
        physical.setConsumeEnable(false);
        physical.setRetryQueueNums(7);
        when(broker.examineSubscriptionGroupConfig("10.0.1.1:10911", "buyers")).thenReturn(physical);
        ConsumerGroupVO group = new ConsumerGroupVO();
        group.setName("buyers");
        group.setInstanceId("first");
        admin.importConsumerGroup(group);
        group.setRetryMaxTimes(20);
        assertCode(() -> admin.importConsumerGroup(group), 409);
        assertThat(physical.isConsumeEnable()).isFalse();
        assertThat(physical.getRetryQueueNums()).isEqualTo(7);
        verify(broker, never()).createAndUpdateSubscriptionGroupConfig(anyString(), any());
        assertThat(jdbc.queryForObject("SELECT status FROM rmq_instance_group", String.class)).isEqualTo("ACTIVE");
    }


    @Test
    void liteTtlUsesOwnedSecondClusterAndPreservesOwnershipOnFailureTest() throws Exception {
        guard.write(first, topic, "cluster-b", true, "LITE", () -> null);
        when(resolver.findByIdentifier("first")).thenReturn(Optional.of(first));
        when(resolver.findByIdentifier("second")).thenReturn(Optional.of(second));
        var runtime = mock(RuntimeAdminClientResolver.class);
        var broker = mock(DefaultMQAdminExt.class);
        var factory = mock(MqAdminExtFactory.class);
        var lite = new RocketMQLiteTopicProvider(factory, new RocketMQProperties(), runtime, guard);
        when(runtime.execute(any(InstanceVO.class), any())).thenAnswer(call ->
                call.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(broker));
        when(broker.examineBrokerClusterInfo()).thenReturn(twoClusters());
        when(broker.examineTopicConfig("10.0.1.1:10911", "orders")).thenAnswer(call -> {
            TopicConfig physical = new TopicConfig("orders");
            physical.setAttributes(Map.of("message.type", "LITE"));
            return physical;
        });
        lite.extendTTL(" first ", " orders ", 60_000L);
        assertCode(() -> lite.extendTTL("second", "orders", 60_000L), 409);
        verify(broker).createAndUpdateTopicConfig(eq("10.0.1.1:10911"), any());
        verify(broker, never()).createAndUpdateTopicConfig(eq("10.0.0.1:10911"), any());
        verify(broker, never()).createAndUpdateTopicConfig(eq("10.0.1.2:10911"), any());
        org.mockito.Mockito.doThrow(new BusinessException(502, "TTL remote write failed"))
                .when(broker).createAndUpdateTopicConfig(eq("10.0.1.1:10911"), any());
        assertCode(() -> lite.extendTTL("first", "orders", 120_000L), 502);
        assertCode(() -> lite.extendTTL("second", "orders", 120_000L), 409);
        verifyNoInteractions(factory);
        assertThat(guard.checkSlot(first, topic, null, true).instanceId()).isEqualTo("first");
    }

    @Test
    void registeredClaimAndRegistrationSerializeWithoutChangingOwnershipTest() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var write = executor.submit(() -> guard.write(first, topic, "cluster", true, "NORMAL", () -> {
                entered.countDown();
                await(release);
                return null;
            }));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            var register = executor.submit(() -> assertCode(() -> guard.withInstanceRegistration("first", () ->
                    jdbc.update("INSERT INTO rmq_instance (name, type, endpoint, vendor, nameserver_id) VALUES ('dup', 'DIRECT', 'other:9876', 'APACHE', 1)")), 409));
            release.countDown();
            write.get(10, TimeUnit.SECONDS);
            register.get(10, TimeUnit.SECONDS);
        }
        assertThat(guard.checkSlot(first, topic, null, true).instanceId()).isEqualTo("first");
    }

    @Test
    void staleRegisteredSnapshotCannotWriteAfterConnectionChangeOrDeletionTest() {
        AtomicInteger remote = new AtomicInteger();
        InstanceVO updated = instance("first");
        updated.setEndpoint("other:9876");
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            guard.lockForInstanceUpdate(first, updated);
            jdbc.update("UPDATE rmq_instance SET endpoint = ? WHERE id = ?", updated.getEndpoint(), first.getId());
        });
        assertCode(() -> guard.write(first, topic, "cluster", true, "NORMAL", remote::incrementAndGet), 409);
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            guard.lockForInstanceDeletion(first.getId());
            jdbc.update("DELETE FROM rmq_instance WHERE id = ?", first.getId());
        });
        assertCode(() -> guard.write(updated, topic, "cluster", true, "NORMAL", remote::incrementAndGet), 409);
        assertThat(remote).hasValue(0);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM rmq_instance_topic", Integer.class)).isZero();
    }

    @Test
    void migrationAcceptsTrustedVirtualOwnerWithoutRewritingLegacyDataTest() throws Exception {
        jdbc.update("INSERT INTO rmq_instance_topic (name, instance_id, cluster_id) VALUES ('orders', 'first', 'cluster')");
        var migration = new ResourceOwnershipSchemaMigration(dataSource);
        migration.afterPropertiesSet();
        migration.afterPropertiesSet();
        assertThat(jdbc.queryForObject("SELECT domain FROM rmq_instance_topic", String.class)).isEqualTo("ns:1");
        assertCode(() -> guard.withInstanceRegistration("first", () -> null), 409);
    }

    @Test
    void migrationConcurrentStartupIsIdempotentTest() throws Exception {
        legacyIndexes();
        jdbc.execute("DROP TABLE rmq_instance_ownership_lock");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            java.util.concurrent.Callable<Void> migrate = () -> {
                await(start);
                new ResourceOwnershipSchemaMigration(dataSource).afterPropertiesSet();
                return null;
            };
            var a = executor.submit(migrate);
            var b = executor.submit(migrate);
            start.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);
        }
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        assertCode(() -> guard.write(second, topic, "cluster", true, "NORMAL", () -> null), 409);
    }

    @Test
    void legacyApacheVendorSnapshotAcceptsBlankButRejectsChangedVendorTest() {
        for (String vendor : new String[] {null, "", " "}) {
            jdbc.update("UPDATE rmq_instance SET vendor = ? WHERE name = 'first'", vendor);
            guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        }
        jdbc.update("UPDATE rmq_instance SET vendor = 'ALIYUN' WHERE name = 'first'");
        AtomicInteger remote = new AtomicInteger();
        assertCode(() -> guard.write(first, topic, "cluster", true, "NORMAL", remote::incrementAndGet), 409);
        assertThat(remote).hasValue(0);
    }

    @Test
    void registrationWinningRacePreventsStaleVirtualWriteTest() {
        guard.withInstanceRegistration("virtual", () -> jdbc.update(
                "INSERT INTO rmq_instance (name, type, endpoint) VALUES ('virtual','DIRECT','other:9876')"));
        AtomicInteger remote = new AtomicInteger();
        assertCode(() -> guard.write(InstanceVO.builder().name("virtual").build(), topic,
                "virtual", true, "NORMAL", remote::incrementAndGet), 409);
        assertThat(remote).hasValue(0);
    }

    private RocketMQAdminClientImpl apacheAdmin(RuntimeAdminClientResolver runtime, DefaultMQAdminExt broker) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqTopic.class);
        when(runtime.execute(anyString(), any())).thenAnswer(call ->
                call.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(broker));
        RmqTopicMapper mapper = mock(RmqTopicMapper.class);
        when(mapper.selectOne(any())).thenAnswer(call -> jdbc.query(
                "SELECT id, name, instance_id, cluster_id, topic_type FROM rmq_instance_topic WHERE name = 'orders'",
                (rs, row) -> {
                    RmqTopic entity = new RmqTopic();
                    entity.setId(rs.getLong(1));
                    entity.setName(rs.getString(2));
                    entity.setInstanceId(rs.getString(3));
                    entity.setClusterId(rs.getString(4));
                    entity.setTopicType(rs.getString(5));
                    return entity;
                }).stream().findFirst().orElse(null));
        when(mapper.updateById(any(RmqTopic.class))).thenAnswer(call -> {
            RmqTopic entity = call.getArgument(0);
            return jdbc.update("UPDATE rmq_instance_topic SET status = ?, read_queue_nums = ?, write_queue_nums = ? WHERE id = ?",
                    entity.getStatus(), entity.getReadQueueNums(), entity.getWriteQueueNums(), entity.getId());
        });
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), RmqGroup.class);
        RmqGroupMapper groups = mock(RmqGroupMapper.class);
        when(groups.selectOne(any())).thenAnswer(call -> jdbc.query(
                "SELECT id, name, instance_id, cluster_id FROM rmq_instance_group WHERE name = 'buyers'", (rs, row) -> {
                    RmqGroup entity = new RmqGroup();
                    entity.setId(rs.getLong(1));
                    entity.setName(rs.getString(2));
                    entity.setInstanceId(rs.getString(3));
                    entity.setClusterId(rs.getString(4));
                    return entity;
                }).stream().findFirst().orElse(null));
        when(groups.updateById(any(RmqGroup.class))).thenAnswer(call -> {
            RmqGroup entity = call.getArgument(0);
            return jdbc.update("UPDATE rmq_instance_group SET status = ?, max_retry = ? WHERE id = ?",
                    entity.getStatus(), entity.getMaxRetry(), entity.getId());
        });
        return new RocketMQAdminClientImpl(mock(MqAdminExtFactory.class), new RocketMQProperties(), mapper,
                groups, mock(AuditService.class), runtime, mock(MqClientPool.class), guard);
    }

    private ClusterInfo twoClusters() {
        ClusterInfo info = new ClusterInfo();
        info.setClusterAddrTable(new HashMap<>(Map.of("cluster-a", Set.of("broker-a"), "cluster-b", Set.of("broker-b"))));
        info.setBrokerAddrTable(new HashMap<>(Map.of(
                "broker-a", new BrokerData("cluster-a", "broker-a", new HashMap<>(Map.of(0L, "10.0.0.1:10911"))),
                "broker-b", new BrokerData("cluster-b", "broker-b",
                        new HashMap<>(Map.of(0L, "10.0.1.1:10911", 1L, "10.0.1.2:10911"))))));
        return info;
    }

    /**
     * A reservation has to carry its own bookkeeping stamps. The shipped columns are declared
     * {@code DEFAULT CURRENT_TIMESTAMP}, which the database evaluates on its own clock, while the create
     * path stamps {@code gmt_modified} from the JVM clock — so a reservation that leans on the column
     * default stores a creation time taken from a different clock than the modification time, and the
     * two can disagree by the offset between the database and the studio (eight hours when the database
     * runs in UTC and the studio in Asia/Shanghai). The two columns are recreated without their defaults
     * here, so that "the reservation supplied the value" is what this test observes.
     */
    @Test
    void topicReservationSuppliesItsOwnBookkeepingStampsTest() {
        dropTimestampDefaults("topic");
        LocalDateTime before = LocalDateTime.now();
        guard.write(first, topic, "cluster", true, "NORMAL", () -> null);
        LocalDateTime after = LocalDateTime.now();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT gmt_create, gmt_modified FROM rmq_instance_topic WHERE name = ?", topic.name());
        assertStampedByTheStudioClock(row, before, after);
    }

    @Test
    void groupReservationSuppliesItsOwnBookkeepingStampsTest() {
        dropTimestampDefaults("group");
        Resource group = new Resource(Kind.GROUP, "orders-group");
        LocalDateTime before = LocalDateTime.now();
        guard.write(first, group, "cluster", true, null, () -> null);
        LocalDateTime after = LocalDateTime.now();
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT gmt_create, gmt_modified FROM rmq_instance_group WHERE name = ?", group.name());
        assertStampedByTheStudioClock(row, before, after);
    }

    /**
     * Both columns have to carry the value the reservation supplied, not merely a non-null one. Equality
     * locks the single {@code stampedAt} the two columns share, and the window proves the studio clock
     * produced them — a database default would land outside it whenever the two clocks disagree, which is
     * the hours-wide zone offset this fix removes. The one-second slack absorbs the second precision the
     * DATETIME columns are stored with, which floors the stamped value.
     */
    private static void assertStampedByTheStudioClock(Map<String, Object> row, LocalDateTime before, LocalDateTime after) {
        LocalDateTime created = JdbcRowValues.dateTimeValue(row, "gmt_create");
        LocalDateTime modified = JdbcRowValues.dateTimeValue(row, "gmt_modified");
        assertThat(created).as("gmt_create").isNotNull();
        assertThat(modified).as("gmt_modified").isNotNull();
        assertThat(created).as("gmt_create and gmt_modified come from one stampedAt").isEqualTo(modified);
        assertThat(created).isAfterOrEqualTo(before.minusSeconds(1));
        assertThat(modified).isBeforeOrEqualTo(after.plusSeconds(1));
    }

    /** Same table, but the two bookkeeping columns no longer fall back to the database clock. */
    private void dropTimestampDefaults(String kind) {
        jdbc.execute("ALTER TABLE rmq_instance_" + kind + " ALTER COLUMN gmt_create DROP DEFAULT");
        jdbc.execute("ALTER TABLE rmq_instance_" + kind + " ALTER COLUMN gmt_modified DROP DEFAULT");
    }

    private void legacyIndexes() {
        for (String kind : List.of("topic", "group")) {
            jdbc.execute("ALTER TABLE rmq_instance_" + kind + " DROP INDEX uk_" + kind + "_domain");
            jdbc.execute("CREATE UNIQUE INDEX uk_cluster_instance_" + kind + " ON rmq_instance_" + kind
                    + " (cluster_id, instance_id, name)");
        }
    }

    private int claim(CountDownLatch start, InstanceVO instance, AtomicInteger remote) {
        await(start);
        try {
            guard.write(instance, topic, "cluster", true, "NORMAL", remote::incrementAndGet);
            return 200;
        } catch (BusinessException failure) {
            return failure.getCode();
        }
    }

    private InstanceVO instance(String name) {
        InstanceVO instance = InstanceVO.builder().name(name).vendor(InstanceVendor.APACHE)
                .type(InstanceType.DIRECT)
                .endpoint(jdbc.queryForObject("SELECT endpoint FROM rmq_instance WHERE name = ?", String.class, name))
                .nameserverId(jdbc.queryForObject("SELECT nameserver_id FROM rmq_instance WHERE name = ?",
                        Long.class, name))
                .build();
        instance.setId(jdbc.queryForObject("SELECT id FROM rmq_instance WHERE name = ?", Long.class, name));
        return instance;
    }

    private static void assertCode(Runnable action, int code) {
        assertThatThrownBy(action::run).isInstanceOf(BusinessException.class)
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(code));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for concurrency test");
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }
}
