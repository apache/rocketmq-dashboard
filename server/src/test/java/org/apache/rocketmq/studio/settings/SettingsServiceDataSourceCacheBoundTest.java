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
import org.apache.rocketmq.studio.common.config.CacheConfig;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the intended scope of the {@code data-sources} cache.
 *
 * <p>The comment above {@code SettingsService.listDataSources()} documents the cache as a cache of
 * the <em>full list</em> ("The full-list endpoint is hit by every metrics tab on first paint ...
 * Caching it with the write paths evicted below keeps the user-visible list correct"). The
 * parameterised, paginated overload carries the same {@code @Cacheable} annotation, and its cache
 * key is derived from the caller-supplied {@code search}/{@code type}/{@code page}/{@code pageSize}
 * arguments. {@code CacheConfig} backs it with a plain {@link ConcurrentMapCacheManager}, whose
 * caches have no TTL, no size bound and no eviction, so every distinct search term pins a
 * permanent {@code PageResult<DataSourceVO>} (a page of credential-bearing data-source configs) in
 * the heap. The paged endpoint is not admin-only, so any authenticated reader can grow the cache
 * without bound.
 *
 * <p>The test runs the service inside a real application context that registers the production
 * {@link CacheConfig}, so the caching behaviour exercised here is the wiring the deployed server
 * uses. The control test proves the harness is live (the full list is served once and cached);
 * the failing test asserts the documented contract — a user-parameterised paged query must not
 * accumulate one permanent cache entry per distinct search term.
 */
class SettingsServiceDataSourceCacheBoundTest {

    private static final int DISTINCT_SEARCHES = 50;

    private final SettingsRepository settingsRepository = mock(SettingsRepository.class);
    private final OperationAuditService operationAuditService = mock(OperationAuditService.class);
    private AnnotationConfigApplicationContext context;
    private SettingsService service;

    @BeforeEach
    void setUp() {
        context = new AnnotationConfigApplicationContext();
        context.getBeanFactory().registerSingleton("settingsRepository", settingsRepository);
        context.getBeanFactory().registerSingleton("operationAuditService", operationAuditService);
        context.register(CacheConfig.class, TestConfig.class);
        context.refresh();
        service = context.getBean(SettingsService.class);
    }

    @org.springframework.context.annotation.Configuration
    static class TestConfig {

        @org.springframework.context.annotation.Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @org.springframework.context.annotation.Bean
        RestClient.Builder restClientBuilder() {
            return RestClient.builder();
        }

        @org.springframework.context.annotation.Bean
        SettingsService settingsService(SettingsRepository repository, RestClient.Builder builder,
                                        ObjectMapper mapper, OperationAuditService audit) {
            return new SettingsService(repository, builder.build(), mapper, audit);
        }
    }

    @AfterEach
    void tearDown() {
        if (context != null) {
            context.close();
        }
    }

    /** Control: the documented cache behaviour — the full list is cached and served once. */
    @Test
    void fullDataSourceListIsCachedAsDocumented() {
        when(settingsRepository.findAllDataSources()).thenReturn(List.of());

        service.listDataSources();
        service.listDataSources();

        verify(settingsRepository, times(1)).findAllDataSources();
        assertThat(dataSourcesCache()).isNotNull();
        assertThat(dataSourcesCache().getNativeCache()).hasSize(1);
    }

    /**
     * A user-parameterised paged query must not populate the unbounded cache: every distinct
     * search term would pin a permanent entry (no TTL, no size bound, no eviction), and the
     * entries carry credential-bearing data-source configs.
     */
    @Test
    void parameterizedDataSourceSearchMustNotAccumulatePermanentCacheEntries() {
        when(settingsRepository.findDataSources(any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> PageResult.of(List.of(), 0L,
                        invocation.getArgument(2), invocation.getArgument(3)));

        for (int i = 1; i <= DISTINCT_SEARCHES; i++) {
            service.listDataSources("search-" + i, null, 1, 20);
        }

        assertThat(dataSourcesCache()).isNotNull();
        // Fails on master: 50 permanent entries keyed on the caller's search terms.
        assertThat(dataSourcesCache().getNativeCache()).isEmpty();
    }

    private ConcurrentMapCache dataSourcesCache() {
        return (ConcurrentMapCache) context.getBean("cacheManager", ConcurrentMapCacheManager.class)
                .getCache(SettingsService.DATA_SOURCE_CACHE);
    }
}
