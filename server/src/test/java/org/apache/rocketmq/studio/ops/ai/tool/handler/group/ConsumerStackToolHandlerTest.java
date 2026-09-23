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
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import org.apache.rocketmq.studio.instance.group.ConsumerDiagnosticsService;
import org.apache.rocketmq.studio.instance.group.ConsumerStackTraceVO;
import org.apache.rocketmq.studio.instance.group.ConsumerThreadStackVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ConsumerStackInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ConsumerStackOutput;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConsumerStackToolHandlerTest {

    @Mock
    private ConsumerDiagnosticsService consumerDiagnosticsService;

    @Test
    void capturesTheSelectedConsumerClientStackThroughTheExistingService() {
        ConsumerStackTraceVO stack = ConsumerStackTraceVO.builder()
                .groupName("cg-orders")
                .clientId("client-1")
                .capturedAt(LocalDateTime.of(2026, 8, 22, 9, 30))
                .threadCount(1)
                .threads(List.of(ConsumerThreadStackVO.builder()
                        .threadName("ConsumeMessageThread_1")
                        .threadId(12)
                        .state("RUNNABLE")
                        .blockedTime(0)
                        .waitedTime(0)
                        .stackTrace(List.of("com.example.Listener.consume(Listener.java:42)"))
                        .build()))
                .build();
        when(consumerDiagnosticsService.getConsumerStack("instance-a", "cg-orders", "client-1"))
                .thenReturn(stack);

        ConsumerStackOutput output = new ConsumerStackToolHandler(consumerDiagnosticsService).execute(
                new ConsumerStackInput("untrusted-instance", "cg-orders", "client-1"),
                ToolExecutionContext.of("instance-a", null, Map.of("instanceId", "instance-a")));

        assertThat(output.instanceId()).isEqualTo("instance-a");
        assertThat(output.groupName()).isEqualTo("cg-orders");
        assertThat(output.clientId()).isEqualTo("client-1");
        assertThat(output.capturedAt()).isEqualTo("2026-08-22T09:30");
        assertThat(output.threadCount()).isEqualTo(1);
        assertThat(output.threads()).singleElement()
                .satisfies(thread -> {
                    assertThat(thread.threadName()).isEqualTo("ConsumeMessageThread_1");
                    assertThat(thread.threadId()).isEqualTo(12);
                    assertThat(thread.stackTrace())
                            .containsExactly("com.example.Listener.consume(Listener.java:42)");
                });
        verify(consumerDiagnosticsService).getConsumerStack("instance-a", "cg-orders", "client-1");
    }
}
