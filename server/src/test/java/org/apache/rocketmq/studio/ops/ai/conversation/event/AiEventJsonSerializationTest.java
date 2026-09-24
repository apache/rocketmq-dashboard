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

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Polymorphic JSON behaviour of {@link LiveEvent} and {@link TimelineEvent}: round-trips for every
 * subtype, the {@code @JsonInclude(NON_NULL)} promise the UI relies on, enum wire spellings, and
 * loud failure on an unknown or missing discriminator.
 *
 * <p>"Loud" matters more than it looks. A silently ignored {@code type} would turn an event the
 * server never persisted into an empty block, and a silently null event into a dropped frame; both
 * present as "the agent said nothing" rather than as the contract break they are.
 */
class AiEventJsonSerializationTest {

    private static final Map<String, Object> TOOL_INPUT = Map.of("instanceId", "open-source-local");
    private static final String TOOL_OUTPUT = "{\"items\":[{\"name\":\"StudioTest\"}]}";

    static Stream<Arguments> liveCases() {
        return crossed(List.of(
                new Sample("run_started", new LiveEvent.RunStarted(41L, 7L, "cluster status", 3)),
                new Sample("text_delta", new LiveEvent.TextDelta("the cluster has ")),
                new Sample("thinking", new LiveEvent.Thinking("check topic routes first",
                        ThinkingSource.MODEL)),
                new Sample("tool_start", new LiveEvent.ToolStart("toolu_01A", "rmq.topic.list", TOOL_INPUT)),
                new Sample("tool_done", new LiveEvent.ToolDone("toolu_01A", "rmq.topic.list", TOOL_OUTPUT,
                        1843, false, 212L, true, null)),
                new Sample("notice", new LiveEvent.Notice("warn", "rmqctl is unavailable")),
                new Sample("error", new LiveEvent.Error("llm.provider.timeout",
                        "claude CLI stream timed out after 300s", "Retry with a shorter prompt.")),
                new Sample("run_finished", new LiveEvent.RunFinished(41L, RunStatus.COMPLETED, 8123L))));
    }

    static Stream<Arguments> timelineCases() {
        return crossed(List.of(
                new Sample("user", new TimelineEvent.User("show cluster status",
                        "list the brokers and topics of this cluster")),
                new Sample("thinking", new TimelineEvent.Thinking("check topic routes first",
                        ThinkingSource.ENHANCE)),
                new Sample("text", new TimelineEvent.Text("the cluster has 2 brokers.")),
                new Sample("tool_use", new TimelineEvent.ToolUse("toolu_01A", "rmq.topic.list", TOOL_INPUT)),
                new Sample("tool_result", new TimelineEvent.ToolResult("toolu_01A", "rmq.topic.list",
                        TOOL_OUTPUT, 1843, false, true, 212L, null)),
                new Sample("notice", new TimelineEvent.Notice("info", "resumed the upstream session")),
                new Sample("error", new TimelineEvent.Error("llm.provider.error_max_turns",
                        "agent reached the maximum turn count", "Split the request.")),
                new Sample("run_status", new TimelineEvent.RunStatus(RunStatus.STOPPED,
                        StopReason.USER_STOP))));
    }

    /**
     * One case per subtype with every optional field left null.
     *
     * <p>The shared fixture {@code ai-event-contract.json} cannot cover this: it carries exactly one
     * example per type and each of them populates its optional fields, so on its own it pins only the
     * "a present field is written" half of {@code @JsonInclude(NON_NULL)}. These cases pin the other
     * half — an absent optional field must not appear in the JSON at all — which is what keeps a
     * per-token {@code text_delta} frame down to its two keys instead of carrying nulls over SSE.
     */
    static Stream<Arguments> minimalLiveCases() {
        return crossed(List.of(
                new Minimal("run_started", new LiveEvent.RunStarted(41L, 7L, null, null),
                        List.of("title", "turn"), List.of("runId", "conversationId")),
                new Minimal("text_delta", new LiveEvent.TextDelta("the cluster has "),
                        List.of(), List.of("content")),
                new Minimal("thinking", new LiveEvent.Thinking("check topic routes first", null),
                        List.of("source"), List.of("content")),
                // The projector never emits a null input (an argument-less call is {}), but NON_NULL is
                // a property of the type, not of one producer, so it is asserted here as well.
                new Minimal("tool_start", new LiveEvent.ToolStart("toolu_01A", "rmq.topic.list", null),
                        List.of("input"), List.of("tcId", "tool")),
                new Minimal("tool_done", new LiveEvent.ToolDone("toolu_01A", "rmq.topic.list", null, null,
                        false, null, true, null),
                        List.of("output", "outputBytes", "durationMs", "error"),
                        List.of("tcId", "tool", "truncated", "success")),
                new Minimal("notice", new LiveEvent.Notice(AgentEventProjector.LEVEL_ERROR,
                        "upstream reported api_error_status 529"),
                        List.of(), List.of("level", "message")),
                new Minimal("error", new LiveEvent.Error("llm.provider.timeout", "timed out", null),
                        List.of("hint"), List.of("code", "message")),
                new Minimal("run_finished", new LiveEvent.RunFinished(41L, RunStatus.COMPLETED, null),
                        List.of("durationMs"), List.of("runId", "status"))));
    }

    /** The persisted counterpart of {@link #minimalLiveCases()}: one case per subtype. */
    static Stream<Arguments> minimalTimelineCases() {
        return crossed(List.of(
                new Minimal("user", new TimelineEvent.User("show cluster status", null),
                        List.of("enhancedPrompt"), List.of("text")),
                new Minimal("thinking", new TimelineEvent.Thinking("check topic routes first", null),
                        List.of("source"), List.of("text")),
                new Minimal("text", new TimelineEvent.Text("the cluster has 2 brokers."),
                        List.of(), List.of("text")),
                new Minimal("tool_use", new TimelineEvent.ToolUse("toolu_01A", "rmq.topic.list", null),
                        List.of("input"), List.of("tcId", "tool")),
                new Minimal("tool_result", new TimelineEvent.ToolResult("toolu_01A", "rmq.topic.list",
                        null, null, false, false, null, null),
                        List.of("output", "outputBytes", "durationMs", "error"),
                        List.of("tcId", "tool", "truncated", "success")),
                new Minimal("notice", new TimelineEvent.Notice(AgentEventProjector.LEVEL_INFO,
                        "resumed the upstream session"),
                        List.of(), List.of("level", "message")),
                new Minimal("error", new TimelineEvent.Error("llm.provider.error_max_turns",
                        "agent reached the maximum turn count", null),
                        List.of("hint"), List.of("code", "message")),
                // reason explains a non-success terminal state only; COMPLETED writes status alone.
                new Minimal("run_status", new TimelineEvent.RunStatus(RunStatus.COMPLETED, null),
                        List.of("reason"), List.of("status"))));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("liveCases")
    void liveEventRoundTripsTest(String label, AiEventCodecs.Codec codec, Sample sample) {
        String json = codec.write(sample.event());

        LiveEvent read = codec.read(json, LiveEvent.class);

        assertThat(read).isEqualTo(sample.event()).isInstanceOf(sample.event().getClass());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timelineCases")
    void timelineEventRoundTripsTest(String label, AiEventCodecs.Codec codec, Sample sample) {
        String json = codec.write(sample.event());

        TimelineEvent read = codec.read(json, TimelineEvent.class);

        assertThat(read).isEqualTo(sample.event()).isInstanceOf(sample.event().getClass());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("liveCases")
    void liveEventCarriesItsWireNameAsDiscriminatorTest(String label, AiEventCodecs.Codec codec,
                                                       Sample sample) {
        JsonNode json = AiEventCodecs.TREES.readTree(codec.write(sample.event()));

        assertThat(json.path("type").asString())
                .as("%s must be discriminated by its @JsonTypeName", sample.label())
                .isEqualTo(wireNameOf(sample.event().getClass()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("timelineCases")
    void timelineEventCarriesItsWireNameAsDiscriminatorTest(String label, AiEventCodecs.Codec codec,
                                                           Sample sample) {
        JsonNode json = AiEventCodecs.TREES.readTree(codec.write(sample.event()));

        assertThat(json.path("type").asString())
                .as("%s must be discriminated by its @JsonTypeName", sample.label())
                .isEqualTo(wireNameOf(sample.event().getClass()));
    }

    @Test
    void everySubtypeHasASerializationSampleTest() {
        assertThat(caseTypes(liveCases()))
                .as("every LiveEvent subtype needs a round-trip sample")
                .isEqualTo(Set.of(LiveEvent.class.getPermittedSubclasses()));
        assertThat(caseTypes(timelineCases()))
                .as("every TimelineEvent subtype needs a round-trip sample")
                .isEqualTo(Set.of(TimelineEvent.class.getPermittedSubclasses()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("minimalLiveCases")
    void liveEventWithoutItsOptionalFieldsIsStillCompleteTest(String label, AiEventCodecs.Codec codec,
                                                             Minimal minimal) {
        assertMinimal(codec, minimal, LiveEvent.class);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("minimalTimelineCases")
    void timelineEventWithoutItsOptionalFieldsIsStillCompleteTest(String label, AiEventCodecs.Codec codec,
                                                                 Minimal minimal) {
        assertMinimal(codec, minimal, TimelineEvent.class);
    }

    /**
     * Closure of the two cases above: without it, dropping a subtype from the minimal list would
     * silently narrow the coverage instead of failing.
     */
    @Test
    void everySubtypeHasAMinimalSampleTest() {
        assertThat(caseTypes(minimalLiveCases()))
                .as("every LiveEvent subtype needs a case with its optional fields absent")
                .isEqualTo(Set.of(LiveEvent.class.getPermittedSubclasses()));
        assertThat(caseTypes(minimalTimelineCases()))
                .as("every TimelineEvent subtype needs a case with its optional fields absent")
                .isEqualTo(Set.of(TimelineEvent.class.getPermittedSubclasses()));
    }

    /**
     * All three notice levels reach the wire, including {@code error}, which the shared fixture never
     * exercises: its single {@code notice} example is a {@code warn} on the live side and an
     * {@code info} on the persisted side.
     *
     * <p>{@code error} is a notice level rather than an {@link LiveEvent.Error} because the run survives
     * it — {@code ClaudeCodeStreamParser} emits one for a {@code result} frame carrying {@code errors}
     * and for a non-null {@code api_error_status}, and {@code AgentEventProjector} passes the level
     * through untouched. The three spellings are the closed vocabulary
     * {@code NoticeLevel} in {@code web/src/api/aiEvents.ts} mirrors.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void noticeCarriesEveryLevelIncludingErrorTest(String label, AiEventCodecs.Codec codec) {
        assertThat(List.of(AgentEventProjector.LEVEL_INFO, AgentEventProjector.LEVEL_WARN,
                AgentEventProjector.LEVEL_ERROR))
                .as("%s must keep the three notice levels the client union type mirrors", label)
                .containsExactly("info", "warn", "error");

        for (String level : List.of(AgentEventProjector.LEVEL_INFO, AgentEventProjector.LEVEL_WARN,
                AgentEventProjector.LEVEL_ERROR)) {
            LiveEvent.Notice live = new LiveEvent.Notice(level, "provider notice at " + level);
            TimelineEvent.Notice persisted = new TimelineEvent.Notice(level, "provider notice at " + level);

            assertThat(AiEventCodecs.TREES.readTree(codec.write(live)).path("level").asString())
                    .as("%s must write the %s level of a live notice", label, level)
                    .isEqualTo(level);
            assertThat(AiEventCodecs.TREES.readTree(codec.write(persisted)).path("level").asString())
                    .as("%s must write the %s level of a persisted notice", label, level)
                    .isEqualTo(level);
            assertThat(codec.read(codec.write(live), LiveEvent.class)).isEqualTo(live);
            assertThat(codec.read(codec.write(persisted), TimelineEvent.class)).isEqualTo(persisted);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void booleanFlagsAreAlwaysWrittenTest(String label, AiEventCodecs.Codec codec) {
        JsonNode json = AiEventCodecs.TREES.readTree(codec.write(
                new LiveEvent.ToolDone("toolu_01A", "rmq.topic.list", TOOL_OUTPUT, 41, false, 212L,
                        false, null)));

        // NON_NULL cannot hide a primitive, and that is wanted: the UI must be able to tell a
        // successful call from one whose result never arrived.
        assertThat(json.has("truncated")).isTrue();
        assertThat(json.path("truncated").booleanValue()).isFalse();
        assertThat(json.has("success")).isTrue();
        assertThat(json.path("success").booleanValue()).isFalse();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void unknownTypeFailsLoudlyTest(String label, AiEventCodecs.Codec codec) {
        Throwable live = catchThrowable(() -> codec.read("{\"type\":\"bogus_event\"}", LiveEvent.class));
        Throwable timeline = catchThrowable(() -> codec.read("{\"type\":\"bogus_event\"}", TimelineEvent.class));

        assertThat(live).as("%s must reject an unknown live type", label).isNotNull();
        assertThat(unwrap(live).getClass().getSimpleName()).isEqualTo("InvalidTypeIdException");
        assertThat(unwrap(live)).hasMessageContaining("bogus_event");
        assertThat(timeline).as("%s must reject an unknown timeline type", label).isNotNull();
        assertThat(unwrap(timeline).getClass().getSimpleName()).isEqualTo("InvalidTypeIdException");
        assertThat(unwrap(timeline)).hasMessageContaining("bogus_event");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void missingTypeFailsLoudlyTest(String label, AiEventCodecs.Codec codec) {
        Throwable live = catchThrowable(() -> codec.read("{\"content\":\"no discriminator\"}", LiveEvent.class));
        Throwable timeline = catchThrowable(() -> codec.read("{\"text\":\"no discriminator\"}",
                TimelineEvent.class));

        assertThat(live).as("%s must reject a live event without a type", label).isNotNull();
        assertThat(unwrap(live)).hasMessageContaining("type id");
        assertThat(timeline).as("%s must reject a timeline event without a type", label).isNotNull();
        assertThat(unwrap(timeline)).hasMessageContaining("type id");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void thinkingSourceUsesLowercaseWireNamesTest(String label, AiEventCodecs.Codec codec) {
        JsonNode model = AiEventCodecs.TREES.readTree(codec.write(
                new LiveEvent.Thinking("reasoning", ThinkingSource.MODEL)));
        JsonNode enhance = AiEventCodecs.TREES.readTree(codec.write(
                new TimelineEvent.Thinking("rewritten prompt", ThinkingSource.ENHANCE)));

        assertThat(model.path("source").asString()).isEqualTo("model");
        assertThat(enhance.path("source").asString()).isEqualTo("enhance");
        assertThat(codec.read(model.toString(), LiveEvent.class))
                .isEqualTo(new LiveEvent.Thinking("reasoning", ThinkingSource.MODEL));
        assertThat(codec.read(enhance.toString(), TimelineEvent.class))
                .isEqualTo(new TimelineEvent.Thinking("rewritten prompt", ThinkingSource.ENHANCE));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void unknownThinkingSourceFailsLoudlyTest(String label, AiEventCodecs.Codec codec) {
        Throwable thrown = catchThrowable(() -> codec.read(
                "{\"type\":\"thinking\",\"content\":\"x\",\"source\":\"reasoning\"}", LiveEvent.class));

        assertThat(thrown).as("%s must reject a source it does not know", label).isNotNull();
        assertThat(unwrap(thrown)).hasMessageContaining("reasoning");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void runStatusAndStopReasonUseEnumNamesTest(String label, AiEventCodecs.Codec codec) {
        JsonNode finished = AiEventCodecs.TREES.readTree(codec.write(
                new LiveEvent.RunFinished(41L, RunStatus.STOPPED, 1200L)));
        JsonNode status = AiEventCodecs.TREES.readTree(codec.write(
                new TimelineEvent.RunStatus(RunStatus.FAILED, StopReason.SERVER_RESTART)));

        assertThat(finished.path("status").asString()).isEqualTo("STOPPED");
        assertThat(status.path("status").asString()).isEqualTo("FAILED");
        assertThat(status.path("reason").asString()).isEqualTo("SERVER_RESTART");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void structuredToolInputSurvivesTheRoundTripTest(String label, AiEventCodecs.Codec codec) {
        Map<String, Object> input = Map.of("instanceId", "open-source-local", "topicName", "StudioTest");
        LiveEvent.ToolStart live = new LiveEvent.ToolStart("toolu_01A", "rmq.topic.list", input);
        TimelineEvent.ToolUse persisted = new TimelineEvent.ToolUse("toolu_01A", "rmq.topic.list", input);

        assertThat(codec.read(codec.write(live), LiveEvent.class)).isEqualTo(live);
        assertThat(codec.read(codec.write(persisted), TimelineEvent.class)).isEqualTo(persisted);
        assertThat(AiEventCodecs.TREES.readTree(codec.write(live)).path("input").path("topicName").asString())
                .isEqualTo("StudioTest");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("codecs")
    void noArgumentToolCallStillCarriesAnInputObjectTest(String label, AiEventCodecs.Codec codec) {
        // AgentEventProjector guarantees a tool input is never null; this pins the serialisation
        // half of that promise. NON_NULL drops a null field, and the client types tool_start.input
        // as a required object, so an argument-less call has to arrive as {} and not as nothing.
        JsonNode live = AiEventCodecs.TREES.readTree(codec.write(
                new LiveEvent.ToolStart("toolu_01A", "rmq.instance.capabilities", Map.of())));
        JsonNode persisted = AiEventCodecs.TREES.readTree(codec.write(
                new TimelineEvent.ToolUse("toolu_01A", "rmq.instance.capabilities", Map.of())));

        assertThat(live.path("input").isObject())
                .as("%s must write an empty input object for tool_start", label)
                .isTrue();
        assertThat(live.path("input").toString()).isEqualTo("{}");
        assertThat(persisted.path("input").isObject())
                .as("%s must write an empty input object for tool_use", label)
                .isTrue();
        assertThat(persisted.path("input").toString()).isEqualTo("{}");
    }

    static Stream<Arguments> codecs() {
        return AiEventCodecs.asArguments();
    }

    private static Stream<Arguments> crossed(List<? extends Case> cases) {
        List<Arguments> arguments = new ArrayList<>();
        for (Case sample : cases) {
            for (AiEventCodecs.Codec codec : AiEventCodecs.all()) {
                arguments.add(Arguments.of(codec.label() + " " + sample.label(), codec, sample));
            }
        }
        return arguments.stream();
    }

    private static Set<Class<?>> caseTypes(Stream<Arguments> cases) {
        return cases.map(Arguments::get)
                .map(args -> (Case) args[2])
                .map(sample -> sample.event().getClass())
                .collect(Collectors.toSet());
    }

    private static String wireNameOf(Class<?> subtype) {
        JsonTypeName typeName = subtype.getAnnotation(JsonTypeName.class);
        assertThat(typeName).as("%s must declare @JsonTypeName", subtype.getSimpleName()).isNotNull();
        return typeName.value();
    }

    /**
     * The three assertions a minimal case is worth: the optional keys are absent, the keys the client
     * types as required are still written (NON_NULL must not be widened into NON_DEFAULT, which would
     * drop a legitimate {@code false} or {@code 0}), and the JSON reads back to an equal instance, i.e.
     * an absent optional field deserialises as null rather than as a failure or an invented default.
     */
    private static <T> void assertMinimal(AiEventCodecs.Codec codec, Minimal minimal, Class<T> baseType) {
        String json = codec.write(minimal.event());
        JsonNode node = AiEventCodecs.TREES.readTree(json);

        assertThat(node.path("type").asString()).isEqualTo(wireNameOf(minimal.event().getClass()));
        for (String key : minimal.optional()) {
            assertThat(node.has(key))
                    .as("null %s must not be written for %s: %s", key, minimal.label(), json)
                    .isFalse();
        }
        for (String key : minimal.required()) {
            assertThat(node.has(key))
                    .as("%s must always be written for %s: %s", key, minimal.label(), json)
                    .isTrue();
        }
        assertThat(codec.read(json, baseType))
                .as("%s must read a minimal %s back unchanged", codec.label(), minimal.label())
                .isEqualTo(minimal.event());
    }

    /** Unwraps the codec's checked-exception wrapper so both mappers can be asserted alike. */
    private static Throwable unwrap(Throwable thrown) {
        Throwable current = thrown;
        while (current instanceof IllegalStateException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    /** One labelled instance of a subtype, so a case list can be crossed with both codecs. */
    private interface Case {

        String label();

        Object event();
    }

    /** One subtype instance plus the wire name it is expected to carry. */
    private record Sample(String label, Object event) implements Case {
    }

    /**
     * One subtype instance with every optional field null, plus the keys that must consequently be
     * absent from the JSON and the keys that must survive anyway.
     */
    private record Minimal(String label, Object event, List<String> optional,
                           List<String> required) implements Case {
    }
}
