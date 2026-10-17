/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 */
package org.apache.rocketmq.studio.ops.ai.tool.handler.group;

import java.util.Map;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.GroupResetOffsetInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.group.ResetOffsetOutput;
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
}
