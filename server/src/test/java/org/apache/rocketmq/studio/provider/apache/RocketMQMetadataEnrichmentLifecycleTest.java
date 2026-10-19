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
package org.apache.rocketmq.studio.provider.apache;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.remoting.protocol.admin.ConsumeStats;
import org.apache.rocketmq.remoting.protocol.admin.OffsetWrapper;
import org.apache.rocketmq.remoting.protocol.body.Connection;
import org.apache.rocketmq.remoting.protocol.body.ConsumerConnection;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.instance.group.ConsumerGroupVO;
import org.apache.rocketmq.studio.persistence.entity.RmqGroup;
import org.apache.rocketmq.studio.persistence.mapper.RmqGroupMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqTopicMapper;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.HashSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RocketMQMetadataEnrichmentLifecycleTest {

    @Test
    void timedOutGroupPagesShouldReleaseTheirQueuedTasksTest() throws Exception {
        Fixture fixture = new Fixture();
        Page<RmqGroup> page = new Page<>(1, 20, 20);
        page.setRecords(groups());
        when(fixture.groupMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(page);
        try {
            assertThat(fixture.provider.listConsumerGroupsPage("instance-a", null, null, 1, 20)
                    .getItems()).hasSize(20);
            assertThat(fixture.started.getCount()).isZero();
            int retainedAfterFirstPage = fixture.executor.getQueue().size();

            assertThat(fixture.provider.listConsumerGroupsPage("instance-a", null, null, 1, 20)
                    .getItems()).hasSize(20);
            int retainedAfterSecondPage = fixture.executor.getQueue().size();

            assertThat(List.of(retainedAfterFirstPage, retainedAfterSecondPage))
                    .as("completed pages must not leave cancelled FutureTasks behind blocked workers")
                    .containsExactly(0, 0);
        } finally {
            fixture.close();
        }
    }

    @Test
    void interruptedGroupListShouldNotRunItsQueuedBrokerLookupsTest() throws Exception {
        Fixture fixture = new Fixture();
        when(fixture.groupMapper.selectList(any())).thenReturn(groups());
        AtomicBoolean callerInterrupted = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch returned = new CountDownLatch(1);
        Thread caller = new Thread(() -> {
            try {
                fixture.provider.listConsumerGroups("instance-a", null, null);
                callerInterrupted.set(Thread.currentThread().isInterrupted());
            } catch (Throwable error) {
                failure.set(error);
            } finally {
                returned.countDown();
            }
        }, "interrupted-group-list-test");
        try {
            caller.start();
            assertThat(fixture.started.await(2, TimeUnit.SECONDS)).isTrue();
            awaitQueuedTasks(fixture.executor, 12);

            caller.interrupt();
            assertThat(returned.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertThat(callerInterrupted.get()).isTrue();

            fixture.release.countDown();
            fixture.executor.shutdown();
            assertThat(fixture.executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
            for (int index = 8; index < 20; index++) {
                verify(fixture.admin, never()).examineConsumerConnectionInfo("group-" + index);
            }
        } finally {
            caller.interrupt();
            fixture.close();
            caller.join(1_000);
        }
    }

    @Test
    void interruptedBatchShouldPreserveConcurrentAndLaterHealthyListingsTest() throws Exception {
        Fixture fixture = new Fixture();
        RmqGroup healthy = new RmqGroup();
        healthy.setName("healthy");
        healthy.setInstanceId("instance-a");
        when(fixture.groupMapper.selectList(any())).thenReturn(groups(), List.of(healthy));
        Connection client = new Connection();
        client.setClientId("healthy-client");
        ConsumerConnection connection = new ConsumerConnection();
        connection.setConnectionSet(new HashSet<>(List.of(client)));
        doReturn(connection).when(fixture.admin).examineConsumerConnectionInfo("healthy");
        ConsumeStats stats = new ConsumeStats();
        OffsetWrapper offset = new OffsetWrapper();
        offset.setBrokerOffset(20);
        offset.setConsumerOffset(13);
        stats.getOffsetTable().put(new MessageQueue("orders", "broker-a", 0), offset);
        when(fixture.admin.examineConsumeStats("healthy")).thenReturn(stats);

        CountDownLatch firstReturned = new CountDownLatch(1);
        CountDownLatch healthyReturned = new CountDownLatch(1);
        AtomicReference<List<ConsumerGroupVO>> healthyResult = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread first = new Thread(() -> {
            try {
                fixture.provider.listConsumerGroups("instance-a", null, null);
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                firstReturned.countDown();
            }
        }, "cancelled-concurrent-group-list-test");
        Thread second = new Thread(() -> {
            try {
                healthyResult.set(fixture.provider.listConsumerGroups("instance-a", null, null));
            } catch (Throwable error) {
                failure.compareAndSet(null, error);
            } finally {
                healthyReturned.countDown();
            }
        }, "healthy-concurrent-group-list-test");
        try {
            first.start();
            assertThat(fixture.started.await(2, TimeUnit.SECONDS)).isTrue();
            awaitQueuedTasks(fixture.executor, 12);
            second.start();
            awaitQueuedTasks(fixture.executor, 13);

            first.interrupt();
            assertThat(firstReturned.await(1, TimeUnit.SECONDS)).isTrue();
            assertThat(fixture.executor.getQueue()).hasSize(1);

            fixture.release.countDown();
            assertThat(healthyReturned.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(failure.get()).isNull();
            assertHealthyGroup(healthyResult.get());
            assertHealthyGroup(fixture.provider.listConsumerGroups("instance-a", null, null));
            verify(fixture.admin, times(2)).examineConsumerConnectionInfo("healthy");
            verify(fixture.admin, times(2)).examineConsumeStats("healthy");
        } finally {
            first.interrupt();
            second.interrupt();
            fixture.close();
            first.join(1_000);
            second.join(1_000);
        }
    }

    private static void awaitQueuedTasks(ThreadPoolExecutor executor, int count) throws InterruptedException {
        long waitUntil = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
        while (executor.getQueue().size() != count && System.nanoTime() < waitUntil) {
            Thread.sleep(5);
        }
        assertThat(executor.getQueue()).hasSize(count);
    }

    private static void assertHealthyGroup(List<ConsumerGroupVO> groups) {
        assertThat(groups).singleElement().satisfies(group -> {
            assertThat(group.getName()).isEqualTo("healthy");
            assertThat(group.getOnlineInstances()).isEqualTo(1);
            assertThat(group.getInstances()).extracting(instance -> instance.getClientId())
                    .containsExactly("healthy-client");
            assertThat(group.isConsumeStatsAvailable()).isTrue();
            assertThat(group.getTotalLag()).isEqualTo(7);
        });
    }

    private static List<RmqGroup> groups() {
        return IntStream.range(0, 20).mapToObj(index -> {
            RmqGroup group = new RmqGroup();
            group.setName("group-" + index);
            group.setInstanceId("instance-a");
            return group;
        }).toList();
    }

    private static final class Fixture implements AutoCloseable {
        private final RmqGroupMapper groupMapper = mock(RmqGroupMapper.class);
        private final DefaultMQAdminExt admin = mock(DefaultMQAdminExt.class);
        private final CountDownLatch started = new CountDownLatch(8);
        private final CountDownLatch release = new CountDownLatch(1);
        private final RocketMQMetadataProvider provider;
        private final ThreadPoolExecutor executor;

        private Fixture() throws Exception {
            RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
            when(resolver.execute(eq("instance-a"), any())).thenAnswer(invocation ->
                    invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(admin));
            when(admin.examineConsumerConnectionInfo(anyString())).thenAnswer(invocation -> {
                started.countDown();
                while (release.getCount() > 0) {
                    try {
                        release.await();
                    } catch (InterruptedException ignored) {
                        // Match the existing late-result tests: an in-flight RPC may ignore cancellation.
                    }
                }
                return new ConsumerConnection();
            });
            provider = new RocketMQMetadataProvider(mock(MqAdminExtFactory.class), new RocketMQProperties(),
                    mock(RmqTopicMapper.class), groupMapper, resolver);
            executor = (ThreadPoolExecutor) ReflectionTestUtils.getField(provider, "onlineEnrichmentExecutor");
        }

        @Override
        public void close() throws InterruptedException {
            release.countDown();
            provider.shutdownOnlineEnrichmentExecutor();
            assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
        }
    }
}
