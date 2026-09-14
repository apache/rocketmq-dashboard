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

import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the {@link CacheManager} backing the {@code @Cacheable} methods enabled by
 * {@code @EnableCaching} on the application class.
 * <p>
 * {@code @EnableCaching} on its own leaves the manager to Spring Boot's cache
 * auto-configuration. In this application that did not yield a manager able to serve the
 * {@code data-sources} cache, so every call to {@code SettingsService.listDataSources} failed with
 * {@code IllegalArgumentException: Cannot find cache named 'data-sources'} and surfaced as a 500 on
 * {@code GET /api/settings/datasources} — breaking both the settings data-source tab and the
 * metrics data-source picker on the dashboard. Declaring the manager here removes the dependency
 * on whatever the auto-configuration happens to resolve to.
 * <p>
 * The manager is deliberately left without a fixed cache-name list: {@code ConcurrentMapCacheManager}
 * then creates caches on first use, so adding a {@code @Cacheable} with a new name cannot
 * reintroduce the same failure. Caches in use today: {@code data-sources}.
 */
@Configuration
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
