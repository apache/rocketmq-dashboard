/*
 * Licensed to the Apache Software Foundation (ASF) under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.studio.cluster.broker;

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MqClientPoolTest {

    private MqClientPool pool;
    private Map<Object, Object> cache;
    private DefaultMQPullConsumer consumer;
    private DefaultMQProducer producer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        pool = new MqClientPool();
        Field cacheField = MqClientPool.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        cache = (Map<Object, Object>) cacheField.get(pool);
        consumer = mock(DefaultMQPullConsumer.class);
        producer = mock(DefaultMQProducer.class);
    }

    /** Builds the private ClientKey record via reflection so the cache can be seeded without forking clients. */
    private static Object key(String namesrvAddr, String identity, String kindName) throws Exception {
        Class<?> keyClass = Class.forName(MqClientPool.class.getName() + "$ClientKey");
        Class<?> kindClass = Class.forName(MqClientPool.class.getName() + "$Kind");
        Object kind = Enum.valueOf((Class<? extends Enum>) kindClass.asSubclass(Enum.class), kindName);
        Constructor<?> constructor = keyClass.getDeclaredConstructor(String.class, String.class, kindClass);
        constructor.setAccessible(true);
        return constructor.newInstance(namesrvAddr, identity, kind);
    }

    private void seed(String namesrvAddr, String identity) throws Exception {
        cache.put(key(namesrvAddr, identity, "PULL_CONSUMER"), consumer);
        cache.put(key(namesrvAddr, identity, "PRODUCER"), producer);
    }

    @Test
    void aBlankNameServerAddressIsRejectedUpFront() {
        assertThatThrownBy(() -> pool.withPullConsumer("   ", null, null, c -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("NameServer address is required");
    }

    @Test
    void aCachedClientIsReusedAcrossCallsForTheSameKey() throws Exception {
        seed("10.0.0.1:9876", "anonymous");

        String first = pool.withPullConsumer("10.0.0.1:9876", null, null, c -> "first");
        String second = pool.withPullConsumer("10.0.0.1:9876", null, null, c -> "second");

        assertThat(first).isEqualTo("first");
        assertThat(second).isEqualTo("second");
        // No new client was created (the seed survived) and the cached one was not shut down.
        verify(consumer, never()).shutdown();
    }

    @Test
    void aNullIdentityIsAnonymousAndEquivalentToABlankOne() throws Exception {
        seed("10.0.0.1:9876", "anonymous");

        String byNull = pool.withProducer("10.0.0.1:9876", null, null, p -> "null");
        String byBlank = pool.withProducer("10.0.0.1:9876", null, "  ", p -> "blank");

        assertThat(byNull).isEqualTo("null");
        assertThat(byBlank).isEqualTo("blank");
        verify(producer, never()).shutdown();
    }

    @Test
    void endpointReleaseShutsDownEveryIdentityOnThatEndpoint() throws Exception {
        seed("10.0.0.1:9876", "credential-a");
        seed("10.0.0.1:9876", "credential-b");
        seed("10.0.0.2:9876", "credential-a");

        pool.release(" 10.0.0.1:9876 ");

        verify(consumer, org.mockito.Mockito.times(2)).shutdown();
        verify(producer, org.mockito.Mockito.times(2)).shutdown();
        assertThat(cache).containsOnlyKeys(key("10.0.0.2:9876", "credential-a", "PULL_CONSUMER"),
                key("10.0.0.2:9876", "credential-a", "PRODUCER"));
    }

    @Test
    void identityReleaseShutsDownOnlyThatIdentity() throws Exception {
        seed("10.0.0.1:9876", "credential-a");
        seed("10.0.0.1:9876", "credential-b");

        pool.release("10.0.0.1:9876", " credential-a ");

        verify(consumer, org.mockito.Mockito.times(1)).shutdown();
        verify(producer, org.mockito.Mockito.times(1)).shutdown();
        assertThat(cache).containsKey(key("10.0.0.1:9876", "credential-b", "PULL_CONSUMER"));
    }

    @Test
    void endpointReleaseMatchesEquivalentAddressLists() throws Exception {
        seed("10.0.0.1:9876;10.0.0.2:9876", "anonymous");

        pool.release("10.0.0.2:9876,10.0.0.1:9876");

        verify(consumer, org.mockito.Mockito.times(1)).shutdown();
        assertThat(cache).isEmpty();
    }

    @Test
    void anActionFailureIsWrappedIn502WithTheRootMessage() throws Exception {
        seed("10.0.0.1:9876", "anonymous");
        IllegalStateException root = new IllegalStateException("connection reset");
        RuntimeException wrapper = new RuntimeException("send failed", root);

        assertThatThrownBy(() -> pool.withProducer("10.0.0.1:9876", null, null, p -> {
            throw wrapper;
        }))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("RocketMQ client call failed: connection reset");
    }

    @Test
    void aBusinessExceptionFromTheActionPassesThroughUnwrapped() throws Exception {
        seed("10.0.0.1:9876", "anonymous");
        BusinessException original = new BusinessException(409, "already exists");

        assertThatThrownBy(() -> pool.withPullConsumer("10.0.0.1:9876", null, null, c -> {
            throw original;
        }))
                .isSameAs(original);
    }

    @Test
    void shutdownClosesEveryClientAndRefusesFurtherWork() throws Exception {
        seed("10.0.0.1:9876", "credential-a");
        seed("10.0.0.2:9876", "credential-b");

        pool.shutdown();

        verify(consumer, org.mockito.Mockito.times(2)).shutdown();
        verify(producer, org.mockito.Mockito.times(2)).shutdown();
        assertThat(cache).isEmpty();
        assertThatThrownBy(() -> pool.withPullConsumer("10.0.0.1:9876", null, null, c -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pool is shutting down");
    }

    @Test
    void workAfterShutdownIsRefusedEvenWhenTheCacheStillHoldsAnEntry() throws Exception {
        pool.shutdown();
        // The cache would be empty after a real shutdown, but a racing late write could
        // leave an entry behind: the up-front closed check must refuse before the cache
        // is consulted, because a cache hit skips the inside-computeIfAbsent re-check.
        seed("10.0.0.1:9876", "anonymous");

        assertThatThrownBy(() -> pool.withPullConsumer("10.0.0.1:9876", null, null, c -> "unused"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("pool is shutting down");
        verify(consumer, never()).shutdown();
    }

    @Test
    void aClientShutdownFailureDuringReleaseIsSwallowed() throws Exception {
        seed("10.0.0.1:9876", "anonymous");
        org.mockito.Mockito.doThrow(new RuntimeException("already stopped"))
                .when(consumer).shutdown();

        pool.release("10.0.0.1:9876");

        assertThat(cache).isEmpty();
    }
}
