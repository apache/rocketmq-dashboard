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
package org.apache.rocketmq.studio.persistence.postgresql;

import org.apache.rocketmq.studio.persistence.entity.RmqInstance;
import org.apache.rocketmq.studio.persistence.mapper.RmqInstanceMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("postgresql")
@Testcontainers(disabledWithoutDocker = true)
class PostgresqlPersistenceIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rocketmq")
            .withUsername("rocketmq")
            .withPassword("rocketmq");

    @DynamicPropertySource
    static void configurePostgresql(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RmqInstanceMapper instanceMapper;

    @Test
    void startsWithPostgresqlSchemaAndSupportsMybatisCrud() throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
        }
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success", Integer.class))
                .isEqualTo(1);

        RmqInstance instance = new RmqInstance();
        instance.setId("postgres-instance");
        instance.setName("PostgreSQL-backed instance");
        instance.setType("DIRECT");
        instance.setEndpoint("127.0.0.1:9876");

        assertThat(instanceMapper.insert(instance)).isEqualTo(1);
        RmqInstance stored = instanceMapper.selectById("postgres-instance");
        assertThat(stored).isNotNull();
        assertThat(stored.getName()).isEqualTo("PostgreSQL-backed instance");
        assertThat(stored.getVendor()).isEqualTo("APACHE");
        assertThat(instanceMapper.deleteById("postgres-instance")).isEqualTo(1);
        assertThat(instanceMapper.selectById("postgres-instance")).isNull();
    }
}
