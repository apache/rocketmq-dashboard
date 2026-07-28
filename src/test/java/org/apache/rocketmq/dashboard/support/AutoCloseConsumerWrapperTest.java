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
package org.apache.rocketmq.dashboard.support;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.remoting.RPCHook;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.Silent.class)
public class AutoCloseConsumerWrapperTest {

    @Mock
    private DefaultMQPullConsumer mockConsumer;

    private AutoCloseConsumerWrapper wrapper;

    @SuppressWarnings("unchecked")
    private AtomicReference<DefaultMQPullConsumer> consumerRef() {
        return (AtomicReference<DefaultMQPullConsumer>)
            ReflectionTestUtils.getField(AutoCloseConsumerWrapper.class, "CONSUMER_REF");
    }

    private void setLastUsedTime(Instant instant) {
        ReflectionTestUtils.setField(AutoCloseConsumerWrapper.class, "lastUsedTime", instant);
    }

    @Before
    public void setUp() {
        // Wrapper subclass avoids creating a real network-backed consumer
        wrapper = new AutoCloseConsumerWrapper() {
            @Override
            protected DefaultMQPullConsumer createNewConsumer(RPCHook rpcHook, Boolean useTLS) {
                return mockConsumer;
            }
        };
        consumerRef().set(null);
        setLastUsedTime(Instant.now());
    }

    @Test
    public void testGetConsumerCreatesAndStartsNewConsumer() throws Exception {
        DefaultMQPullConsumer consumer = wrapper.getConsumer(null, true);
        assertSame(mockConsumer, consumer);
        assertSame(mockConsumer, consumerRef().get());
        verify(mockConsumer).start();
    }

    @Test
    public void testGetConsumerReusesExistingConsumer() throws Exception {
        wrapper.getConsumer(null, true);
        DefaultMQPullConsumer second = wrapper.getConsumer(null, true);
        assertSame(mockConsumer, second);
        // start() only invoked on first creation
        verify(mockConsumer, times(1)).start();
    }

    @Test
    public void testGetConsumerStartFailureCleansUp() throws Exception {
        doThrow(new MQClientException("start failed", null)).when(mockConsumer).start();
        try {
            wrapper.getConsumer(null, false);
            fail("Expected RuntimeException");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Failed to start consumer"));
        }
        verify(mockConsumer).shutdown();
        assertNull(consumerRef().get());
    }

    @Test
    public void testCheckAndCloseIdleConsumerClosesWhenIdle() throws Exception {
        wrapper.getConsumer(null, true);
        setLastUsedTime(Instant.now().minusSeconds(120));

        wrapper.checkAndCloseIdleConsumer();

        verify(mockConsumer).shutdown();
        assertNull(consumerRef().get());
    }

    @Test
    public void testCheckAndCloseIdleConsumerKeepsRecentConsumer() throws Exception {
        wrapper.getConsumer(null, true);
        setLastUsedTime(Instant.now());

        wrapper.checkAndCloseIdleConsumer();

        verify(mockConsumer, never()).shutdown();
        assertSame(mockConsumer, consumerRef().get());
    }

    @Test
    public void testCloseWithoutConsumerIsNoOp() {
        consumerRef().set(null);
        wrapper.close();
        assertNull(consumerRef().get());
    }

    @Test
    public void testCloseShutsDownConsumer() throws Exception {
        wrapper.getConsumer(null, true);
        wrapper.close();
        verify(mockConsumer).shutdown();
        assertNull(consumerRef().get());
    }

    @Test
    public void testCreateNewConsumerBuildsConfiguredConsumer() {
        AutoCloseConsumerWrapper realWrapper = new AutoCloseConsumerWrapper();
        DefaultMQPullConsumer consumer = realWrapper.createNewConsumer(null, true);
        assertNotNull(consumer);
        assertEquals(MixAll.TOOLS_CONSUMER_GROUP, consumer.getConsumerGroup());
        assertTrue(consumer.isUseTLS());
    }
}
