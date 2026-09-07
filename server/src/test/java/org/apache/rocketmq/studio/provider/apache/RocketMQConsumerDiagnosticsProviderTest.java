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

import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.remoting.protocol.ResponseCode;
import org.apache.rocketmq.remoting.protocol.body.ConsumerRunningInfo;
import org.apache.rocketmq.studio.cluster.broker.MqAdminExtFactory;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ConsumerStackTraceVO;
import org.apache.rocketmq.tools.admin.MQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQConsumerDiagnosticsProviderTest {

    @Mock
    private RuntimeAdminClientResolver runtimeAdminClientResolver;

    @Mock
    private MqAdminExtFactory adminFactory;

    @Mock
    private RocketMQProperties properties;

    @Mock
    private MQAdminExt adminExt;

    private RocketMQConsumerDiagnosticsProvider provider;

    @BeforeEach
    void setUp() {
        lenient().when(properties.getNamesrvAddr()).thenReturn("127.0.0.1:9876");
        lenient().when(runtimeAdminClientResolver.execute(anyString(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(1).apply(adminExt));
        lenient().when(adminFactory.execute(anyString(), any(), any())).thenAnswer(invocation ->
                invocation.<MqAdminExtFactory.AdminAction<Object>>getArgument(2).apply(adminExt));
        provider = new RocketMQConsumerDiagnosticsProvider(runtimeAdminClientResolver, adminFactory, properties);
    }

    @Test
    void getConsumerStackShouldUseSelectedInstanceAndParseJstack() throws Exception {
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("""
                ConsumeMessageThread_1                   TID: 12 STATE: RUNNABLE
                ConsumeMessageThread_1                   org.apache.demo.OrderListener.consume(OrderListener.java:42)
                ConsumeMessageThread_1                   java.base/java.lang.Thread.run(Thread.java:1583)

                PullMessageService                       TID: 13 STATE: WAITING
                PullMessageService                       java.base/jdk.internal.misc.Unsafe.park(Native Method)
                """);
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true)).thenReturn(runningInfo);

        ConsumerStackTraceVO result = provider.getConsumerStack("instance-a", "cg-orders", "client-1");

        assertThat(result.getGroupName()).isEqualTo("cg-orders");
        assertThat(result.getClientId()).isEqualTo("client-1");
        assertThat(result.getThreadCount()).isEqualTo(2);
        assertThat(result.getThreads()).hasSize(2);
        assertThat(result.getThreads().get(0).getThreadName()).isEqualTo("ConsumeMessageThread_1");
        assertThat(result.getThreads().get(0).getThreadId()).isEqualTo(12);
        assertThat(result.getThreads().get(0).getState()).isEqualTo("RUNNABLE");
        assertThat(result.getThreads().get(0).getStackTrace())
                .containsExactly(
                        "org.apache.demo.OrderListener.consume(OrderListener.java:42)",
                        "java.base/java.lang.Thread.run(Thread.java:1583)");
        verify(runtimeAdminClientResolver).execute(eq("instance-a"), any());
        verify(adminExt).getConsumerRunningInfo("cg-orders", "client-1", true);
        verify(adminFactory, never()).execute(anyString(), any(), any());
    }

    @Test
    void getConsumerStackShouldParseThreadNamesLongerThanJstackColumnWidth() throws Exception {
        String threadName = "ConsumeMessageThreadWithANameLongerThanFortyCharacters";
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack(String.format(
                "%-40sTID: 42 STATE: RUNNABLE%n%-40scom.example.Listener.consume(Listener.java:10)%n",
                threadName, threadName));
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true)).thenReturn(runningInfo);

        ConsumerStackTraceVO result = provider.getConsumerStack("instance-a", "cg-orders", "client-1");

        assertThat(result.getThreadCount()).isEqualTo(1);
        assertThat(result.getThreads().get(0).getThreadName()).isEqualTo(threadName);
        assertThat(result.getThreads().get(0).getThreadId()).isEqualTo(42);
        assertThat(result.getThreads().get(0).getState()).isEqualTo("RUNNABLE");
        assertThat(result.getThreads().get(0).getStackTrace())
                .containsExactly("com.example.Listener.consume(Listener.java:10)");
    }

    @Test
    void getConsumerStackShouldIgnoreOverflowingThreadIdsTest() throws Exception {
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("""
                first TID: 12 STATE: RUNNABLE
                first com.example.First.run(First.java:1)
                malformed TID: 999999999999999999999999999999 STATE: WAITING
                malformed ignored.frame(Line.java:2)
                second TID: 13 STATE: WAITING
                second com.example.Second.run(Second.java:3)
                """);
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true)).thenReturn(runningInfo);

        ConsumerStackTraceVO result = provider.getConsumerStack("instance-a", "cg-orders", "client-1");

        assertThat(result.getThreads()).extracting(thread -> thread.getThreadName())
                .containsExactly("first", "second");
    }

    @Test
    void getConsumerStackShouldRejectOversizedJstackTest() throws Exception {
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("x".repeat(RocketMQConsumerDiagnosticsProvider.MAX_JSTACK_CHARS + 1));
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true)).thenReturn(runningInfo);

        assertThatThrownBy(() -> provider.getConsumerStack("instance-a", "cg-orders", "client-1"))
                .isInstanceOfSatisfying(BusinessException.class,
                        error -> assertThat(error.getCode()).isEqualTo(502))
                .hasMessageContaining("exceeds the supported size");
    }

    @Test
    void getConsumerStackShouldUseDefaultNameServerWhenInstanceIsBlank() throws Exception {
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("");
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true)).thenReturn(runningInfo);

        ConsumerStackTraceVO result = provider.getConsumerStack(null, "cg-orders", "client-1");

        assertThat(result.getThreadCount()).isZero();
        verify(adminFactory).execute(eq("127.0.0.1:9876"), any(), any());
        verify(runtimeAdminClientResolver, never()).execute(anyString(), any());
    }

    @Test
    void getConsumerStackShouldFailFastWhenDefaultAdminIsMissing() {
        when(properties.getNamesrvAddr()).thenReturn("");

        assertThatThrownBy(() -> provider.getConsumerStack(null, "cg-orders", "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("RocketMQ admin not connected")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(503));
    }

    @Test
    void getConsumerStackShouldMapOfflineConsumerToNotFound() throws Exception {
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true))
                .thenThrow(new MQClientException(ResponseCode.CONSUMER_NOT_ONLINE, "consumer offline"));

        assertThatThrownBy(() -> provider.getConsumerStack("instance-a", "cg-orders", "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Consumer client is not reachable from any proxy or broker: client-1")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void getConsumerStackShouldPreferProxyOverBrokerTest() {
        ProxyConsumerResolver resolver = org.mockito.Mockito.mock(ProxyConsumerResolver.class);
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("""
                ConsumeMessageThread_1                   TID: 7 STATE: RUNNABLE
                ConsumeMessageThread_1                   com.example.Listener.consume(Listener.java:20)
                """);
        when(resolver.resolveConsumerRunningInfo("instance-a", "cg-orders", "client-1")).thenReturn(runningInfo);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "proxyConsumerResolver", resolver);

        ConsumerStackTraceVO result = provider.getConsumerStack("instance-a", "cg-orders", "client-1");

        assertThat(result.getThreadCount()).isEqualTo(1);
        assertThat(result.getThreads().get(0).getThreadName()).isEqualTo("ConsumeMessageThread_1");
        verify(runtimeAdminClientResolver, never()).execute(anyString(), any());
    }

    @Test
    void getConsumerStackShouldFallBackToBrokerWhenNoProxyAnswersTest() throws Exception {
        ProxyConsumerResolver resolver = org.mockito.Mockito.mock(ProxyConsumerResolver.class);
        when(resolver.resolveConsumerRunningInfo("instance-a", "cg-orders", "client-1")).thenReturn(null);
        org.springframework.test.util.ReflectionTestUtils.setField(provider, "proxyConsumerResolver", resolver);
        ConsumerRunningInfo runningInfo = new ConsumerRunningInfo();
        runningInfo.setJstack("PullMessageService                       TID: 9 STATE: WAITING\n");
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true)).thenReturn(runningInfo);

        ConsumerStackTraceVO result = provider.getConsumerStack("instance-a", "cg-orders", "client-1");

        assertThat(result.getThreadCount()).isEqualTo(1);
        verify(adminExt).getConsumerRunningInfo("cg-orders", "client-1", true);
    }

    @Test
    void getConsumerStackShouldMapBrokerNotOnlineRemarkToNotFoundTest() throws Exception {
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true))
                .thenThrow(new MQClientException(1,
                        "The Consumer <cg-orders> <client-1> not online"));

        assertThatThrownBy(() -> provider.getConsumerStack("instance-a", "cg-orders", "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Consumer client is not reachable from any proxy or broker: client-1")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(404));
    }

    @Test
    void getConsumerStackShouldSurfaceAdminFailuresAsBadGateway() throws Exception {
        when(adminExt.getConsumerRunningInfo("cg-orders", "client-1", true))
                .thenThrow(new MQClientException(1, "broker rejected request"));

        assertThatThrownBy(() -> provider.getConsumerStack("instance-a", "cg-orders", "client-1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Failed to get consumer stack for client-1")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(502));
    }
}
