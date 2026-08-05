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
package org.apache.rocketmq.studio.rocketmq;

import org.apache.rocketmq.client.consumer.DefaultMQPullConsumer;
import org.apache.rocketmq.client.QueryResult;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.message.TraceRecordVO;
import org.apache.rocketmq.studio.queryhistory.QueryHistoryService;
import org.apache.rocketmq.tools.admin.DefaultMQAdminExt;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RocketMQMessageProviderTest {

    @Mock
    private ObjectProvider<DefaultMQAdminExt> adminExtProvider;

    @Mock
    private DefaultMQAdminExt adminExt;

    @Mock
    private QueryHistoryService queryHistoryService;

    private RocketMQMessageProvider provider;

    @BeforeEach
    void setUp() {
        when(adminExtProvider.getIfAvailable()).thenReturn(adminExt);
        provider = new RocketMQMessageProvider(adminExtProvider, queryHistoryService, new RocketMQProperties());
    }

    @Test
    void queryByTopicReturnsEmptyListWhenQueueSetIsNull() throws Exception {
        try (MockedConstruction<DefaultMQPullConsumer> mockedConsumers =
                     mockConstruction(DefaultMQPullConsumer.class, (consumer, context) -> {
                         doNothing().when(consumer).start();
                         when(consumer.fetchSubscribeMessageQueues("TopicA")).thenReturn(null);
                         doNothing().when(consumer).shutdown();
                     })) {
            List<MessageRecordVO> messages = provider.queryMessages("TopicA", null, null, null, 100L, 200L);

            assertThat(messages).isEmpty();
            assertThat(mockedConsumers.constructed()).hasSize(1);
            DefaultMQPullConsumer consumer = mockedConsumers.constructed().get(0);
            verify(consumer).start();
            verify(consumer).fetchSubscribeMessageQueues("TopicA");
            verify(consumer, never()).pull(any(MessageQueue.class), anyString(), anyLong(), anyInt());
            verify(consumer).shutdown();
        }
        verify(queryHistoryService).recordMessageQuery("TOPIC", "TopicA", null, null, null, 100L, 200L, 0);
    }

    @Test
    void traceUsesConfiguredWindowAndMarksFailedTransaction() throws Exception {
        RocketMQProperties properties = new RocketMQProperties();
        properties.setTraceQueryWindow(Duration.ofHours(6));
        provider = new RocketMQMessageProvider(adminExtProvider, queryHistoryService, properties);
        MessageExt trace = new MessageExt();
        trace.setBody(("EndTransaction\u0001123\u0001region\u0001group\u0001topic\u0001target\u0001tags"
                + "\u0001keys\u0001store\u0001client\u00010\u0001NORMAL\u0001false\u0001tx\u0001ROLLBACK\n")
                .getBytes());
        when(adminExt.queryMessage(eq("RMQ_SYS_TRACE_TOPIC"), eq("target"), anyInt(), anyLong(), anyLong()))
                .thenReturn(new QueryResult(0L, List.of(trace)));

        long before = System.currentTimeMillis();
        TraceRecordVO result = provider.getMessageTrace("target");

        assertThat(result.getNodes()).singleElement().satisfies(node -> {
            assertThat(node.getStatus()).isEqualTo("failed");
            assertThat(node.getDescription()).contains("ROLLBACK");
        });
        org.mockito.ArgumentCaptor<Long> begin = org.mockito.ArgumentCaptor.forClass(Long.class);
        verify(adminExt).queryMessage(eq("RMQ_SYS_TRACE_TOPIC"), eq("target"), anyInt(), begin.capture(), anyLong());
        assertThat(begin.getValue()).isBetween(before - Duration.ofHours(6).toMillis() - 1_000L,
                before - Duration.ofHours(6).toMillis() + 1_000L);
    }

    @Test
    void traceQueryFailureIsNotReturnedAsAnEmptyTrace() throws Exception {
        when(adminExt.queryMessage(anyString(), anyString(), anyInt(), anyLong(), anyLong()))
                .thenThrow(new RuntimeException("ACL denied"));

        assertThatThrownBy(() -> provider.getMessageTrace("target"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("ACL denied")
                .satisfies(ex -> assertThat(((BusinessException) ex).getCode()).isEqualTo(502));
    }
}
