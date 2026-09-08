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
import org.apache.rocketmq.client.exception.MQClientException;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.cluster.broker.MqClientPool;
import org.apache.rocketmq.studio.cluster.broker.RuntimeAdminClientResolver;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.common.exception.GlobalExceptionHandler;
import org.apache.rocketmq.studio.provider.InstanceProviderRegistry;
import org.apache.rocketmq.studio.provider.apache.RocketMQMessageProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises HTTP binding, service validation and provider lookup together, with only the SDK boundary mocked. */
class QueueTimestampLookupTest {
    private final MessageQueue queue = new MessageQueue("orders", "broker-a", 2);
    private final DefaultMQPullConsumer consumer = mock(DefaultMQPullConsumer.class);
    private final RuntimeAdminClientResolver resolver = mock(RuntimeAdminClientResolver.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(resolver.executePullConsumer(eq("instance-a"), any())).thenAnswer(invocation -> {
            MqClientPool.ClientAction<DefaultMQPullConsumer, Object> action = invocation.getArgument(1);
            return action.apply(consumer);
        });
        MessageService service = new MessageService(new RocketMQMessageProvider(resolver),
                mock(InstanceProviderRegistry.class), mock(QueryHistoryService.class), mock(OperationAuditService.class));
        mvc = MockMvcBuilders.standaloneSetup(new MessageController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"15, 15", "0, 10", "20, 19", "30, 19"})
    void locatesWithinRetainedQueueBoundsWithoutReadingBodiesOrChangingConsumption(long brokerOffset,
                                                                                  long expectedOffset) throws Exception {
        readableQueue(10, 20);
        when(consumer.searchOffset(queue, 1788825600000L)).thenReturn(brokerOffset);

        mvc.perform(request()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.brokerName").value("broker-a"))
                .andExpect(jsonPath("$.data.queueId").value(2))
                .andExpect(jsonPath("$.data.minOffset").value(10))
                .andExpect(jsonPath("$.data.maxOffset").value(20))
                .andExpect(jsonPath("$.data.offset").value(expectedOffset));

        verify(consumer).fetchSubscribeMessageQueues("orders");
        verify(consumer).minOffset(queue);
        verify(consumer).maxOffset(queue);
        verify(consumer).searchOffset(queue, 1788825600000L);
        verifyNoMoreInteractions(consumer);
    }

    @Test
    void returnsEmptyQueueSnapshotWithoutSearching() throws Exception {
        readableQueue(20, 20);
        mvc.perform(request()).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.minOffset").value(20))
                .andExpect(jsonPath("$.data.maxOffset").value(20))
                .andExpect(jsonPath("$.data.offset").doesNotExist());
        verify(consumer, never()).searchOffset(any(), anyLong());
    }

    @Test
    void acceptsEpochWithoutTreatingItAsMissing() throws Exception {
        readableQueue(0, 1);
        when(consumer.searchOffset(queue, 0)).thenReturn(0L);
        mvc.perform(request("timestamp", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.offset").value(0));
        verify(consumer).searchOffset(queue, 0);
    }

    @Test
    void rejectsQueueRemovedFromReadableRoute() throws Exception {
        when(consumer.fetchSubscribeMessageQueues("orders"))
                .thenReturn(Set.of(new MessageQueue("orders", "broker-b", 2)));
        mvc.perform(request()).andExpect(status().isNotFound());
        verify(consumer, never()).minOffset(any());
    }

    @Test
    void rejectsMissingTopicRoute() throws Exception {
        when(consumer.fetchSubscribeMessageQueues("orders")).thenReturn(null);
        mvc.perform(request()).andExpect(status().isNotFound());
        verify(consumer, never()).searchOffset(any(), anyLong());
    }

    @ParameterizedTest
    @CsvSource({"-1, 20", "20, 10"})
    void reportsUnavailableBoundsInsteadOfSuggestingAnOffset(long min, long max) throws Exception {
        readableQueue(min, max);
        mvc.perform(request()).andExpect(status().isBadGateway());
        verify(consumer, never()).searchOffset(any(), anyLong());
    }

    @Test
    void doesNotConvertFailedLookupIntoFirstRetainedOffset() throws Exception {
        readableQueue(10, 20);
        when(consumer.searchOffset(queue, 1788825600000L)).thenReturn(-1L);
        mvc.perform(request()).andExpect(status().isBadGateway());
    }

    @Test
    void reportsBrokerConnectionFailure() throws Exception {
        when(consumer.fetchSubscribeMessageQueues("orders")).thenThrow(new MQClientException("Connection failed", null));
        mvc.perform(request()).andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.startsWith(
                        "Failed to locate queue by time: Connection failed")));
    }

    @Test
    void preservesInstanceResolverRejectionBeforeContactingBroker() throws Exception {
        when(resolver.executePullConsumer(eq("instance-a"), any()))
                .thenThrow(new BusinessException(400, "Only Apache instances support this operation"));
        mvc.perform(request()).andExpect(status().isBadRequest());
        verifyNoInteractions(consumer);
    }

    @ParameterizedTest
    @CsvSource({"queueId, -1", "timestamp, -1", "timestamp, invalid", "instanceId, ''", "topic, ''", "brokerName, ''"})
    void rejectsInvalidCoordinatesBeforeResolvingAnInstance(String field, String value) throws Exception {
        mvc.perform(request(field, value)).andExpect(status().isBadRequest());
        verifyNoInteractions(consumer);
        verify(resolver, never()).executePullConsumer(any(), any());
    }

    private void readableQueue(long min, long max) throws Exception {
        when(consumer.fetchSubscribeMessageQueues("orders")).thenReturn(Set.of(queue));
        when(consumer.minOffset(queue)).thenReturn(min);
        when(consumer.maxOffset(queue)).thenReturn(max);
    }

    private MockHttpServletRequestBuilder request() {
        return request("timestamp", "1788825600000");
    }

    private MockHttpServletRequestBuilder request(String field, String value) {
        var parameters = new org.springframework.util.LinkedMultiValueMap<String, String>();
        parameters.set("instanceId", "instance-a");
        parameters.set("topic", "orders");
        parameters.set("brokerName", "broker-a");
        parameters.set("queueId", "2");
        parameters.set("timestamp", "1788825600000");
        parameters.set(field, value);
        return get("/api/messages/queue-position").params(parameters);
    }
}
