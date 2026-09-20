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
package org.apache.rocketmq.studio.ops.ai.conversation.event;

import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEventProjector.Projection;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The exhaustive {@link AgentEvent} to ({@link LiveEvent}, {@link TimelineEvent}) mapping: what each
 * subtype projects to, what it does not, and the stateful parts that a pure mapping cannot do —
 * tool durations measured from the remembered start, tool names recovered from the remembered call,
 * deltas coalesced into blocks, and tool output sanitised identically on both sides.
 */
class AgentEventProjectorTest {

    private static final long RUN_ID = 41L;
    private static final String TC_ID = "toolu_01A";
    private static final String TOOL = "rmq.topic.list";
    private static final Map<String, Object> INPUT = Map.of("instanceId", "open-source-local");
    private static final String OUTPUT = "{\"items\":[{\"name\":\"StudioTest\"}]}";

    /** Fixed clock so a tool duration is a number that can be asserted instead of tolerated. */
    private final AtomicLong clock = new AtomicLong(1_000_000_000L);

    private AgentEventProjector newProjector() {
        return new AgentEventProjector(RUN_ID, clock::get);
    }

    // ---------------------------------------------------------------- coalescing

    @Test
    void textDeltaProjectsLiveFrameAndBuffersThePersistedBlockTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.TextDelta("the cluster has "));

        assertThat(projection.live()).isEqualTo(new LiveEvent.TextDelta("the cluster has "));
        assertThat(projection.persisted()).isEmpty();
        assertThat(projector.hasPending()).isTrue();
        assertThat(projector.flushPending()).contains(new TimelineEvent.Text("the cluster has "));
        assertThat(projector.hasPending()).isFalse();
    }

    @Test
    void consecutiveTextDeltasCoalesceIntoOnePersistedBlockTest() {
        AgentEventProjector projector = newProjector();

        assertThat(projector.project(new AgentEvent.TextDelta("the cluster ")).persisted()).isEmpty();
        assertThat(projector.project(new AgentEvent.TextDelta("has 2 brokers ")).persisted()).isEmpty();
        assertThat(projector.project(new AgentEvent.TextDelta("and 40 topics.")).persisted()).isEmpty();

        assertThat(projector.flushPending())
                .contains(new TimelineEvent.Text("the cluster has 2 brokers and 40 topics."));
    }

    @Test
    void flushPendingIsEmptyWhenNothingIsBufferedTest() {
        AgentEventProjector projector = newProjector();

        assertThat(projector.flushPending()).isEmpty();
        assertThat(projector.hasPending()).isFalse();
        projector.project(new AgentEvent.TextDelta("drained"));
        assertThat(projector.flushPending()).isPresent();
        assertThat(projector.flushPending()).isEmpty();
    }

    @Test
    void consecutiveThinkingDeltasFromOneSourceCoalesceIntoOnePersistedBlockTest() {
        AgentEventProjector projector = newProjector();

        assertThat(projector.project(new AgentEvent.ThinkingDelta("the user wants lag; ",
                ThinkingSource.MODEL)).persisted()).isEmpty();
        Projection second = projector.project(new AgentEvent.ThinkingDelta("check the routes first",
                ThinkingSource.MODEL));

        assertThat(second.live()).isEqualTo(new LiveEvent.Thinking("check the routes first",
                ThinkingSource.MODEL));
        assertThat(second.persisted()).isEmpty();
        assertThat(projector.flushPending()).contains(new TimelineEvent.Thinking(
                "the user wants lag; check the routes first", ThinkingSource.MODEL));
    }

    @Test
    void thinkingSourceChangeCutsThePersistedBlockTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.ThinkingDelta("model reasoning", ThinkingSource.MODEL));

        Projection enhance = projector.project(new AgentEvent.ThinkingDelta("rewritten prompt",
                ThinkingSource.ENHANCE));

        assertThat(enhance.persisted())
                .as("model reasoning and a prompt rewrite must never land in one block")
                .containsExactly(new TimelineEvent.Thinking("model reasoning", ThinkingSource.MODEL));
        assertThat(projector.flushPending())
                .contains(new TimelineEvent.Thinking("rewritten prompt", ThinkingSource.ENHANCE));
    }

    @Test
    void textAndThinkingInterleaveInArrivalOrderTest() {
        AgentEventProjector projector = newProjector();
        List<TimelineEvent> persisted = new ArrayList<>();

        persisted.addAll(projector.project(new AgentEvent.TextDelta("prose before")).persisted());
        persisted.addAll(projector.project(new AgentEvent.ThinkingDelta("reasoning",
                ThinkingSource.MODEL)).persisted());
        persisted.addAll(projector.project(new AgentEvent.TextDelta("prose after")).persisted());
        projector.flushPending().ifPresent(persisted::add);

        assertThat(persisted).containsExactly(
                new TimelineEvent.Text("prose before"),
                new TimelineEvent.Thinking("reasoning", ThinkingSource.MODEL),
                new TimelineEvent.Text("prose after"));
    }

    @Test
    void pendingBlockIsWrittenBeforeTheNextToolUseTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.ThinkingDelta("list the topics", ThinkingSource.MODEL));

        Projection projection = projector.project(new AgentEvent.ToolInputComplete(TC_ID, TOOL, INPUT));

        assertThat(projection.persisted()).containsExactly(
                new TimelineEvent.Thinking("list the topics", ThinkingSource.MODEL),
                new TimelineEvent.ToolUse(TC_ID, TOOL, INPUT));
    }

    @Test
    void coalesceCeilingCutsATextBlockTest() {
        AgentEventProjector projector = newProjector();
        String below = "a".repeat(AgentEventProjector.COALESCE_MAX_CHARS - 1);

        assertThat(projector.project(new AgentEvent.TextDelta(below)).persisted()).isEmpty();
        Projection crossing = projector.project(new AgentEvent.TextDelta("b"));

        assertThat(crossing.persisted())
                .containsExactly(new TimelineEvent.Text(below + "b"));
        assertThat(projector.hasPending()).isFalse();
    }

    @Test
    void coalesceCeilingCutsAThinkingBlockTest() {
        AgentEventProjector projector = newProjector();
        String chunk = "t".repeat(AgentEventProjector.COALESCE_MAX_CHARS);

        Projection projection = projector.project(new AgentEvent.ThinkingDelta(chunk,
                ThinkingSource.MODEL));

        assertThat(projection.persisted())
                .containsExactly(new TimelineEvent.Thinking(chunk, ThinkingSource.MODEL));
        assertThat(projection.live()).isEqualTo(new LiveEvent.Thinking(chunk, ThinkingSource.MODEL));
    }

    @Test
    void coalesceCeilingIsTheNumberTheClientUsesTest() {
        // DEFAULT_COALESCE_MAX_CHARS in web/src/pages/ai/render/foldTimeline.ts. Change one, change both.
        assertThat(AgentEventProjector.COALESCE_MAX_CHARS).isEqualTo(2048);
    }

    // ---------------------------------------------------------------- tool calls

    @Test
    void toolStartIsRememberedButProjectsNothingTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ToolStart(TC_ID, TOOL));

        assertThat(projection.live()).isNull();
        assertThat(projection.persisted()).isEmpty();
        assertThat(projection.isEmpty()).isTrue();
    }

    @Test
    void toolInputCompleteProjectsLiveToolStartAndPersistedToolUseTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.ToolStart(TC_ID, TOOL));

        Projection projection = projector.project(new AgentEvent.ToolInputComplete(TC_ID, TOOL, INPUT));

        assertThat(projection.live()).isEqualTo(new LiveEvent.ToolStart(TC_ID, TOOL, INPUT));
        assertThat(projection.persisted()).containsExactly(new TimelineEvent.ToolUse(TC_ID, TOOL, INPUT));
    }

    @Test
    void toolInputCompleteWithoutAPriorStartStillProjectsTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ToolInputComplete(TC_ID, TOOL, INPUT));

        assertThat(projection.live()).isEqualTo(new LiveEvent.ToolStart(TC_ID, TOOL, INPUT));
        assertThat(projection.persisted()).containsExactly(new TimelineEvent.ToolUse(TC_ID, TOOL, INPUT));
    }

    @Test
    void toolCallWithoutArgumentsProjectsAnEmptyInputObjectTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ToolInputComplete(TC_ID, TOOL, null));

        // The client types tool_start.input as a required Record<string, unknown>, and
        // @JsonInclude(NON_NULL) would drop a null input from the frame altogether. A tool that
        // takes no arguments must still arrive as an object, on both sides and identically.
        Object liveInput = ((LiveEvent.ToolStart) projection.live()).input();
        assertThat(liveInput).isNotNull().isEqualTo(Map.of());
        assertThat(((TimelineEvent.ToolUse) projection.persisted().get(0)).input())
                .as("a replayed tool block must carry the same input as the live one")
                .isEqualTo(liveInput);
    }

    @Test
    void toolDoneComputesDurationFromTheRememberedStartTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.ToolStart(TC_ID, TOOL));
        clock.addAndGet(212_000_000L);

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, OUTPUT, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        TimelineEvent.ToolResult persisted = (TimelineEvent.ToolResult) projection.persisted().get(0);
        assertThat(live.durationMs()).isEqualTo(212L);
        assertThat(persisted.durationMs()).isEqualTo(212L);
    }

    @Test
    void toolDonePrefersTheDurationTheProviderReportedTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.ToolStart(TC_ID, TOOL));
        clock.addAndGet(212_000_000L);

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, OUTPUT, true,
                999L, null));

        assertThat(((LiveEvent.ToolDone) projection.live()).durationMs()).isEqualTo(999L);
    }

    @Test
    void toolDoneIsLabelledWithTheToolRememberedAtStartTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.ToolStart(TC_ID, TOOL));

        // The provider's result frame carries the call id only, never the tool name.
        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, null, OUTPUT, true,
                null, null));

        assertThat(((LiveEvent.ToolDone) projection.live()).tool()).isEqualTo(TOOL);
        assertThat(((TimelineEvent.ToolResult) projection.persisted().get(0)).tool()).isEqualTo(TOOL);
    }

    @Test
    void toolDoneWithoutARememberedStartHasNoDurationTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ToolDone("toolu_unknown", null, OUTPUT,
                true, null, null));

        assertThat(((LiveEvent.ToolDone) projection.live()).durationMs()).isNull();
        assertThat(((LiveEvent.ToolDone) projection.live()).tool()).isNull();
    }

    @Test
    void toolOutputAtTheCapIsNotTruncatedTest() {
        AgentEventProjector projector = newProjector();
        String output = "a".repeat(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES);

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, output, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        TimelineEvent.ToolResult persisted = (TimelineEvent.ToolResult) projection.persisted().get(0);
        assertThat(live.outputBytes()).isEqualTo(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES);
        assertThat(live.truncated()).isFalse();
        assertThat(live.output()).isEqualTo(output);
        assertThat(persisted.outputBytes()).isEqualTo(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES);
        assertThat(persisted.truncated()).isFalse();
    }

    @Test
    void toolOutputOneByteOverTheCapIsTruncatedOnBothSidesTest() {
        AgentEventProjector projector = newProjector();
        String output = "a".repeat(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES + 1);

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, output, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        TimelineEvent.ToolResult persisted = (TimelineEvent.ToolResult) projection.persisted().get(0);
        assertThat(live.outputBytes())
                .as("outputBytes is the true size, not the size of what is shown")
                .isEqualTo(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES + 1);
        assertThat(live.truncated()).isTrue();
        assertThat(live.output()).hasSize(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES);
        assertThat(persisted.outputBytes()).isEqualTo(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES + 1);
        assertThat(persisted.truncated()).isTrue();
        assertThat(persisted.output()).isEqualTo(live.output());
    }

    @Test
    void truncationNeverSplitsAMultiByteCharacterTest() {
        AgentEventProjector projector = newProjector();
        String output = "byte" + "\u8282".repeat(11000);

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, output, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        assertThat(live.truncated()).isTrue();
        assertThat(live.outputBytes()).isEqualTo(output.getBytes(StandardCharsets.UTF_8).length);
        assertThat(live.output().getBytes(StandardCharsets.UTF_8).length)
                .as("the cut must land on a character boundary, not mid character")
                .isLessThanOrEqualTo(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES)
                .isGreaterThan(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES - 4);
        assertThat(live.output()).doesNotContain("\uFFFD");
    }

    @Test
    void inlineBase64IsStrippedFromBothSidesTest() {
        AgentEventProjector projector = newProjector();
        String blob = "data:image/png;base64," + "A".repeat(64);
        String output = "{\"chart\":\"" + blob + "\",\"rows\":3}";

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, output, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        TimelineEvent.ToolResult persisted = (TimelineEvent.ToolResult) projection.persisted().get(0);
        String expected = "{\"chart\":\"[base64 image/png omitted]\",\"rows\":3}";
        assertThat(live.output()).isEqualTo(expected);
        assertThat(persisted.output())
                .as("a replayed tool result must read exactly like the live one did")
                .isEqualTo(live.output());
        assertThat(live.outputBytes()).isEqualTo(expected.getBytes(StandardCharsets.UTF_8).length);
        assertThat(live.truncated()).isFalse();
    }

    @Test
    void strippedBase64DoesNotCountTowardsTheOutputCapTest() {
        AgentEventProjector projector = newProjector();
        String blob = "data:application/octet-stream;base64,"
                + "A".repeat(AgentEventProjector.MAX_TOOL_OUTPUT_BYTES * 2);

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, blob, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        assertThat(live.output()).isEqualTo("[base64 application/octet-stream omitted]");
        assertThat(live.truncated())
                .as("stripping happens before measuring, so the blob cannot trigger the cap")
                .isFalse();
        assertThat(live.outputBytes()).isEqualTo(live.output().getBytes(StandardCharsets.UTF_8).length);
    }

    @Test
    void nullToolOutputProjectsNoOutputAndNoByteCountTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, null, true,
                null, null));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        assertThat(live.output()).isNull();
        assertThat(live.outputBytes()).isNull();
        assertThat(live.truncated()).isFalse();
    }

    @Test
    void failedToolDoneKeepsItsErrorOnBothSidesTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ToolDone(TC_ID, TOOL, null, false,
                null, "instance open-source-local is unreachable"));

        LiveEvent.ToolDone live = (LiveEvent.ToolDone) projection.live();
        TimelineEvent.ToolResult persisted = (TimelineEvent.ToolResult) projection.persisted().get(0);
        assertThat(live.success()).isFalse();
        assertThat(live.error()).isEqualTo("instance open-source-local is unreachable");
        assertThat(persisted.success()).isFalse();
        assertThat(persisted.error()).isEqualTo(live.error());
    }

    // ---------------------------------------------------------------- run terminal states

    @Test
    void resultMetaSuccessProjectsRunFinishedAndTerminalRunStatusTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ResultMeta("280b366b", 8123L, 6, 29376,
                "success"));

        assertThat(projection.live()).isEqualTo(new LiveEvent.RunFinished(RUN_ID, RunStatus.COMPLETED,
                8123L));
        assertThat(projection.persisted())
                .containsExactly(new TimelineEvent.RunStatus(RunStatus.COMPLETED, null));
    }

    @Test
    void resultMetaSuccessWritesPendingProseBeforeTheRunStatusTest() {
        AgentEventProjector projector = newProjector();
        projector.project(new AgentEvent.TextDelta("all done."));

        Projection projection = projector.project(new AgentEvent.ResultMeta("280b366b", 8123L, 6, 29376,
                "success"));

        assertThat(projection.persisted()).containsExactly(
                new TimelineEvent.Text("all done."),
                new TimelineEvent.RunStatus(RunStatus.COMPLETED, null));
    }

    @Test
    void resultMetaErrorMaxTurnsProjectsErrorOnBothSidesTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ResultMeta("280b366b", 1000L, 6, 100,
                "error_max_turns"));

        LiveEvent.Error expected = new LiveEvent.Error("llm.provider.error_max_turns",
                "agent reached the maximum turn count", "Split the request.");
        assertThat(projection.live()).isEqualTo(expected);
        assertThat(projection.persisted()).containsExactly(new TimelineEvent.Error(expected.code(),
                expected.message(), expected.hint()));
    }

    @Test
    void resultMetaWithAnotherFailureSubtypeProjectsAGenericErrorTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ResultMeta("280b366b", 1000L, 6, 100,
                "error_during_execution"));

        assertThat(projection.live()).isEqualTo(new LiveEvent.Error("llm.provider.error_during_execution",
                "agent run ended with subtype: error_during_execution", null));
    }

    @Test
    void resultMetaWithoutASubtypeIsStillAnErrorTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ResultMeta("280b366b", 1000L, 6, 100,
                null));

        assertThat(((LiveEvent.Error) projection.live()).code()).isEqualTo("llm.provider.unknown");
        assertThat(projection.persisted()).hasSize(1);
    }

    // ---------------------------------------------------------------- notices

    @Test
    void initMetaWithoutOurMcpServerWarnsLiveOnlyTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.InitMeta("280b366b", List.of("filesystem"),
                List.of("mcp__filesystem__read")));

        assertThat(projection.live()).isEqualTo(new LiveEvent.Notice(AgentEventProjector.LEVEL_WARN,
                "MCP server rocketmq-studio did not connect"));
        assertThat(projection.persisted()).isEmpty();
    }

    @Test
    void initMetaWithOurMcpServerProjectsNothingTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.InitMeta("280b366b",
                List.of("rocketmq-studio"), List.of("mcp__rocketmq-studio__rmq_topic_list")));

        assertThat(projection.isEmpty()).isTrue();
    }

    @Test
    void initMetaWithoutAServerListSaysNothingTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.InitMeta("280b366b", null, null));

        assertThat(projection.isEmpty())
                .as("an absent list is not evidence that the server failed to connect")
                .isTrue();
    }

    @Test
    void providerNoticeProjectsBothSidesTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ProviderNotice(
                AgentEventProjector.LEVEL_WARN, "2 tool calls were denied by permission"));

        assertThat(projection.live()).isEqualTo(new LiveEvent.Notice(AgentEventProjector.LEVEL_WARN,
                "2 tool calls were denied by permission"));
        assertThat(projection.persisted()).containsExactly(new TimelineEvent.Notice(
                AgentEventProjector.LEVEL_WARN, "2 tool calls were denied by permission"));
    }

    @Test
    void unhandledUpstreamProjectsAWarnNoticeOnBothSidesTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.UnhandledUpstream("signature_delta"));

        assertThat(projection.live()).isEqualTo(new LiveEvent.Notice(AgentEventProjector.LEVEL_WARN,
                "unhandled upstream event type: signature_delta"));
        assertThat(projection.persisted()).containsExactly(new TimelineEvent.Notice(
                AgentEventProjector.LEVEL_WARN, "unhandled upstream event type: signature_delta"));
    }

    // ---------------------------------------------------------------- degenerate input

    @Test
    void emptyDeltasProjectNothingTest() {
        AgentEventProjector projector = newProjector();

        assertThat(projector.project(new AgentEvent.TextDelta("")).isEmpty()).isTrue();
        assertThat(projector.project(new AgentEvent.TextDelta(null)).isEmpty()).isTrue();
        assertThat(projector.project(new AgentEvent.ThinkingDelta("", ThinkingSource.MODEL)).isEmpty())
                .isTrue();
        assertThat(projector.hasPending()).isFalse();
    }

    @Test
    void thinkingDeltaWithoutASourceIsTreatedAsModelReasoningTest() {
        AgentEventProjector projector = newProjector();

        Projection projection = projector.project(new AgentEvent.ThinkingDelta("reasoning", null));

        assertThat(projection.live()).isEqualTo(new LiveEvent.Thinking("reasoning", ThinkingSource.MODEL));
        assertThat(projector.flushPending())
                .contains(new TimelineEvent.Thinking("reasoning", ThinkingSource.MODEL));
    }

    @Test
    void nullEventProjectsNothingTest() {
        assertThat(newProjector().project(null).isEmpty()).isTrue();
    }

    @Test
    void everyAgentEventSubtypeIsProjectedTest() {
        List<AgentEvent> samples = List.of(
                new AgentEvent.TextDelta("text"),
                new AgentEvent.ThinkingDelta("thinking", ThinkingSource.MODEL),
                new AgentEvent.ToolStart(TC_ID, TOOL),
                new AgentEvent.ToolInputComplete(TC_ID, TOOL, INPUT),
                new AgentEvent.ToolDone(TC_ID, TOOL, OUTPUT, true, 212L, null),
                new AgentEvent.ResultMeta("280b366b", 8123L, 6, 29376, "success"),
                new AgentEvent.InitMeta("280b366b", List.of("rocketmq-studio"), List.of()),
                new AgentEvent.ProviderNotice(AgentEventProjector.LEVEL_INFO, "resumed session"),
                new AgentEvent.UnhandledUpstream("ping"));

        assertThat(samples.stream().map(Object::getClass).collect(Collectors.toSet()))
                .as("this test must cover every permitted subtype of AgentEvent")
                .isEqualTo(Set.of(AgentEvent.class.getPermittedSubclasses()));
        for (AgentEvent sample : samples) {
            assertThat(newProjector().project(sample))
                    .as("projection of %s", sample.getClass().getSimpleName())
                    .isNotNull();
        }
    }

    @Test
    void projectionNeverHandsOutAMutablePersistedListTest() {
        Projection projection = newProjector().project(new AgentEvent.ProviderNotice(
                AgentEventProjector.LEVEL_INFO, "info"));

        assertThat(projection.persisted()).isUnmodifiable();
        assertThat(Projection.none().persisted()).isEmpty();
        assertThat(Projection.none().live()).isNull();
    }
}
