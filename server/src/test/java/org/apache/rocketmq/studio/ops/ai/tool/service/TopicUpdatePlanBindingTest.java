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
package org.apache.rocketmq.studio.ops.ai.tool.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.instance.topic.MetadataService;
import org.apache.rocketmq.studio.instance.topic.TopicVO;
import org.apache.rocketmq.studio.ops.ai.tool.catalog.ToolCatalog;
import org.apache.rocketmq.studio.ops.ai.tool.contract.common.MutationOutput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.topic.TopicInput;
import org.apache.rocketmq.studio.ops.ai.tool.contract.plan.ToolPlan;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionContext;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolFilterChain;
import org.apache.rocketmq.studio.ops.ai.tool.filter.ToolMutationFilter;
import org.apache.rocketmq.studio.ops.ai.tool.handler.topic.TopicUpdateToolHandler;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TopicUpdatePlanBindingTest {

    private final ToolCatalog catalog = new ToolCatalog(new DefaultResourceLoader());
    private final ToolTokenService tokens = new ToolTokenService(new ObjectMapper(),
            Clock.fixed(Instant.parse("2026-10-10T00:00:00Z"), ZoneOffset.UTC),
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private final MetadataService metadata = mock(MetadataService.class);
    private final TopicUpdateToolHandler handler = new TopicUpdateToolHandler(metadata);
    private final ToolFilterChain chain = new ToolFilterChain(List.of(new ToolMutationFilter(tokens, false)));
    private final TopicVO current = new TopicInput("instance-dev", "orders", null, 8, 8, null, null).toTopicVO();

    @Test
    void rejectsChangedTopicBeforeAnyWriteTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders"))).thenReturn(Optional.of(current));
        String token = previewToken();
        current.setReadQueues(16);

        assertThatThrownBy(() -> apply(token)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("CONFLICT"));
        verify(metadata, never()).updateTopic(any(), any());
        verify(metadata, never()).createTopic(any(), any());
    }

    @Test
    void executesUnchangedTopicWithConfirmedTargetTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders"))).thenReturn(Optional.of(current));
        when(metadata.updateTopic(eq("instance-dev"), any())).thenAnswer(call -> call.getArgument(1));
        String token = previewToken();

        MutationOutput<?> output = apply(token);
        assertThat(output.status()).isEqualTo(MutationOutput.Status.EXECUTED);
        verify(metadata).updateTopic(eq("instance-dev"), any());
    }

    @Test
    void rejectsTopicCreatedSincePreviewTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders")))
                .thenReturn(Optional.empty(), Optional.of(current));
        String token = previewToken();

        assertConflictWithoutWrite(token);
    }

    @Test
    void rejectsTopicDeletedSincePreviewTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders")))
                .thenReturn(Optional.of(current), Optional.empty());
        String token = previewToken();

        assertConflictWithoutWrite(token);
    }

    @Test
    void dynamicMetricsDoNotInvalidateConfigurationConfirmationTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders"))).thenReturn(Optional.of(current));
        when(metadata.updateTopic(eq("instance-dev"), any())).thenAnswer(call -> call.getArgument(1));
        String token = previewToken();
        current.setTps(1000);
        current.setMessageCount(12345);
        current.setConsumerGroupCount(5);

        assertThat(apply(token).status()).isEqualTo(MutationOutput.Status.EXECUTED);
    }

    @Test
    void unboundLegacyTokenCannotAuthorizeTopicUpdateTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders"))).thenReturn(Optional.of(current));
        String token = tokens.issue(invocation(Map.of()).context());

        assertThatThrownBy(() -> apply(token)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("INVALID_ARGUMENT"));
        verify(metadata, never()).updateTopic(any(), any());
        verify(metadata, never()).createTopic(any(), any());
    }

    @Test
    void invalidTokenIsRejectedBeforeMetadataReadTest() {
        assertThatThrownBy(() -> apply("invalid")).isInstanceOf(ToolExecutionException.class);
        verifyNoInteractions(metadata);
    }

    @Test
    void failingExecutionPreviewDoesNotWriteTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders"))).thenReturn(Optional.of(current));
        String token = previewToken();
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders")))
                .thenThrow(new IllegalStateException("metadata unavailable"));
        clearInvocations(metadata);

        assertThatThrownBy(() -> apply(token)).isInstanceOf(IllegalStateException.class)
                .hasMessage("metadata unavailable");
        verify(metadata, never()).updateTopic(any(), any());
        verify(metadata, never()).createTopic(any(), any());
    }

    @Test
    void presentationTextDoesNotChangeTheConfirmationProjectionTest() {
        when(metadata.findTopic(eq("instance-dev"), isNull(), eq("orders"))).thenReturn(Optional.of(current));
        var plan = handler.preview(invocation(Map.of()).context()
                .convertInput(handler.inputType()), invocation(Map.of()).context());
        var reworded = new ToolPlan(
                "New wording", List.of("New explanation"), plan.before(), plan.after(), List.of("New warning"));
        assertThat(handler.confirmationState(reworded)).isEqualTo(handler.confirmationState(plan));
    }

    private void assertConflictWithoutWrite(String token) {
        assertThatThrownBy(() -> apply(token)).isInstanceOfSatisfying(ToolExecutionException.class,
                failure -> assertThat(failure.getErrorCode()).isEqualTo("CONFLICT"));
        verify(metadata, never()).updateTopic(any(), any());
        verify(metadata, never()).createTopic(any(), any());
    }

    private String previewToken() {
        return ((MutationOutput<?>) chain.execute(invocation(Map.of("dry_run", true)))).confirmToken();
    }

    private MutationOutput<?> apply(String token) {
        return (MutationOutput<?>) chain.execute(invocation(Map.of("confirm_token", token)));
    }

    private ToolInvocation invocation(Map<String, Object> controls) {
        Map<String, Object> input = new LinkedHashMap<>(Map.of(
                "instanceId", "instance-dev", "topicName", "orders", "writeQueues", 12));
        input.putAll(controls);
        return new ToolInvocation(ToolExecutionContext.of("instance-dev",
                catalog.getDefinition(handler.name()), input, "alice"), handler);
    }
}
