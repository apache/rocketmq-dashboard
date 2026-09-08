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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.consumer.MessageSelector;
import org.apache.rocketmq.client.consumer.PullResult;
import org.apache.rocketmq.client.consumer.PullStatus;
import org.apache.rocketmq.client.exception.MQBrokerException;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.GlobalExceptionHandler;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.RocketMQMessageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.LinkedMultiValueMap;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class QueueFilterPreviewTest {
    private final DefaultMQPullConsumer consumer = mock(DefaultMQPullConsumer.class);
    private final RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
    private final MessageQueue queue = new MessageQueue("orders", "broker-a", 0);
    private MockMvc mvc;

    @BeforeEach
    void setUp() throws Exception {
        when(resolver.executePullConsumer(eq("instance-a"), any())).thenAnswer(invocation ->
                invocation.<MqClientPool.ClientAction<DefaultMQPullConsumer, Object>>getArgument(1).apply(consumer));
        when(consumer.fetchSubscribeMessageQueues("orders")).thenReturn(Set.of(queue));
        when(consumer.minOffset(queue)).thenReturn(10L);
        when(consumer.maxOffset(queue)).thenReturn(100L);
        MessageService service = new MessageService(new RocketMQMessageProvider(resolver),
                mock(InstanceProviderRegistry.class), mock(QueryHistoryService.class), mock(OperationAuditService.class));
        mvc = MockMvcBuilders.standaloneSetup(new MessageController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"TAG, 'paid || shipped'", "SQL92, 'amount > 100'"})
    void sendsTheNativeSelectorAndReturnsMatchingMessageDetails(String type, String expression) throws Exception {
        MessageExt message = new MessageExt();
        message.setTopic("orders");
        message.setMsgId("matched-message");
        message.setQueueId(0);
        message.setQueueOffset(18);
        message.setBody("event".getBytes(StandardCharsets.UTF_8));
        message.setBornHost(new InetSocketAddress("127.0.0.1", 1000));
        message.setStoreHost(new InetSocketAddress("127.0.0.1", 10911));
        message.putUserProperty("amount", "200");
        when(consumer.pull(eq(queue), any(MessageSelector.class), eq(10L), eq(20), eq(3000L)))
                .thenReturn(new PullResult(PullStatus.FOUND, 30, 10, 100, List.of(message)));

        mvc.perform(request(type, expression)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].msgId").value("matched-message"))
                .andExpect(jsonPath("$.data.items[0].properties.amount").value("200"))
                .andExpect(jsonPath("$.data.items[0].queueOffset").value(18))
                .andExpect(jsonPath("$.data.nextOffset").value(30))
                .andExpect(jsonPath("$.data.hasMore").value(true));

        ArgumentCaptor<MessageSelector> selector = ArgumentCaptor.forClass(MessageSelector.class);
        verify(consumer).pull(eq(queue), selector.capture(), eq(10L), eq(20), eq(3000L));
        assertThat(selector.getValue().getExpressionType()).isEqualTo(type);
        assertThat(selector.getValue().getExpression()).isEqualTo(expression);
        verify(consumer).fetchSubscribeMessageQueues("orders");
        verify(consumer).minOffset(queue);
        verify(consumer).maxOffset(queue);
        verifyNoMoreInteractions(consumer);
    }

    @ParameterizedTest
    @CsvSource({"NO_MATCHED_MSG, 50, true", "NO_NEW_MSG, 100, false", "OFFSET_ILLEGAL, 20, true"})
    void preservesContinuationEvenWhenTheBatchContainsNoMatches(PullStatus status, long next, boolean more) throws Exception {
        when(consumer.pull(eq(queue), any(MessageSelector.class), eq(10L), eq(20), eq(3000L)))
                .thenReturn(new PullResult(status, next, 10, 100, List.of()));
        mvc.perform(request("TAG", "missing-tag")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.nextOffset").value(next))
                .andExpect(jsonPath("$.data.hasMore").value(more))
                .andExpect(jsonPath("$.data.offsetAdjusted").value(status == PullStatus.OFFSET_ILLEGAL));
    }

    @Test
    void clampsExpiredInputBeforePullingAndReportsTheAdjustment() throws Exception {
        when(consumer.pull(eq(queue), any(MessageSelector.class), eq(10L), eq(20), eq(3000L)))
                .thenReturn(new PullResult(PullStatus.NO_MATCHED_MSG, 30, 10, 100, List.of()));
        mvc.perform(requestWith("offset", "0")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.startOffset").value(10))
                .andExpect(jsonPath("$.data.offsetAdjusted").value(true));
    }

    @Test
    void returnsTheTailWithoutPullingWhenNoReadablePositionRemains() throws Exception {
        mvc.perform(requestWith("offset", "100")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nextOffset").value(100))
                .andExpect(jsonPath("$.data.hasMore").value(false));
        verify(consumer, never()).pull(any(), any(MessageSelector.class), anyLong(), anyInt(), anyLong());
    }

    @Test
    void rejectsAStaleQueueRoute() throws Exception {
        when(consumer.fetchSubscribeMessageQueues("orders")).thenReturn(Set.of());
        mvc.perform(request("TAG", "*")).andExpect(status().isNotFound());
        verify(consumer, never()).pull(any(), any(MessageSelector.class), anyLong(), anyInt(), anyLong());
    }

    @Test
    void reportsBrokerFilterRejectionInsteadOfSilentlyQueryingAllMessages() throws Exception {
        when(consumer.pull(eq(queue), any(MessageSelector.class), eq(10L), eq(20), eq(3000L)))
                .thenThrow(new MQBrokerException(1, "Property filtering is disabled"));
        mvc.perform(request("SQL92", "amount > 100")).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Property filtering is disabled")));
        verify(consumer, never()).pull(any(), any(String.class), anyLong(), anyInt());
    }

    @Test
    void rejectsANonAdvancingBatchRatherThanOfferingAnEndlessNextButton() throws Exception {
        when(consumer.pull(eq(queue), any(MessageSelector.class), eq(10L), eq(20), eq(3000L)))
                .thenReturn(new PullResult(PullStatus.NO_MATCHED_MSG, 10, 10, 100, List.of()));
        mvc.perform(request("TAG", "*")).andExpect(status().isBadGateway());
    }

    @Test
    void preservesInterruptionWhenAFilterPullIsCancelled() throws Exception {
        when(consumer.pull(eq(queue), any(MessageSelector.class), eq(10L), eq(20), eq(3000L)))
                .thenThrow(new InterruptedException("Cancelled"));
        try {
            mvc.perform(request("TAG", "*")).andExpect(status().isBadGateway());
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @ParameterizedTest
    @CsvSource({"expressionType, JSON", "expression, ''", "queueId, -1", "offset, -1", "topic, ''"})
    void validatesTheHttpContractBeforeResolvingAnInstance(String field, String value) throws Exception {
        mvc.perform(requestWith(field, value)).andExpect(status().isBadRequest());
        verify(resolver, never()).executePullConsumer(any(), any());
    }

    private MockHttpServletRequestBuilder request(String type, String expression) {
        var parameters = parameters();
        parameters.set("expressionType", type);
        parameters.set("expression", expression);
        return get("/api/messages/queue-filter-preview").params(parameters);
    }

    private MockHttpServletRequestBuilder requestWith(String field, String value) {
        var parameters = parameters();
        parameters.set(field, value);
        return get("/api/messages/queue-filter-preview").params(parameters);
    }

    private LinkedMultiValueMap<String, String> parameters() {
        var parameters = new LinkedMultiValueMap<String, String>();
        parameters.set("instanceId", "instance-a");
        parameters.set("topic", "orders");
        parameters.set("brokerName", "broker-a");
        parameters.set("queueId", "0");
        parameters.set("offset", "10");
        parameters.set("expressionType", "TAG");
        parameters.set("expression", "*");
        return parameters;
    }
}
