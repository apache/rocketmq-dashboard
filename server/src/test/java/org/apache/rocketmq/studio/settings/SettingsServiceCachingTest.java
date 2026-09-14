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
package org.apache.rocketmq.studio.settings;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = SettingsServiceCachingTest.Config.class)
class SettingsServiceCachingTest {

    @Autowired
    private SettingsService settingsService;

    @Autowired
    private SettingsRepository settingsRepository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(settingsRepository);
        cacheManager.getCache("data-sources").clear();
    }

    @Test
    void pagedInventoryShouldDistinguishNullFromLiteralNullSearchTest() {
        PageResult<DataSourceVO> unfiltered = PageResult.of(
                List.of(DataSourceVO.builder().key("all").name("All").build()), 1, 1, 20);
        PageResult<DataSourceVO> literalNull = PageResult.of(
                List.of(DataSourceVO.builder().key("literal-null").name("Literal null").build()),
                1, 1, 20);
        when(settingsRepository.findDataSources(null, null, 1, 20)).thenReturn(unfiltered);
        when(settingsRepository.findDataSources("null", null, 1, 20)).thenReturn(literalNull);

        assertThat(settingsService.listDataSources(null, null, 1, 20)).isSameAs(unfiltered);
        assertThat(settingsService.listDataSources("null", null, 1, 20)).isSameAs(literalNull);
        verify(settingsRepository).findDataSources(null, null, 1, 20);
        verify(settingsRepository).findDataSources("null", null, 1, 20);
    }

    @Configuration
    @EnableCaching
    static class Config {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("data-sources");
        }

        @Bean
        SettingsRepository settingsRepository() {
            return mock(SettingsRepository.class);
        }

        @Bean
        SettingsService settingsService(SettingsRepository settingsRepository) {
            return new SettingsService(settingsRepository, RestClient.create(), new ObjectMapper(),
                    mock(OperationAuditService.class));
        }
    }
}
