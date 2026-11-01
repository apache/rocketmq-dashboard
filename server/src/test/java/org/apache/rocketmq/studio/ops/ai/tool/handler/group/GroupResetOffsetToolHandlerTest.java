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

import java.util.List;
import java.util.Map;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetPreviewVO;
import org.apache.rocketmq.studio.instance.group.ResetConsumerOffsetQueuePreviewVO;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ResetOffsetOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GroupResetOffsetToolHandlerTest {

    @Mock
    private MetadataService metadataService;

    @InjectMocks
    private GroupResetOffsetToolHandler handler;

    @Test
    void applyShouldResetOffsetAndReturnOutput() {
        assertThat(handler.name()).isEqualTo("rmq.group.reset_offset");
        GroupResetOffsetInput input = new GroupResetOffsetInput(
                "untrusted-instance", "group-1", "TopicA", 1700000000000L);
        ToolExecutionContext execution = ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "untrusted-instance"));

        ResetOffsetOutput output = handler.execute(input, execution);

        assertThat(output.group()).isEqualTo("group-1");
        assertThat(output.topic()).isEqualTo("TopicA");
        assertThat(output.timestamp()).isEqualTo(1700000000000L);
        verify(metadataService).resetOffset(eq("instance-a"), eq("group-1"),
                eq(1700000000000L), eq("TopicA"));
    }

    @Test
    void applyShouldRejectMissingTimestampTest() {
        // Decision 13: the server no longer defaults to the current time.
        GroupResetOffsetInput input = new GroupResetOffsetInput(
                "untrusted-instance", "group-1", "TopicA", null);
        ToolExecutionContext execution = ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "untrusted-instance"));

        assertThatThrownBy(() -> handler.execute(input, execution))
                .isInstanceOf(BusinessException.class)
                .hasMessage("timestamp is required")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verifyNoInteractions(metadataService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void applyShouldRejectANonPositiveTimestampTest(long timestamp) {
        // The broker resolves a non-positive timestamp to the minimum offset, which
        // would rewind every queue; the tool must match the REST contract's @Positive.
        GroupResetOffsetInput input = new GroupResetOffsetInput(
                "untrusted-instance", "group-1", "TopicA", timestamp);
        ToolExecutionContext execution = ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "untrusted-instance"));

        assertThatThrownBy(() -> handler.execute(input, execution))
                .isInstanceOf(BusinessException.class)
                .hasMessage("timestamp must be positive")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verifyNoInteractions(metadataService);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    void previewShouldRejectANonPositiveTimestampTest(long timestamp) {
        // The L2 preview must fail before issuing a confirm token for the destructive rewind.
        GroupResetOffsetInput input = new GroupResetOffsetInput(
                "untrusted-instance", "group-1", "TopicA", timestamp);
        ToolExecutionContext execution = ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "untrusted-instance"));

        assertThatThrownBy(() -> handler.preview(input, execution))
                .isInstanceOf(BusinessException.class)
                .hasMessage("timestamp must be positive")
                .satisfies(error -> assertThat(((BusinessException) error).getCode()).isEqualTo(400));
        verifyNoInteractions(metadataService);
    }

    @Test
    void previewShouldRenderAnUnavailableLagInsteadOfTheSentinelTest() {
        // An offline group has no current offset, so the preview reports the -1 "cannot be
        // determined" sentinel for the current lag. The plan prose must never interpolate it, or
        // the operator is told "projected lag changes from -1 to 500".
        GroupResetOffsetInput input = new GroupResetOffsetInput(
                "untrusted-instance", "group-1", "TopicA", 1700000000000L);
        ToolExecutionContext execution = ToolExecutionContext.of(
                "instance-a", null, Map.of("instanceId", "untrusted-instance"));
        ResetConsumerOffsetPreviewVO preview = ResetConsumerOffsetPreviewVO.builder()
                .instanceId("instance-a")
                .groupName("group-1")
                .topic("TopicA")
                .timestamp(1700000000000L)
                .complete(true)
                .allowReset(true)
                .queueCount(1)
                .warningCount(1)
                .currentTotalLag(-1L)
                .projectedTotalLag(500L)
                .totalOffsetDelta(0L)
                .warnings(List.of(
                        "Consumer group is not online and no consume offset data is available"
                                + "; current offsets are unavailable, so offset deltas cannot be computed"))
                .queues(List.of(ResetConsumerOffsetQueuePreviewVO.builder()
                        .topic("TopicA")
                        .broker("broker-a")
                        .queueId(0)
                        .minOffset(10L)
                        .maxOffset(600L)
                        .brokerOffset(600L)
                        .consumerOffset(-1L)
                        .targetOffset(100L)
                        .currentLag(-1L)
                        .projectedLag(500L)
                        .offsetDelta(0L)
                        .riskLevel("INFO")
                        .message("Current offset unavailable (group offline); target offset 100")
                        .build()))
                .build();
        when(metadataService.previewResetOffset("instance-a", "group-1", 1700000000000L, "TopicA"))
                .thenReturn(preview);

        ToolPlan plan = handler.preview(input, execution);

        assertThat(plan.impact()).containsExactly(
                "Moves offsets for 1 queues; projected lag changes from unavailable to 500.");
        assertThat(plan.impact()).noneMatch(line -> line.contains("-1"));
        assertThat(plan.warnings()).containsExactly(
                "Consumer group is not online and no consume offset data is available"
                        + "; current offsets are unavailable, so offset deltas cannot be computed");
        // The structured before/after state keeps the documented sentinel rather than a fabricated
        // zero, so the caller can still tell "unknown" from "measured as nothing".
        assertThat(((Number) plan.before().get("totalLag")).longValue()).isEqualTo(-1L);
        assertThat(((Number) plan.after().get("totalLag")).longValue()).isEqualTo(500L);
    }
}
