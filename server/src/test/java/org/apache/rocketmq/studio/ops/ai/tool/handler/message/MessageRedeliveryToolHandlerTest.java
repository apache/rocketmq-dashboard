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
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.message.MessageRedeliveryOutput;
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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MessageRedeliveryToolHandlerTest {

    @Test
    void defaultsToGroupRetryTopicAndExecutesTest() {
        MetadataService metadata = mock(MetadataService.class);
        MessageRedeliveryToolHandler handler = new MessageRedeliveryToolHandler(metadata);
        ToolExecutionContext execution = context("instance-a", Map.of("msgId", "original"));
        MessageRedeliveryInput input =
                new MessageRedeliveryInput("instance-a", "group-a", "original", null, null);
        MessageRecordVO source = MessageRecordVO.builder().msgId("original").topic("orders")
                .storeTime(1_800_000_000_000L).size(42).build();
        when(metadata.findMessageForRedelivery("instance-a", null, "original"))
                .thenReturn(source);
        when(metadata.redeliverMessage("instance-a", "group-a", null, "original", null))
                .thenReturn(SendMessageVO.builder().msgId("redelivered").build());

        MessageRedeliveryOutput result = handler.execute(input, execution);

        verify(metadata).redeliverMessage("instance-a", "group-a", null, "original", null);
        assertThat(result).isEqualTo(
                new MessageRedeliveryOutput("original", "redelivered", "%RETRY%group-a"));

        ToolPlan plan = handler.preview(input, execution);
        assertThat(plan.summary()).isEqualTo(
                "redeliver message 'original' for consumer group 'group-a' in instance 'instance-a'.");
        assertThat(plan.before()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "msgId", "original", "topic", "orders", "storeTime", 1_800_000_000_000L, "size", 42));
        assertThat(plan.after()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "operation", "REDELIVERY", "sourceMsgId", "original", "targetTopic", "%RETRY%group-a"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"TopicB", " TopicB "})
    void explicitTargetTopicOverridesRetryTopicTest(String targetTopic) {
        MetadataService metadata = mock(MetadataService.class);
        MessageRedeliveryToolHandler handler = new MessageRedeliveryToolHandler(metadata);
        MessageRedeliveryInput input =
                new MessageRedeliveryInput("instance-a", "group-a", "original", "orders", targetTopic);
        when(metadata.redeliverMessage("instance-a", "group-a", "orders", "original", targetTopic))
                .thenReturn(SendMessageVO.builder().msgId("redelivered").build());

        MessageRedeliveryOutput result = handler.execute(input, context("instance-a"));

        verify(metadata).redeliverMessage("instance-a", "group-a", "orders", "original", targetTopic);
        assertThat(result).isEqualTo(
                new MessageRedeliveryOutput("original", "redelivered", "TopicB"));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void missingGroupNameIsRejectedAsInvalidArgumentTest(String groupName) {
        MetadataService metadata = mock(MetadataService.class);
        MessageRedeliveryToolHandler handler = new MessageRedeliveryToolHandler(metadata);
        MessageRedeliveryInput input =
                new MessageRedeliveryInput("instance-a", groupName, "original", null, null);

        assertThatThrownBy(() -> handler.execute(input, context("instance-a")))
                .isInstanceOf(ToolExecutionException.class)
                .satisfies(exception -> assertThat(((ToolExecutionException) exception).getErrorCode())
                        .isEqualTo("INVALID_ARGUMENT"))
                .hasMessage("Missing required tool parameter: groupName");

        assertThatThrownBy(() -> handler.preview(input, context("instance-a")))
                .isInstanceOf(ToolExecutionException.class)
                .satisfies(exception -> assertThat(((ToolExecutionException) exception).getErrorCode())
                        .isEqualTo("INVALID_ARGUMENT"));

        verifyNoInteractions(metadata);
    }
}
