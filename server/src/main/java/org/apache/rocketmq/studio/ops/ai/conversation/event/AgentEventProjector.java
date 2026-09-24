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

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.LongSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Translates the provider-neutral {@link AgentEvent} stream into the two wire vocabularies: the
 * ephemeral {@link LiveEvent} pushed over SSE and the coalesced {@link TimelineEvent} written to
 * {@code rmq_ai_event}. One upstream event may yield zero, one or two outputs, and the two sides
 * are deliberately not the same.
 *
 * <h2>Name shifts between the two sides</h2>
 * <ul>
 *   <li>{@code text_delta} (live) becomes {@code text} (persisted) — many deltas coalesce into one
 *       block, see {@link #flushPending()}.</li>
 *   <li>{@code tool_start} (live) becomes {@code tool_use} (persisted).</li>
 *   <li>{@code tool_done} (live) becomes {@code tool_result} (persisted).</li>
 *   <li>{@code thinking} keeps its name but renames {@code content} to {@code text}, because the
 *       persisted side is a finished block rather than a fragment.</li>
 *   <li>{@code run_started} is live-only: the run row is the truth about a run starting.</li>
 *   <li>{@code run_finished} is live-only, but a terminal {@code run_status} IS persisted so a
 *       reloaded conversation can show that it stopped without joining {@code rmq_ai_run}.</li>
 * </ul>
 *
 * <h2>Why this is a per-run object and not a static utility</h2>
 * It keeps two pieces of per-run state that a stateless mapping cannot recover:
 * <ol>
 *   <li>{@code tcId -> start timestamp}, so a {@link AgentEvent.ToolDone} — for which the provider
 *       reports no duration — can be given one.</li>
 *   <li>{@code tcId -> tool}, because the provider's result frame carries only the call id, and the
 *       UI needs the tool name on the result card.</li>
 * </ol>
 * Instances are not thread-safe; a run is streamed by exactly one thread.
 *
 * <h2>Tool start timing</h2>
 * {@link AgentEvent.ToolStart} emits nothing. It only records state, because at that moment the
 * arguments are still arriving as {@code input_json_delta} fragments and a card without input is
 * not the card the timeline will later show. {@link AgentEvent.ToolInputComplete} emits both sides
 * at once — live {@code tool_start} and persisted {@code tool_use}, with identical input — so a
 * live tool block and a replayed one are indistinguishable.
 *
 * <p>That input is always an object, see {@code inputOrEmpty}. The web contract types it as a
 * non-optional {@code Record<string, unknown>}, and {@code @JsonInclude(NON_NULL)} would drop a null
 * input from the frame altogether, so a no-argument tool call projects to {@code {}} rather than to
 * a field the client is told to expect but never receives.
 *
 * <h2>Tool output sanitising</h2>
 * Applied identically to both sides, in this order: strip inline base64, then measure, then cap at
 * {@link #MAX_TOOL_OUTPUT_BYTES}. {@code outputBytes} is therefore the true size of the sanitised
 * output and {@code truncated} says whether {@code output} was cut, on the live side as well as on
 * the persisted one. Stripping live too is a deliberate choice: the persisted side must strip (a
 * megabyte of base64 has no business in a database row), and if the live side kept it the same tool
 * call would render differently depending on whether the user watched it happen or reloaded the
 * page. Base64 is stripped from the middle of the text and replaced with a short marker, so the
 * surrounding JSON stays readable.
 *
 * <h2>The coalescing seam</h2>
 * Persisted {@code text} and {@code thinking} blocks are coalesced, not written per delta: a run
 * that streams 80 text deltas and 67 thinking deltas must not become 147 rows. Both live in one
 * kind-discriminated buffer, which is what keeps the timeline in arrival order — a text block can
 * never be written after the reasoning or the tool call that followed it. A buffer is cut when its
 * kind changes, when the thinking {@link ThinkingSource} changes (model reasoning and a prompt
 * rewrite are never one block), when it reaches {@link #COALESCE_MAX_CHARS}, or when any other
 * persisted event is about to be written.
 *
 * <p>Whatever is left at the end is the buffered writer's to collect: it calls {@link #flushPending()}
 * on its own time boundary and once more when the run ends, and persists what comes back. The
 * character ceiling is deliberately the same number the web client coalesces with
 * ({@code DEFAULT_COALESCE_MAX_CHARS} in {@code web/src/pages/ai/render/foldTimeline.ts}); the time
 * boundary is the writer's alone, because a pure function on the client cannot model it.
 */
@Slf4j
public final class AgentEventProjector {

    /** Ceiling for a tool output on both sides. Beyond this the output is cut and marked truncated. */
    public static final int MAX_TOOL_OUTPUT_BYTES = 32 * 1024;

    /**
     * Character ceiling for one coalesced {@code text} or {@code thinking} block. Change this and
     * you must change {@code DEFAULT_COALESCE_MAX_CHARS} in
     * {@code web/src/pages/ai/render/foldTimeline.ts} with it: the client models the server's
     * buffered write to prove live and replay render alike, and the two numbers are the same rule.
     */
    public static final int COALESCE_MAX_CHARS = 2048;

    /** The name our in-process MCP server is registered under in the agent CLI configuration. */
    public static final String STUDIO_MCP_SERVER = "rocketmq-studio";

    /** Notice levels understood by the UI. Shared so no caller invents its own spelling. */
    public static final String LEVEL_INFO = "info";
    public static final String LEVEL_WARN = "warn";
    public static final String LEVEL_ERROR = "error";

    /** Prefix of the machine-readable error code built from a provider result subtype. */
    static final String PROVIDER_ERROR_CODE_PREFIX = "llm.provider.";

    /**
     * The {@code result} frame subtype that means "the agent finished successfully". This is the only
     * case in which the projector emits a terminal — a live {@code run_finished} plus a persisted
     * {@code run_status}, both {@link RunStatus#COMPLETED}. Every other subtype yields an
     * {@code error} and nothing terminal, and a provider that throws yields no projection at all, so
     * the executor owns the terminal state for STOPPED and FAILED and must consult this constant to
     * know whether one was already written.
     */
    public static final String SUCCESS_SUBTYPE = "success";

    private static final String ERROR_MAX_TURNS_SUBTYPE = "error_max_turns";

    /**
     * An inline data URL. The alphabet is intentionally restricted to standard base64 so the match
     * cannot run past the blob into the prose that follows it.
     */
    private static final Pattern INLINE_BASE64 =
            Pattern.compile("data:([^;,\\s]+)?;base64,[A-Za-z0-9+/=]+");

    private final Long runId;
    private final LongSupplier nanoTime;

    /**
     * The duration the live {@code run_finished} carried, replayed on the terminal frame. The client
     * contract pins {@code durationMs} as a required run_finished field, so a replayed terminal must
     * carry it too; the value is only knowable by the caller at replay time, hence the holder.
     */
    private Long replayedDurationMs;

    /** tcId -> remembered call. Insertion ordered so a dump reads like the run did. */
    private final Map<String, ToolCall> toolCalls = new LinkedHashMap<>();

    /**
     * Deltas accumulated since the last flush, and what they are. One buffer for both kinds is what
     * makes the persisted order match the arrival order.
     */
    private final StringBuilder pending = new StringBuilder();
    private PendingKind pendingKind;
    private ThinkingSource pendingSource;

    /** @param runId stamped onto the live {@code run_finished}; the run row stays the authority. */
    public AgentEventProjector(Long runId) {
        this(runId, System::nanoTime);
    }

    /** Visible for testing: a controllable clock makes tool durations assertable. */
    AgentEventProjector(Long runId, LongSupplier nanoTime) {
        this.runId = runId;
        this.nanoTime = nanoTime;
    }

    /**
     * Sets the duration replayed on the terminal {@code run_finished} frame. Called by the attach
     * path with the run row's {@code durationMs} before the persisted rows are replayed.
     */
    public void setReplayedDurationMs(Long replayedDurationMs) {
        this.replayedDurationMs = replayedDurationMs;
    }

    /**
     * Projects one provider event onto both wire vocabularies.
     *
     * <p>The switch is exhaustive over the sealed {@link AgentEvent} with no default branch, so
     * adding a subtype without deciding what it projects to is a compile error rather than a
     * silently dropped event.
     */
    public Projection project(AgentEvent event) {
        if (event == null) {
            return Projection.none();
        }
        return switch (event) {
            case AgentEvent.TextDelta delta -> {
                if (!StringUtils.hasLength(delta.content())) {
                    yield Projection.none();
                }
                yield Projection.of(
                        new LiveEvent.TextDelta(delta.content()),
                        buffer(PendingKind.TEXT, null, delta.content()));
            }
            case AgentEvent.ThinkingDelta delta -> {
                if (!StringUtils.hasLength(delta.content())) {
                    yield Projection.none();
                }
                ThinkingSource source = delta.source() == null ? ThinkingSource.MODEL : delta.source();
                yield Projection.of(
                        new LiveEvent.Thinking(delta.content(), source),
                        buffer(PendingKind.THINKING, source, delta.content()));
            }
            case AgentEvent.ToolStart start -> {
                remember(start.tcId(), start.tool(), true);
                yield Projection.none();
            }
            case AgentEvent.ToolInputComplete complete -> {
                String tool = remember(complete.tcId(), complete.tool(), false);
                Object input = inputOrEmpty(complete.input());
                yield Projection.of(
                        new LiveEvent.ToolStart(complete.tcId(), tool, input),
                        drainThen(new TimelineEvent.ToolUse(complete.tcId(), tool, input)));
            }
            case AgentEvent.ToolDone done -> projectToolDone(done);
            case AgentEvent.ResultMeta meta -> projectResultMeta(meta);
            case AgentEvent.InitMeta init -> {
                if (init.connectedMcpServers() != null && !init.connectedMcpServers().contains(STUDIO_MCP_SERVER)) {
                    log.warn("agent run {} started without the {} MCP server; RocketMQ tools are unavailable",
                            runId, STUDIO_MCP_SERVER);
                    yield Projection.live(new LiveEvent.Notice(LEVEL_WARN,
                            "MCP server " + STUDIO_MCP_SERVER + " did not connect"));
                }
                yield Projection.none();
            }
            case AgentEvent.ProviderNotice notice -> {
                if (!StringUtils.hasText(notice.message())) {
                    yield Projection.none();
                }
                yield Projection.of(
                        new LiveEvent.Notice(notice.level(), notice.message()),
                        drainThen(new TimelineEvent.Notice(notice.level(), notice.message())));
            }
            case AgentEvent.UnhandledUpstream unhandled -> {
                // Deduplicating this per upstream type is the caller's job: the projector has no
                // opinion about how chatty the parser is.
                String message = "unhandled upstream event type: " + unhandled.upstreamType();
                log.debug("agent run {} got an unmodelled upstream frame: {}", runId, unhandled.upstreamType());
                yield Projection.of(
                        new LiveEvent.Notice(LEVEL_WARN, message),
                        drainThen(new TimelineEvent.Notice(LEVEL_WARN, message)));
            }
        };
    }

    /**
     * Translates a persisted event back into its live frame — the inverse direction, used when an
     * observer attaches to a run that is already in progress and has to be handed the database rows
     * before it starts tailing live ones. Same wire vocabulary on both sides, so the client cannot tell
     * a replayed block from a streamed one; that is the property
     * {@code web/src/pages/ai/render/equivalence.test.ts} states.
     *
     * <p>The mapping is lossy in the only direction that is safe: a whole coalesced {@code text} block
     * becomes one {@code text_delta}, which the client appends exactly as it would append the many
     * deltas it would have received live. {@link TimelineEvent.User} has no live counterpart at all —
     * the live vocabulary starts at {@code run_started} and the user's own turn is rendered from what
     * the client just sent — so it replays as nothing.
     *
     * <p>Exhaustive over the sealed hierarchy with no default branch, like {@link #project}: a new
     * persisted event type that nobody thought about is a compile error here rather than a silently
     * missing block in every replayed conversation.
     */
    public Optional<LiveEvent> replay(TimelineEvent event) {
        if (event == null) {
            return Optional.empty();
        }
        LiveEvent live = switch (event) {
            case TimelineEvent.User user -> null;
            case TimelineEvent.Thinking thinking ->
                    new LiveEvent.Thinking(thinking.text(), thinking.source());
            case TimelineEvent.Text text -> new LiveEvent.TextDelta(text.text());
            case TimelineEvent.ToolUse use ->
                    new LiveEvent.ToolStart(use.tcId(), use.tool(), inputOrEmpty(use.input()));
            case TimelineEvent.ToolResult result -> new LiveEvent.ToolDone(result.tcId(), result.tool(),
                    result.output(), result.outputBytes(), result.truncated(), result.durationMs(),
                    result.success(), result.error());
            case TimelineEvent.Notice notice -> new LiveEvent.Notice(notice.level(), notice.message());
            case TimelineEvent.Error error -> new LiveEvent.Error(error.code(), error.message(), error.hint());
            case TimelineEvent.RunStatus terminal ->
                    new LiveEvent.RunFinished(runId, terminal.status(), replayedDurationMs);
        };
        return Optional.ofNullable(live);
    }

    /**
     * The buffered writer's coalescing seam: hands over what the delta buffer holds as one
     * persisted block — {@link TimelineEvent.Text} or {@link TimelineEvent.Thinking} — or nothing
     * when the buffer is empty. The projector cuts the buffer itself on a kind change, a thinking
     * source change and {@link #COALESCE_MAX_CHARS}; the writer owns the time boundary and the end
     * of the run, and must call this at both.
     */
    public Optional<TimelineEvent> flushPending() {
        return Optional.ofNullable(drainPending());
    }

    /** True when a block is waiting to be persisted. Lets the writer ask without consuming. */
    public boolean hasPending() {
        return !pending.isEmpty();
    }

    private Projection projectToolDone(AgentEvent.ToolDone done) {
        ToolCall call = done.tcId() == null ? null : toolCalls.remove(done.tcId());
        String tool = labelFor(done.tool(), call);
        Long durationMs = durationFor(done.durationMs(), call);
        if (call == null && done.durationMs() == null) {
            log.debug("tool result {} arrived without a remembered start; no duration to report", done.tcId());
        }
        SanitisedOutput output = sanitise(done.output());
        return Projection.of(
                new LiveEvent.ToolDone(done.tcId(), tool, output.output(), output.outputBytes(),
                        output.truncated(), durationMs, done.success(), done.error()),
                drainThen(new TimelineEvent.ToolResult(done.tcId(), tool, output.output(),
                        output.outputBytes(), output.truncated(), done.success(), durationMs,
                        done.error())));
    }

    /** The provider's tool name wins; otherwise fall back to the one remembered at call start. */
    private static String labelFor(String reported, ToolCall call) {
        if (StringUtils.hasText(reported)) {
            return reported;
        }
        return call == null ? null : call.tool();
    }

    /** The provider rarely reports a duration, so it is measured from the remembered start. */
    private Long durationFor(Long reported, ToolCall call) {
        if (reported != null) {
            return reported;
        }
        return call == null ? null : elapsedMillis(call.startedAtNanos());
    }

    /**
     * A tool call always carries an input object. A tool that takes no arguments assembles to
     * {@code {}}, and a provider that hands us nothing at all must not become a null field: the
     * client's contract has {@code input} as required, so omitting it would make the type lie.
     */
    private static Object inputOrEmpty(Object input) {
        return input == null ? Map.of() : input;
    }

    private Projection projectResultMeta(AgentEvent.ResultMeta meta) {
        if (SUCCESS_SUBTYPE.equals(meta.subtype())) {
            return Projection.of(
                    new LiveEvent.RunFinished(runId, RunStatus.COMPLETED, meta.durationMs()),
                    drainThen(new TimelineEvent.RunStatus(RunStatus.COMPLETED, null)));
        }
        String subtype = StringUtils.hasText(meta.subtype()) ? meta.subtype() : "unknown";
        String code = PROVIDER_ERROR_CODE_PREFIX + subtype;
        String message = ERROR_MAX_TURNS_SUBTYPE.equals(subtype)
                ? "agent reached the maximum turn count"
                : "agent run ended with subtype: " + subtype;
        String hint = ERROR_MAX_TURNS_SUBTYPE.equals(subtype) ? "Split the request." : null;
        log.warn("agent run {} reported provider subtype {}", runId, subtype);
        return Projection.of(
                new LiveEvent.Error(code, message, hint),
                drainThen(new TimelineEvent.Error(code, message, hint)));
    }

    /**
     * Records a tool call and returns the tool name to label it with.
     *
     * @param restart whether this is the initial announcement, which (re)starts the duration clock
     */
    private String remember(String tcId, String tool, boolean restart) {
        ToolCall existing = tcId == null ? null : toolCalls.get(tcId);
        String resolved = labelFor(tool, existing);
        if (tcId != null) {
            long startedAtNanos;
            if (restart || existing == null) {
                startedAtNanos = nanoTime.getAsLong();
            } else {
                startedAtNanos = existing.startedAtNanos();
            }
            toolCalls.put(tcId, new ToolCall(resolved, startedAtNanos));
        }
        return resolved;
    }

    private long elapsedMillis(long startedAtNanos) {
        return Math.max(0L, (nanoTime.getAsLong() - startedAtNanos) / 1_000_000L);
    }

    /**
     * Appends a delta to the coalescing buffer and returns the blocks it had to cut, in order: at
     * most one because the buffer changed kind or thinking source, plus at most one because the
     * character ceiling was reached. Mirrors {@code coalesceLiveToTimeline} in
     * {@code web/src/pages/ai/render/foldTimeline.ts} — same rules, same ceiling.
     */
    private List<TimelineEvent> buffer(PendingKind kind, ThinkingSource source, String content) {
        List<TimelineEvent> flushed = new ArrayList<>(2);
        if (!pending.isEmpty() && cutsBefore(kind, source)) {
            flushed.add(drainPending());
        }
        pending.append(content);
        pendingKind = kind;
        pendingSource = source;
        if (pending.length() >= COALESCE_MAX_CHARS) {
            flushed.add(drainPending());
        }
        return List.copyOf(flushed);
    }

    /** Reasoning from the model and reasoning from the prompt rewrite are never one block. */
    private boolean cutsBefore(PendingKind kind, ThinkingSource source) {
        if (pendingKind != kind) {
            return true;
        }
        return kind == PendingKind.THINKING && pendingSource != source;
    }

    /** Drains the buffer ahead of another persisted event so timeline order matches arrival order. */
    private List<TimelineEvent> drainThen(TimelineEvent event) {
        TimelineEvent flushed = drainPending();
        if (flushed == null) {
            return List.of(event);
        }
        return List.of(flushed, event);
    }

    private TimelineEvent drainPending() {
        if (pending.isEmpty()) {
            return null;
        }
        String text = pending.toString();
        PendingKind kind = pendingKind;
        ThinkingSource source = pendingSource;
        pending.setLength(0);
        pendingKind = null;
        pendingSource = null;
        if (kind == PendingKind.THINKING) {
            return new TimelineEvent.Thinking(text, source);
        }
        return new TimelineEvent.Text(text);
    }

    /** Strips inline base64, then measures the true size and caps it. Same result for both sides. */
    static SanitisedOutput sanitise(String rawOutput) {
        String stripped = stripInlineBase64(rawOutput);
        if (stripped == null) {
            return new SanitisedOutput(null, null, false);
        }
        int outputBytes = stripped.getBytes(StandardCharsets.UTF_8).length;
        if (outputBytes <= MAX_TOOL_OUTPUT_BYTES) {
            return new SanitisedOutput(stripped, outputBytes, false);
        }
        return new SanitisedOutput(truncateOnCharacterBoundary(stripped, MAX_TOOL_OUTPUT_BYTES),
                outputBytes, true);
    }

    static String stripInlineBase64(String output) {
        if (output == null || !output.contains(";base64,")) {
            return output;
        }
        Matcher matcher = INLINE_BASE64.matcher(output);
        StringBuilder sanitised = new StringBuilder(output.length());
        while (matcher.find()) {
            String mime = matcher.group(1);
            matcher.appendReplacement(sanitised, Matcher.quoteReplacement(
                    "[base64 " + (mime == null ? "data" : mime) + " omitted]"));
        }
        matcher.appendTail(sanitised);
        return sanitised.toString();
    }

    /**
     * Cuts to at most {@code maxBytes} UTF-8 bytes without splitting a character: walking back over
     * continuation bytes lands on the lead byte of the first character that no longer fits.
     */
    static String truncateOnCharacterBoundary(String value, int maxBytes) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return value;
        }
        int end = maxBytes;
        while (end > 0 && (bytes[end] & 0xC0) == 0x80) {
            end--;
        }
        return new String(bytes, 0, end, StandardCharsets.UTF_8);
    }

    /** A remembered tool call: the display name and when it started. */
    private record ToolCall(String tool, long startedAtNanos) {
    }

    /** What the coalescing buffer currently holds. Mirrors the client's PendingBuffer union. */
    private enum PendingKind {
        TEXT, THINKING
    }

    /** Tool output after sanitising: what to show, how big it really was, whether it was cut. */
    record SanitisedOutput(String output, Integer outputBytes, boolean truncated) {
    }

    /**
     * What one {@link AgentEvent} projects to. Either side may be absent, and neither implies the
     * other: {@link AgentEvent.ToolStart} yields nothing at all, a text delta yields a live frame
     * and — only when a coalescing boundary was hit — persisted blocks, and
     * {@link AgentEventProjector#flushPending()} hands back a persisted block with no live frame.
     */
    public record Projection(LiveEvent live, List<TimelineEvent> persisted) {

        public Projection {
            persisted = persisted == null ? List.of() : List.copyOf(persisted);
        }

        public static Projection none() {
            return new Projection(null, List.of());
        }

        public static Projection live(LiveEvent live) {
            return new Projection(live, List.of());
        }

        public static Projection of(LiveEvent live, List<TimelineEvent> persisted) {
            return new Projection(live, persisted);
        }

        public boolean isEmpty() {
            return live == null && persisted.isEmpty();
        }
    }
}
