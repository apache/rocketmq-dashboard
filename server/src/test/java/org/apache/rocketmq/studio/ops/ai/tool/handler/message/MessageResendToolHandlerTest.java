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
package org.apache.rocketmq.studio.ops.ai.tool.handler.message;

import org.apache.rocketmq.studio.instance.message.MessageRecordVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.SendMessageVO;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageResendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageResendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Map;

import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class MessageResendToolHandlerTest {

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"   ", " orders-retry "})
    void resolvesDestinationAndExecutesWithoutPreview(String targetTopic) {
        MetadataService metadata = mock(MetadataService.class);
        MessageResendToolHandler handler = new MessageResendToolHandler(metadata);
        ToolExecutionContext execution = context("instance-a", Map.of("msgId", "original"));
        MessageResendInput input = new MessageResendInput("instance-a", "original", null, targetTopic);
        MessageRecordVO source = MessageRecordVO.builder().msgId("original").topic("orders")
                .storeTime(1_800_000_000_000L).size(42).build();
        String destination = targetTopic == null || targetTopic.isBlank() ? "orders" : targetTopic.trim();
        when(metadata.findMessageForResend("instance-a", null, "original"))
                .thenReturn(source);
        when(metadata.resendMessage("instance-a", source, destination))
                .thenReturn(SendMessageVO.builder().msgId("resent").build());

        MessageResendOutput result = handler.execute(input, execution);

        verify(metadata).findMessageForResend("instance-a", null, "original");
        verify(metadata).resendMessage("instance-a", source, destination);
        verifyNoMoreInteractions(metadata);
        assertThat(result).isEqualTo(new MessageResendOutput("original", "resent", destination));

        ToolPlan plan = handler.preview(input, execution);
        assertThat(plan.before()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "msgId", "original", "topic", "orders", "storeTime", 1_800_000_000_000L, "size", 42));
        assertThat(plan.after()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "operation", "RESEND", "sourceMsgId", "original", "targetTopic", destination));
    }

    @Test
    void rejectsMissingDestinationWithoutPreview() {
        MetadataService metadata = mock(MetadataService.class);
        MessageResendToolHandler handler = new MessageResendToolHandler(metadata);
        MessageResendInput input = new MessageResendInput("instance-a", "original", null, null);
        when(metadata.findMessageForResend("instance-a", null, "original"))
                .thenReturn(MessageRecordVO.builder().msgId("original").build());

        assertThatThrownBy(() -> handler.execute(input, context("instance-a")))
                .isInstanceOf(ToolExecutionException.class)
                .hasMessage("target topic is required when the source message has no topic");
        verify(metadata).findMessageForResend("instance-a", null, "original");
        verifyNoMoreInteractions(metadata);
    }
}
