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
import org.apache.rocketmq.common.message.MessageQueue;
import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
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

import static org.assertj.core.api.Assertions.assertThat;
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
}
