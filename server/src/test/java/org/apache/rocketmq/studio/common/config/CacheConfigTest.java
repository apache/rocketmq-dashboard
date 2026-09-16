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
package org.apache.rocketmq.studio.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheInterceptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the wiring that broke {@code GET /api/settings/datasources} with a 500: caching was
 * enabled but the resolved {@link CacheManager} could not hand out the {@code data-sources} cache,
 * so the {@code @Cacheable} interceptor threw {@code Cannot find cache named 'data-sources'}.
 * <p>
 * The pre-existing {@code SettingsServiceCachingTest} supplies its own manager, which is why it
 * kept passing while production failed on every request.
 */
class CacheConfigTest {

    /** Mirrors the production wiring: {@code @EnableCaching} plus {@link CacheConfig}. */
    @EnableCaching
    static class CachingContext {
    }

    @Test
    void resolvesEveryCacheNameUsedByCacheableMethodsTest() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CachingContext.class, CacheConfig.class);
            context.refresh();

            CacheManager cacheManager = context.getBean(CacheManager.class);

            // The name SettingsService.listDataSources caches under.
            assertThat(cacheManager.getCache("data-sources")).isNotNull();
            // Names are created on demand, so a newly added @Cacheable cannot break the same way.
            assertThat(cacheManager.getCache("any-future-cache-name")).isNotNull();
        }
    }

    @Test
    void enablesTheCachingInterceptorAgainstThatManagerTest() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(CachingContext.class, CacheConfig.class);
            context.refresh();

            // Without an interceptor @Cacheable would silently no-op instead of caching.
            assertThat(context.getBeansOfType(CacheInterceptor.class)).isNotEmpty();
        }
    }
}
