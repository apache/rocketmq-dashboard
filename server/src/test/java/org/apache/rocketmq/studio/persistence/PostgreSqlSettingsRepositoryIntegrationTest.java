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
package org.apache.rocketmq.studio.persistence;

import org.apache.rocketmq.studio.settings.DataSourceVO;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
@ActiveProfiles("postgres")
@SpringBootTest
class PostgreSqlSettingsRepositoryIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("rocketmq_studio")
            .withUsername("rocketmq_studio")
            .withPassword("rocketmq_studio");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private SettingsRepository settingsRepository;

    @Test
    void persistsGeneralSettingsAndDataSources() {
        GeneralSettingsVO settings = GeneralSettingsVO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(false)
                .notifySound(false)
                .sessionTimeout(15)
                .requireLogin(true)
                .llmProvider("openai")
                .apiKey("test-key")
                .model("gpt-4")
                .baseUrl("https://example.test/v1")
                .build();
        settingsRepository.saveGeneralSettings(settings);

        DataSourceVO dataSource = DataSourceVO.builder()
                .key("postgres-prometheus")
                .name("PostgreSQL integration test")
                .type("prometheus")
                .url("https://prometheus.example.test")
                .auth("none")
                .status("healthy")
                .build();
        settingsRepository.saveDataSource(dataSource);

        assertThat(settingsRepository.loadGeneralSettings())
                .usingRecursiveComparison()
                .ignoringFields("apiKey", "clearApiKey")
                .isEqualTo(settings);
        assertThat(settingsRepository.findDataSourceByKey(dataSource.getKey()))
                .contains(dataSource);
        assertThat(settingsRepository.findAllDataSources()).contains(dataSource);

        dataSource.setName("Updated PostgreSQL integration test");
        assertThat(settingsRepository.replaceDataSource(dataSource)).isTrue();
        assertThat(settingsRepository.findDataSourceByKey(dataSource.getKey()))
                .contains(dataSource);
        assertThat(settingsRepository.deleteDataSource(dataSource.getKey())).isTrue();
        assertThat(settingsRepository.findDataSourceByKey(dataSource.getKey())).isEmpty();
    }
}
