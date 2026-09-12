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
package org.apache.rocketmq.studio.ops.ai.tool.handler.dlq;

import org.apache.rocketmq.studio.instance.dlq.DLQResendResultVO;
import org.apache.rocketmq.studio.instance.dlq.DLQService;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQResendInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.TimeRange;
import org.apache.rocketmq.studio.ops.ai.tool.contract.dlq.DLQResendOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.apache.rocketmq.studio.ops.ai.tool.TestToolExecutionContexts.context;

@ExtendWith(MockitoExtension.class)
class DLQResendToolHandlerTest {

    @Mock
    private DLQService dlqService;

    @InjectMocks
    private DLQResendToolHandler handler;

    @Test
    void executeDelegatesToResendMessagesAndReturnsOutput() {
        assertThat(handler.name()).isEqualTo("rmq.dlq.resend");
        DLQResendResultVO resultVO = DLQResendResultVO.builder()
                .matched(10)
                .resent(8)
                .failed(2)
                .outcome("PARTIAL")
                .scanIncomplete(false)
                .failedQueueCount(0)
                .build();
        when(dlqService.resendMessages(eq("instance-a"), eq("group-1"),
                eq(1000L), eq(2000L), eq("TopicA-retry")))
                .thenReturn(resultVO);

        DLQResendInput input = new DLQResendInput(
                "instance-a", "group-1", new TimeRange(1000L, 2000L), "TopicA-retry");
        DLQResendOutput output = handler.execute(input, context("instance-a"));
        assertThat(output.matched()).isEqualTo(10);
        assertThat(output.resent()).isEqualTo(8);
        assertThat(output.failed()).isEqualTo(2);
        assertThat(output.outcome()).isEqualTo("PARTIAL");
        assertThat(output.scanIncomplete()).isFalse();
        assertThat(output.failedQueueCount()).isEqualTo(0);
        verify(dlqService).resendMessages(eq("instance-a"), eq("group-1"),
                eq(1000L), eq(2000L), eq("TopicA-retry"));
        verifyNoMoreInteractions(dlqService);
    }

    @Test
    void executePassesNullTimeRangeWhenAbsent() {
        when(dlqService.resendMessages(eq("instance-a"), eq("group-1"),
                eq(null), eq(null), eq(null)))
                .thenReturn(DLQResendResultVO.builder().matched(0).resent(0).outcome("NO_MESSAGES").build());

        DLQResendInput input = new DLQResendInput("instance-a", "group-1", null, null);
        DLQResendOutput output = handler.execute(input, context("instance-a"));
        assertThat(output.matched()).isZero();
        verify(dlqService).resendMessages(eq("instance-a"), eq("group-1"),
                eq(null), eq(null), eq(null));
        verifyNoMoreInteractions(dlqService);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "TopicA-retry"})
    void previewPreservesScopeAndResendEffect(String targetTopic) {
        Map<String, Object> input = new LinkedHashMap<>(Map.of(
                "group", "group-1", "time", Map.of("startTime", 1000L, "endTime", 2000L), "dry_run", true));
        input.put("targetTopic", targetTopic);
        ToolExecutionContext ctx = context("instance-a", input);

        ToolPlan plan = handler.preview(
                ctx.convertInput(DLQResendInput.class), ctx);

        assertThat(plan.summary()).isEqualTo("resend DLQ messages for consumer group 'group-1' in cluster 'instance-a'.");
        assertThat(plan.impact()).containsExactly("Republishes matching dead-letter messages for consumer group 'group-1' without deleting the originals.");
        assertThat(plan.before()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "group", "group-1",
                "targetTopic", targetTopic == null || targetTopic.isBlank() ? "ORIGINAL_TOPIC" : targetTopic,
                "startTime", 1000L, "endTime", 2000L));
        assertThat(plan.after()).containsExactlyInAnyOrderEntriesOf(Map.of(
                "operation", "RESEND",
                "targetTopic", targetTopic == null || targetTopic.isBlank() ? "ORIGINAL_TOPIC" : targetTopic));
        verifyNoMoreInteractions(dlqService);
    }
}
