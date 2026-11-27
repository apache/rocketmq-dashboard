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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.RestClient;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {CacheConfig.class, SettingsServiceCacheInvalidationTest.Config.class})
class SettingsServiceCacheInvalidationTest {

    @Autowired
    private SettingsService service;

    @Autowired
    private SettingsRepository repository;

    @Autowired
    private CacheManager cacheManager;

    @BeforeEach
    void resetState() {
        reset(repository);
        cacheManager.getCache(SettingsService.DATA_SOURCE_CACHE).clear();
    }

    @ParameterizedTest
    @CsvSource({"false,create", "false,update", "false,delete",
                "true,create", "true,update", "true,delete"})
    void completedWriteShouldEvictAnOverlappingOldInventoryLoadTest(boolean paged, String mutation)
            throws Exception {
        DataSourceVO oldSource = source("source-a", "Old name");
        DataSourceVO updatedSource = source("source-a", "Updated name");
        DataSourceVO newSource = source("source-b", "New source");
        List<DataSourceVO> oldRows = List.of(oldSource);
        List<DataSourceVO> newRows = switch (mutation) {
            case "create" -> List.of(oldSource, newSource);
            case "update" -> List.of(updatedSource);
            default -> List.of();
        };
        AtomicReference<List<DataSourceVO>> storedRows = new AtomicReference<>(oldRows);
        CountDownLatch readStarted = new CountDownLatch(1);
        CountDownLatch allowRead = new CountDownLatch(1);
        CountDownLatch writeCommitted = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        org.mockito.stubbing.Answer<List<DataSourceVO>> loadRows = invocation -> {
            List<DataSourceVO> snapshot = storedRows.get();
            if (loads.incrementAndGet() == 1) {
                readStarted.countDown();
                assertThat(allowRead.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return snapshot;
        };
        if (paged) {
            when(repository.findDataSources(null, null, 1, 20)).thenAnswer(invocation -> {
                List<DataSourceVO> rows = loadRows.answer(invocation);
                return PageResult.of(rows, rows.size(), 1, 20);
            });
        } else {
            when(repository.findAllDataSources()).thenAnswer(loadRows);
        }
        Runnable commitRows = () -> {
            storedRows.set(newRows);
            writeCommitted.countDown();
        };
        when(repository.saveDataSource(any())).thenAnswer(invocation -> {
            commitRows.run();
            return newSource;
        });
        when(repository.replaceDataSource(any())).thenAnswer(invocation -> {
            commitRows.run();
            return true;
        });
        when(repository.deleteDataSource("source-a")).thenAnswer(invocation -> {
            commitRows.run();
            return true;
        });
        Supplier<List<DataSourceVO>> inventory = paged
                ? () -> service.listDataSources(null, null, 1, 20).getItems()
                : service::listDataSources;
        FutureTask<List<DataSourceVO>> read = new FutureTask<>(inventory::get);
        FutureTask<Void> write = new FutureTask<>(() -> {
            switch (mutation) {
                case "create" -> service.createDataSource(newSource);
                case "update" -> service.updateDataSource(updatedSource);
                default -> service.deleteDataSource("source-a");
            }
            return null;
        });
        Thread reader = new Thread(read, "data-source-cache-reader-test");
        Thread writer = new Thread(write, "data-source-cache-writer-test");
        try {
            reader.start();
            assertThat(readStarted.await(5, TimeUnit.SECONDS)).isTrue();
            writer.start();
            assertThat(writeCommitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitEvictionBoundary(write, writer);
            allowRead.countDown();
            assertThat(read.get(5, TimeUnit.SECONDS)).containsExactlyElementsOf(oldRows);
            write.get(5, TimeUnit.SECONDS);

            // Once both overlapping requests finish, subsequent callers must see the committed rows.
            assertThat(inventory.get()).containsExactlyElementsOf(newRows);
            assertThat(inventory.get()).containsExactlyElementsOf(newRows);
            assertThat(loads).hasValue(2);
        } finally {
            allowRead.countDown();
            reader.join(5_000);
            writer.join(5_000);
            assertThat(reader.isAlive()).isFalse();
            assertThat(writer.isAlive()).isFalse();
        }
    }

    @Test
    void successfulWriteShouldInvalidateBothInFlightInventoryKeysTest() throws Exception {
        List<DataSourceVO> oldRows = List.of(source("source-a", "Old name"));
        DataSourceVO updated = source("source-a", "Updated name");
        List<DataSourceVO> newRows = List.of(updated);
        AtomicReference<List<DataSourceVO>> storedRows = new AtomicReference<>(oldRows);
        CountDownLatch readsStarted = new CountDownLatch(2);
        CountDownLatch allowReads = new CountDownLatch(1);
        CountDownLatch writeCommitted = new CountDownLatch(1);
        AtomicInteger loads = new AtomicInteger();
        org.mockito.stubbing.Answer<List<DataSourceVO>> loadRows = invocation -> {
            List<DataSourceVO> snapshot = storedRows.get();
            if (loads.incrementAndGet() <= 2) {
                readsStarted.countDown();
                assertThat(allowReads.await(5, TimeUnit.SECONDS)).isTrue();
            }
            return snapshot;
        };
        when(repository.findAllDataSources()).thenAnswer(loadRows);
        when(repository.findDataSources(null, null, 1, 20)).thenAnswer(invocation -> {
            List<DataSourceVO> rows = loadRows.answer(invocation);
            return PageResult.of(rows, rows.size(), 1, 20);
        });
        when(repository.replaceDataSource(updated)).thenAnswer(invocation -> {
            storedRows.set(newRows);
            writeCommitted.countDown();
            return true;
        });
        FutureTask<List<DataSourceVO>> full = new FutureTask<>(service::listDataSources);
        FutureTask<PageResult<DataSourceVO>> page = new FutureTask<>(
                () -> service.listDataSources(null, null, 1, 20));
        FutureTask<DataSourceVO> write = new FutureTask<>(() -> service.updateDataSource(updated));
        Thread fullReader = new Thread(full, "data-source-full-cache-reader-test");
        Thread pageReader = new Thread(page, "data-source-page-cache-reader-test");
        Thread writer = new Thread(write, "data-source-both-cache-writer-test");
        try {
            fullReader.start();
            pageReader.start();
            assertThat(readsStarted.await(5, TimeUnit.SECONDS)).isTrue();
            writer.start();
            assertThat(writeCommitted.await(5, TimeUnit.SECONDS)).isTrue();
            awaitEvictionBoundary(write, writer);
            allowReads.countDown();
            assertThat(full.get(5, TimeUnit.SECONDS)).containsExactlyElementsOf(oldRows);
            assertThat(page.get(5, TimeUnit.SECONDS).getItems()).containsExactlyElementsOf(oldRows);
            write.get(5, TimeUnit.SECONDS);
            assertThat(service.listDataSources()).containsExactlyElementsOf(newRows);
            assertThat(service.listDataSources(null, null, 1, 20).getItems()).containsExactlyElementsOf(newRows);
            assertThat(loads).hasValue(4);
        } finally {
            allowReads.countDown();
            for (Thread thread : List.of(fullReader, pageReader, writer)) {
                thread.join(5_000);
                assertThat(thread.isAlive()).isFalse();
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"create", "update", "delete"})
    void failedWriteShouldKeepExistingInventoryCacheHitsTest(String mutation) {
        DataSourceVO existing = source("source-a", "Existing");
        List<DataSourceVO> rows = List.of(existing);
        when(repository.findAllDataSources()).thenReturn(rows);
        when(repository.findDataSources(null, null, 1, 20)).thenReturn(PageResult.of(rows, 1, 1, 20));
        assertThat(service.listDataSources()).containsExactly(existing);
        assertThat(service.listDataSources(null, null, 1, 20).getItems()).containsExactly(existing);
        IllegalStateException failure = new IllegalStateException("Simulated write failure");
        switch (mutation) {
            case "create" -> when(repository.saveDataSource(any())).thenThrow(failure);
            case "update" -> when(repository.replaceDataSource(any())).thenThrow(failure);
            default -> when(repository.deleteDataSource("source-a")).thenThrow(failure);
        }
        assertThatThrownBy(() -> {
            switch (mutation) {
                case "create" -> service.createDataSource(existing);
                case "update" -> service.updateDataSource(existing);
                default -> service.deleteDataSource("source-a");
            }
        }).isSameAs(failure);
        assertThat(service.listDataSources()).containsExactly(existing);
        assertThat(service.listDataSources(null, null, 1, 20).getItems()).containsExactly(existing);
        verify(repository).findAllDataSources();
        verify(repository).findDataSources(null, null, 1, 20);
    }

    private static void awaitEvictionBoundary(FutureTask<?> write, Thread writer) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!write.isDone() && !blockedInCacheClear(writer) && System.nanoTime() < deadline) {
            Thread.onSpinWait();
        }
        // The original asynchronous cache put lets eviction finish. A synchronized loader instead
        // makes the production ConcurrentMap cache wait for this load before clearing its entry.
        assertThat(write.isDone() || blockedInCacheClear(writer)).isTrue();
    }

    private static boolean blockedInCacheClear(Thread writer) {
        return writer.getState() == Thread.State.BLOCKED && Arrays.stream(writer.getStackTrace())
                .anyMatch(frame -> frame.getClassName().equals("java.util.concurrent.ConcurrentHashMap")
                        && frame.getMethodName().equals("clear"));
    }

    private static DataSourceVO source(String key, String name) {
        return DataSourceVO.builder().key(key).name(name).type("prometheus")
                .url("http://192.0.2.1:9090").auth("none").instanceIds(List.of("instance-a")).build();
    }

    @Configuration
    static class Config {
        @Bean
        SettingsRepository settingsRepository() {
            return mock(SettingsRepository.class);
        }

        @Bean
        SettingsService settingsService(SettingsRepository repository) {
            return new SettingsService(repository, RestClient.create(), new ObjectMapper(),
                    mock(OperationAuditService.class));
        }
    }
}
