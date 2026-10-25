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

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.spring.MybatisSqlSessionFactoryBean;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.common.domain.enums.InstanceType;
import org.apache.rocketmq.studio.common.domain.enums.InstanceVendor;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.util.RegionNames;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.credential.CloudCredentialRepository;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.cache.CacheManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.spy;

class InstanceUpdateConcurrencyTest {
    private JdbcTemplate jdbc;
    private TransactionTemplate transaction;
    private TransactionTemplate competingTransaction;
    private InstanceRepository repository;
    private ResourceOwnershipGuard guard;
    private InstanceService service;

    @BeforeEach
    void setUp() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:instance-update-" + UUID.randomUUID()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE");
        new ResourceDatabasePopulator(new ClassPathResource("db/schema.sql")).execute(dataSource);
        jdbc = new JdbcTemplate(dataSource);
        DataSourceTransactionManager transactions = new DataSourceTransactionManager(dataSource);
        transaction = new TransactionTemplate(transactions);
        competingTransaction = new TransactionTemplate(transactions);
        competingTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        guard = new ResourceOwnershipGuard(jdbc, transactions, mock(InstanceResolver.class));
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.addMapper(RmqInstanceMapper.class);
        MybatisSqlSessionFactoryBean factory = new MybatisSqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setConfiguration(configuration);
        SqlSessionTemplate session = new SqlSessionTemplate(factory.getObject());
        repository = spy(new MybatisPlusInstanceRepository(session.getMapper(RmqInstanceMapper.class),
                mock(RmqTopicMapper.class), mock(RmqGroupMapper.class)));
        service = new InstanceService(repository, mock(CloudCredentialRepository.class),
                mock(InstanceProviderRegistry.class), mock(MqAdminExtFactory.class), mock(MqClientPool.class),
                mock(OperationAuditService.class), mock(SettingsRepository.class), mock(CacheManager.class),
                mock(RegionNames.class), guard);
        jdbc.update("INSERT INTO rmq_instance (id, name, type, endpoint, vendor, remark)"
                + " VALUES (1, 'production', 'DIRECT', 'old:9876', 'APACHE', 'old remark')");
    }

    @AfterEach
    void tearDown() {
        service.shutdownCountRunner();
    }

    @ParameterizedTest
    @CsvSource({"'',committed remark", "old remark,committed remark", "old remark,''"})
    void staleEndpointUpdateMustNotOverwriteACommittedRemarkTest(String initialRemark, String committedRemark) {
        jdbc.update("UPDATE rmq_instance SET remark = ? WHERE id = 1", initialRemark);
        changeRemarkAfterSnapshot(committedRemark);

        Throwable failure = catchThrowable(() -> transaction.execute(status -> service.updateInstance(endpointUpdate())));

        assertThat(readInstance().getRemark()).isEqualTo(committedRemark);
        assertThat(readInstance().getEndpoint()).isEqualTo("old:9876");
        assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
                exception -> assertThat(exception.getCode()).isEqualTo(409));
        verify(repository, never()).save(argThat(instance -> "new:9876".equals(instance.getEndpoint())));
    }

    @Test
    void anUnchangedNullRemarkAllowsAnEndpointUpdateTest() {
        jdbc.update("UPDATE rmq_instance SET remark = NULL WHERE id = 1");

        InstanceVO saved = transaction.execute(status -> service.updateInstance(endpointUpdate()));

        assertThat(saved.getRemark()).isNull();
        assertThat(readInstance().getRemark()).isNull();
        assertThat(readInstance().getEndpoint()).isEqualTo("new:9876");
    }

    @Test
    void retryWithAFreshSnapshotPreservesTheCommittedRemarkTest() {
        changeRemarkAfterSnapshot("committed remark");
        Throwable failure = catchThrowable(() -> transaction.execute(status -> service.updateInstance(endpointUpdate())));
        assertThat(failure).isInstanceOf(BusinessException.class);

        InstanceVO saved = transaction.execute(status -> service.updateInstance(endpointUpdate()));

        assertThat(saved.getRemark()).isEqualTo("committed remark");
        assertThat(readInstance().getRemark()).isEqualTo("committed remark");
        assertThat(readInstance().getEndpoint()).isEqualTo("new:9876");
    }

    @Test
    void aRemarkChangeMustNotInvalidateAResourceWriteTest() {
        InstanceVO snapshot = readInstance();
        ResourceOwnershipGuard.Resource topic = new ResourceOwnershipGuard.Resource(
                ResourceOwnershipGuard.Kind.TOPIC, "orders");
        guard.write(snapshot, topic, "cluster", true, "NORMAL", () -> null);
        transaction.execute(status -> service.updateInstance(remarkUpdate("committed remark")));

        String result = guard.withOwned(snapshot, List.of(topic), () -> "resource write admitted");

        assertThat(result).isEqualTo("resource write admitted");
        assertThat(readInstance().getRemark()).isEqualTo("committed remark");
    }

    private void changeRemarkAfterSnapshot(String remark) {
        AtomicBoolean changePending = new AtomicBoolean(true);
        doAnswer(call -> {
            Object snapshot = call.callRealMethod();
            // A separate transaction commits after the pending update read its snapshot,
            // but before the real ownership guard takes its database locks.
            if (changePending.getAndSet(false)) {
                competingTransaction.execute(status -> service.updateInstance(remarkUpdate(remark)));
            }
            return snapshot;
        }).when(repository).findById(1L);
    }

    private InstanceVO readInstance() {
        return jdbc.queryForObject("SELECT id, name, type, endpoint, vendor, remark FROM rmq_instance WHERE id = 1",
                (row, index) -> {
                    InstanceVO instance = InstanceVO.builder().name(row.getString("name"))
                            .type(InstanceType.valueOf(row.getString("type")))
                            .endpoint(row.getString("endpoint"))
                            .vendor(InstanceVendor.valueOf(row.getString("vendor")))
                            .remark(row.getString("remark")).build();
                    instance.setId(row.getLong("id"));
                    return instance;
                });
    }

    private InstanceVO endpointUpdate() {
        InstanceVO update = InstanceVO.builder().endpoint("new:9876").build();
        update.setId(1L);
        return update;
    }

    private InstanceVO remarkUpdate(String remark) {
        InstanceVO update = InstanceVO.builder().remark(remark).build();
        update.setId(1L);
        return update;
    }
}
