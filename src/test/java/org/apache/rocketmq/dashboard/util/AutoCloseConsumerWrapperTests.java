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

package org.apache.rocketmq.dashboard.util;

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.dashboard.support.AutoCloseConsumerWrapper;
import org.apache.rocketmq.remoting.RPCHook;
import java.lang.reflect.Field;
import static org.mockito.Mockito.mock;
import org.apache.rocketmq.client.exception.MQClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AutoCloseConsumerWrapperTests {

    private static class TestableWrapper extends AutoCloseConsumerWrapper {
        private DefaultMQPullConsumer mockConsumer = mock(DefaultMQPullConsumer.class);

        @Override
        protected DefaultMQPullConsumer createNewConsumer(RPCHook rpcHook, Boolean useTLS) {
            return mockConsumer;
        }
    }

    @Test
    void shouldReuseConsumerInstance() throws Exception {
        TestableWrapper wrapper = new TestableWrapper();

        DefaultMQPullConsumer first = wrapper.getConsumer(mock(RPCHook.class), true);
        assertNotNull(first);

        DefaultMQPullConsumer second = wrapper.getConsumer(mock(RPCHook.class), true);
        assertSame(first, second);
    }

    @Test
    void shouldHandleStartFailure() throws Exception {
        TestableWrapper wrapper = new TestableWrapper();
        doThrow(new MQClientException("Simulated error", null))
                .when(wrapper.mockConsumer).start();

        assertThrows(RuntimeException.class, () ->
                wrapper.getConsumer(mock(RPCHook.class), true));

        verify(wrapper.mockConsumer).shutdown();
    }



    @Test
    void shouldCloseIdleConsumer() throws Exception {
        TestableWrapper wrapper = new TestableWrapper();

        wrapper.getConsumer(mock(RPCHook.class), true);

        Field lastUsedTime = AutoCloseConsumerWrapper.class.getDeclaredField("lastUsedTime");
        lastUsedTime.setAccessible(true);
        lastUsedTime.set(wrapper, Instant.now().minusSeconds(70));

        wrapper.checkAndCloseIdleConsumer();

        verify(wrapper.mockConsumer).shutdown();
    }
}
