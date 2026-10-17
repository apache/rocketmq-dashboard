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
package org.apache.rocketmq.studio.cluster.nameserver;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.apache.rocketmq.studio.persistence.entity.RmqNameserver;
import org.apache.rocketmq.studio.persistence.mapper.RmqNameserverMapper;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.Mockito.mock;

class NameserverRegistryPersistenceTest {

    private SqlSession session;
    private RmqNameserverMapper mapper;

    @BeforeEach
    void setUp() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:registry-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE");
        MybatisConfiguration configuration = new MybatisConfiguration();
        configuration.setEnvironment(new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RmqNameserverMapper.class);
        session = new MybatisSqlSessionFactoryBuilder().build(configuration).openSession(true);
        mapper = session.getMapper(RmqNameserverMapper.class);
        // Keep the database alive while JdbcTemplate prepares it on a separate connection.
        session.getConnection();
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE rmq_nameserver (
                    id BIGINT PRIMARY KEY, name VARCHAR(128) UNIQUE NOT NULL,
                    namesrv_addr VARCHAR(512) NOT NULL, k8s_namespace VARCHAR(128),
                    k8s_id VARCHAR(128), description TEXT, status VARCHAR(32),
                    gmt_create TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    gmt_modified TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP)
                """);
        RmqNameserver initial = new RmqNameserver();
        initial.setId(1L);
        initial.setName("original");
        initial.setNamesrvAddr("original:9876");
        initial.setK8sNamespace("original-namespace");
        initial.setK8sId("original-k8s");
        initial.setDescription("original description");
        initial.setStatus("healthy");
        mapper.insert(initial);
    }

    @AfterEach
    void tearDown() {
        if (session != null) {
            session.close();
        }
    }

    @Test
    void updateShouldNotClearFieldsFromACompetingReplacementTest() {
        NameserverRegistryService competing = new NameserverRegistryService(mapper);
        AtomicInteger writes = new AtomicInteger();
        RmqNameserverMapper interleaved = mock(RmqNameserverMapper.class, invocation -> {
            Object result = delegatesTo(mapper).answer(invocation);
            String method = invocation.getMethod().getName();
            if ((method.equals("updateById") || method.equals("update"))
                    && writes.incrementAndGet() == 1) {
                // Finish another request after the first SQL write commits, before the
                // original request can issue its optional-column clear.
                competing.update(UpdateNameserverRegistryDTO.builder()
                        .id(1L).name("competing").namesrvAddr("competing:9876")
                        .k8sNamespace("competing-namespace").k8sId("competing-k8s")
                        .description("competing description").build());
            }
            return result;
        });

        new NameserverRegistryService(interleaved).update(UpdateNameserverRegistryDTO.builder()
                .id(1L).name("first").namesrvAddr("first:9876").build());

        session.clearCache();
        RmqNameserver stored = mapper.selectById(1L);
        assertThat(stored.getName()).isEqualTo("competing");
        assertThat(stored.getNamesrvAddr()).isEqualTo("competing:9876");
        assertThat(stored.getK8sNamespace()).isEqualTo("competing-namespace");
        assertThat(stored.getK8sId()).isEqualTo("competing-k8s");
        assertThat(stored.getDescription()).isEqualTo("competing description");
        assertThat(writes).hasValue(1);
    }

    @Test
    void updateShouldPersistMixedSetAndClearedFieldsTest() {
        NameserverRegistryVO updated = new NameserverRegistryService(mapper).update(
                UpdateNameserverRegistryDTO.builder().id(1L).name("  renamed  ")
                        .namesrvAddr(" NS1:9876 ; NS2:9876 ")
                        .k8sId("replacement-k8s").build());

        session.clearCache();
        RmqNameserver stored = mapper.selectById(1L);
        assertThat(stored.getName()).isEqualTo("renamed");
        assertThat(stored.getNamesrvAddr()).isEqualTo("ns1:9876,ns2:9876");
        assertThat(stored.getK8sNamespace()).isNull();
        assertThat(stored.getK8sId()).isEqualTo("replacement-k8s");
        assertThat(stored.getDescription()).isNull();
        assertThat(stored.getStatus()).isEqualTo("healthy");
        assertThat(updated.getK8sId()).isEqualTo(stored.getK8sId());
        assertThat(updated.getDescription()).isNull();
    }
}
