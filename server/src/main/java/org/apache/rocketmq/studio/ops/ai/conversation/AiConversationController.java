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
package org.apache.rocketmq.studio.ops.ai.conversation;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.auth.AuthenticatedUserContext;
import org.apache.rocketmq.studio.common.domain.PageResult;
import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentCapabilityProbe;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiConversationCreateDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiConversationUpdateDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiMessageDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.ReportRunSpeedDTO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiAgentCapabilitiesVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationDetailVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationListItemVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiConversationVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiRmqctlConfigVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiRunVO;
import org.apache.rocketmq.studio.ops.ai.conversation.vo.AiTimelineVO;
import org.apache.rocketmq.studio.persistence.entity.RmqAiConversation;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * The conversation REST surface: eleven endpoints, nine of them the usual {@code Result<T>} JSON and
 * two of them SSE.
 *
 * <table border="1">
 *   <caption>Endpoints</caption>
 *   <tr><td>POST</td>   <td>{@code /api/ai/conversations}</td>                  <td>create</td></tr>
 *   <tr><td>GET</td>    <td>{@code /api/ai/conversations}</td>                  <td>page</td></tr>
 *   <tr><td>GET</td>    <td>{@code /api/ai/conversations/{id}}</td>             <td>detail + active run</td></tr>
 *   <tr><td>PATCH</td>  <td>{@code /api/ai/conversations/{id}}</td>             <td>rename / archive</td></tr>
 *   <tr><td>DELETE</td> <td>{@code /api/ai/conversations/{id}}</td>             <td>hard delete + workspace</td></tr>
 *   <tr><td>GET</td>    <td>{@code /api/ai/conversations/{id}/events}</td>      <td>persisted timeline</td></tr>
 *   <tr><td>POST</td>   <td>{@code /api/ai/conversations/{id}/messages}</td>    <td>SSE, opens a run</td></tr>
 *   <tr><td>GET</td>    <td>{@code /api/ai/runs/{runId}/stream}</td>            <td>SSE, re-attaches to a run</td></tr>
 *   <tr><td>POST</td>   <td>{@code /api/ai/runs/{runId}/stop}</td>              <td>idempotent stop</td></tr>
 *   <tr><td>GET</td>    <td>{@code /api/ai/agent-capabilities}</td>             <td>what this server can run</td></tr>
 *   <tr><td>GET</td>    <td>{@code /api/ai/conversations/{id}/rmqctl-config}</td><td>snippet for an external agent</td></tr>
 * </table>
 *
 * <h2>Two independent filters, and neither replaces the other</h2>
 * <strong>Role.</strong> {@code AuthInterceptor} makes the five write endpoints — create, message,
 * stop, rename/archive, delete — admin-only. That is not a leftover default: a hosted agent's tool calls
 * are signed with the instance credential the <em>server</em> resolves, not with the caller's identity,
 * so a reader who could start a run could perform the L2 mutations the interceptor refuses that reader
 * in the Tool Playground. Readers keep every GET, {@code GET …/runs/{runId}/stream} included, because
 * reading a transcript drives no tool.
 *
 * <strong>Ownership.</strong> There is no permission system beyond that role gate, and deliberately so:
 * every id in a path is resolved through {@link AiConversationService#requireOwned} (or
 * {@link AiRunService}, which does the same for a run), and a conversation belonging to somebody else is
 * a <strong>404</strong>, never a 403. A 403 tells a caller that the id exists, which turns the
 * auto-increment primary key into an enumeration oracle; this is the same guard
 * {@code QueryHistoryService.getMessageQueryResults} applies. Owner comes from
 * {@link AuthenticatedUserContext}, which the interceptor fills in before any handler runs, so the two
 * writers of a deployment still cannot read each other's transcripts.
 *
 * <h2>The two SSE endpoints</h2>
 * Both return the emitter a service already streaming into, and both set the four headers a streaming
 * response needs (see {@link #streamingHeaders}). The wire format itself belongs to
 * {@link AgentStreamSession}: every domain frame goes out as {@code event: agent} with the {@code type}
 * discriminator inside the JSON, {@code event: done} terminates, and {@code event: error} is reserved
 * for transport-level failure — a domain error is an {@code event: agent} frame of type {@code error},
 * because the TypeScript client <em>throws</em> on {@code event: error} instead of rendering it.
 *
 * <p>A refusal on either of them — 404 for somebody else's conversation, 409 for a second concurrent
 * run, 400 for an unusable message or an unconfigured provider — arrives as an {@code event: error}
 * frame inside a 200 event stream, not as an HTTP error status. It cannot arrive any other way: the
 * client asks for {@code text/event-stream} and the mapping declares the same as producible, so no JSON
 * converter can write a {@code Result} and the advice that would have written it fails instead.
 * {@link AiRunService#refusalStream(BusinessException)} is the one place that frame is built, and the
 * client turns it into a thrown {@code AiStreamError} carrying the status, the code and the hint.
 *
 * <p>Generation belongs to the run, not to the request: the emitter handed back by
 * {@code POST …/messages} is only its first observer, so hanging up does not stop the answer and
 * {@code GET …/runs/{runId}/stream} picks it back up.
 */
@Slf4j
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiConversationController {

    /** {@code GET …/events?after=}: everything from the beginning by default. */
    private static final int DEFAULT_AFTER_SEQ = 0;

    /** {@code GET …/events?limit=}; {@link AiConversationService} caps it at 500. */
    private static final int DEFAULT_TIMELINE_LIMIT = 200;

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_PAGE_SIZE = 20;

    /** nginx buffers a proxied response unless told not to; without this the stream arrives in chunks
     * of whatever the proxy's buffer size is, which reads as a stalled UI. */
    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    /** Fallback for a constraint that carries no message; the wording {@code GlobalExceptionHandler} uses. */
    private static final String INVALID_REQUEST_MESSAGE = "Invalid request";

    private final AiConversationService conversationService;
    private final AiRunService runService;
    private final RmqctlWorkspace rmqctlWorkspace;
    private final AgentCapabilityProbe capabilityProbe;

    /** 1. Creates an empty conversation owned by the caller. Both body fields are optional. */
    @PostMapping("/conversations")
    public Result<AiConversationVO> createConversation(
            @Valid @RequestBody(required = false) AiConversationCreateDTO request) {
        AiConversationCreateDTO body = request == null ? new AiConversationCreateDTO() : request;
        RmqAiConversation conversation =
                conversationService.create(owner(), body.getInstanceId(), body.getMode());
        log.debug("created AI conversation {} for {}", conversation.getId(), conversation.getOwner());
        return Result.ok(AiConversationVoAssembler.toConversationVo(conversation));
    }

    /**
     * 2. One page of the caller's conversations, most recently used first, each row carrying its newest
     * run so the list can show a status. {@code search} is a substring match on the title and the
     * service escapes the LIKE wildcards in it; {@code archived} left out means "both".
     */
    @GetMapping("/conversations")
    public Result<PageResult<AiConversationListItemVO>> listConversations(
            @RequestParam(name = "page", defaultValue = "" + DEFAULT_PAGE) int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size,
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "archived", required = false) Boolean archived) {
        // Page and size are clamped by the service (1.. and 1..100) rather than rejected here: an
        // out-of-range page is a stale bookmark, not an attack, and a 400 for it would be noise.
        return Result.ok(conversationService.listItems(owner(), trimToNull(search), archived, page, size));
    }

    /**
     * 3. One conversation plus the run still generating, so a reload can re-attach instead of showing a
     * transcript that looks finished but is not.
     *
     * @throws BusinessException 404 when the id is unknown <em>or</em> belongs to somebody else
     */
    @GetMapping("/conversations/{id}")
    public Result<AiConversationDetailVO> getConversation(@PathVariable("id") Long conversationId) {
        RmqAiConversation conversation = conversationService.requireOwned(conversationId, owner());
        return Result.ok(AiConversationVoAssembler.toConversationDetailVo(conversation,
                conversationService.activeRun(conversationId).orElse(null)));
    }

    /**
     * 4. Renames and/or archives. A field left out of the body is left alone, and an update that changes
     * nothing does not bump {@code gmt_modified} — otherwise renaming a conversation to the title it
     * already has would reorder the list.
     */
    @PatchMapping("/conversations/{id}")
    public Result<AiConversationVO> updateConversation(@PathVariable("id") Long conversationId,
                                                       @Valid @RequestBody AiConversationUpdateDTO request) {
        RmqAiConversation conversation = conversationService.update(conversationId, owner(),
                request == null ? null : request.getTitle(),
                request == null ? null : request.getArchived());
        return Result.ok(AiConversationVoAssembler.toConversationVo(conversation));
    }

    /**
     * 5. Hard delete. The service stops an active run first, then removes events, runs and the
     * conversation in that order (this project declares no foreign keys, so the cascade is code), and
     * finally the per-conversation agent workspace with the {@code claude} resume state inside it.
     */
    @DeleteMapping("/conversations/{id}")
    public Result<Void> deleteConversation(@PathVariable("id") Long conversationId) {
        conversationService.delete(conversationId, owner());
        return Result.ok();
    }

    /**
     * 6. The persisted timeline, cursor-paged on {@code seq}: {@code after} is exclusive and
     * {@code nextAfter} in the answer is the cursor for the following page, or null at the tail.
     *
     * <p>Items are the full {@code TimelineItem} contract — the row id, the run that produced it and the
     * <em>deserialised</em> event, not the raw {@code type} + {@code payload} columns. The query behind
     * this has no SQL {@code ORDER BY} on purpose ({@code payload} is MEDIUMTEXT and a filesort would
     * materialise it into {@code sort_buffer_size}); the repository sorts the bounded slice in memory.
     */
    @GetMapping("/conversations/{id}/events")
    public Result<AiTimelineVO> conversationEvents(
            @PathVariable("id") Long conversationId,
            @RequestParam(name = "after", defaultValue = "" + DEFAULT_AFTER_SEQ) int after,
            @RequestParam(name = "limit", defaultValue = "" + DEFAULT_TIMELINE_LIMIT) int limit) {
        return Result.ok(AiConversationVoAssembler.toTimelineVo(
                conversationService.timeline(conversationId, owner(), after, limit)));
    }

    /**
     * 7. Sends one turn and streams the run it opens. Replaces {@code POST /api/ai/chat}.
     *
     * <p>Admission happens inside {@link AiRunService#sendMessage}, which has already persisted the
     * user's turn and published {@code run_started} by the time this returns, so a client that starts
     * reading the body cannot miss the first frame.
     *
     * <p>A refusal — 404 for somebody else's conversation, 400 for an unusable message, 409 when the
     * conversation already has an answer in progress, 400 when no provider is configured — arrives as an
     * {@code event: error} frame carrying that status and code, not as an HTTP error: this endpoint
     * cannot write a JSON {@code Result} to a request that asked for {@code text/event-stream}. See
     * {@link AiRunService#refusalStream(BusinessException)}.
     */
    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(@PathVariable("id") Long conversationId,
                                  @Valid @RequestBody AiMessageDTO request,
                                  BindingResult bindingResult,
                                  HttpServletResponse response) {
        // Both outcomes of this endpoint are event streams — an admitted run and a refusal alike — so the
        // headers go on first. A refusal is converted rather than thrown: see refusalStream.
        streamingHeaders(response);
        if (bindingResult.hasErrors()) {
            // A BindingResult directly after the @Valid body makes Spring hand the violations here
            // instead of throwing MethodArgumentNotValidException. That is the only way to answer them
            // in kind: an @ExceptionHandler cannot return an SseEmitter at all, because the exception
            // resolver has no return-value handler for one, and it cannot write a JSON Result either,
            // because nothing JSON is acceptable to a request that asked for text/event-stream.
            return runService.refusalStream(HttpStatus.BAD_REQUEST.value(), AiRunService.REFUSED_INVALID_CODE,
                    firstViolation(bindingResult), null);
        }
        try {
            return runService.sendMessage(conversationId, runRequest(request));
        } catch (BusinessException refusal) {
            return runService.refusalStream(refusal);
        } catch (LlmGatewayException refusal) {
            return runService.refusalStream(refusal);
        }
    }

    /**
     * 8. Attaches to a run that is already generating: replays the persisted events with
     * {@code seq > after}, translated back into live frames, registers this connection as an observer,
     * then tails until {@code done}. A run that has already reached a terminal state replays and closes
     * immediately.
     *
     * <p>An unknown run, or one whose conversation belongs to somebody else, arrives as an
     * {@code event: error} frame for the same reason as above.
     */
    @GetMapping(value = "/runs/{runId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter attachRunStream(@PathVariable("runId") Long runId,
                                      @RequestParam(name = "after", defaultValue = "" + DEFAULT_AFTER_SEQ) int after,
                                      HttpServletResponse response) {
        streamingHeaders(response);
        try {
            return runService.attach(runId, after);
        } catch (BusinessException refusal) {
            return runService.refusalStream(refusal);
        }
    }

    /**
     * 9. Stops a run. Idempotent by design, because it backs a button a user may press twice: an
     * already-terminal run is a 200 no-op. A run that is not the caller's is a 404, and a run that is no
     * longer the conversation's active one is a 409 that kills nothing — the {@code runId} the caller
     * holds came from a {@code run_started} frame it may have received a whole turn ago, and the one
     * unrecoverable mistake available here is stopping the answer the user is currently watching.
     *
     * <p>Stopping does not close the stream: it stays open to deliver the terminal {@code run_status}
     * frame and {@code done}, which is what lets the button leave its "stopping" state.
     */
    @PostMapping("/runs/{runId}/stop")
    public Result<AiRunVO> stopRun(@PathVariable("runId") Long runId) {
        return Result.ok(AiConversationVoAssembler.toRunVo(runService.stop(runId)));
    }

    /**
     * 9a. Persists the generation speed the client measured while the run streamed, so a
     * replayed transcript can show the same number next to the answer. Ownership is enforced by
     * the service (somebody else's run is a 404); the value is bounded by the DTO's validation.
     */
    @PostMapping("/runs/{runId}/speed")
    public Result<Void> reportRunSpeed(@PathVariable("runId") Long runId,
                                       @Valid @RequestBody ReportRunSpeedDTO request) {
        runService.reportSpeed(runId, request.getTokensPerSecond());
        return Result.ok();
    }

    /**
     * 10. What this server can actually run, probed at request time and cached briefly: the two agent
     * CLIs, the {@code rmqctl} MCP transport, whether the MCP server is enabled at all, and whether
     * destructive (L3) tools are allowed.
     *
     * <p>The last two exist so the UI can explain a refusal instead of showing a mystery error. A
     * hosted agent cannot run an L3 tool unless {@code studio.ai.allow-l3-tools} is on, and with
     * {@code mcpEnabled=false} no tool of any tier can execute; both are facts about the deployment
     * that the composer has no other way to learn.
     */
    @GetMapping("/agent-capabilities")
    public Result<AiAgentCapabilitiesVO> agentCapabilities() {
        AgentCapabilityProbe.Binaries binaries = capabilityProbe.binaries();
        return Result.ok(AiAgentCapabilitiesVO.builder()
                .rmqctlAvailable(binaries.rmqctl())
                .claudeAvailable(binaries.claude())
                .qoderAvailable(binaries.qoder())
                .mcpEnabled(capabilityProbe.mcpEnabled())
                .l3ToolsAllowed(capabilityProbe.l3ToolsAllowed())
                .build());
    }

    /**
     * 11. The paste-ready {@code {"mcpServers":{...}}} snippet for pointing an agent Studio does
     * <em>not</em> host — Claude Desktop, Cursor — at this conversation's instance. Same signed gateway,
     * same per-instance binding, same risk gates; the hosted agent is only the first customer of the
     * tool channel, and this endpoint is what makes that claim checkable.
     *
     * <p>Never carries a secret and cannot: the credential stays an {@code env:} reference in the
     * user's own {@code rmqctl} config, which is also why the snippet omits {@code --config} (the path
     * this server would pass exists only inside its own container) and why {@code server} is returned
     * separately, so the caller can tell the user what to put there.
     *
     * @throws BusinessException 404 for somebody else's conversation, 400 for one that is not bound to
     *     an instance — {@code rmqctl} refuses to default {@code --instance-id}, so a snippet without
     *     one would fail at the first tool call rather than at configuration time
     */
    @GetMapping("/conversations/{id}/rmqctl-config")
    public Result<AiRmqctlConfigVO> rmqctlConfig(@PathVariable("id") Long conversationId) {
        RmqAiConversation conversation = conversationService.requireOwned(conversationId, owner());
        String instanceId = conversation.getInstanceId();
        if (!StringUtils.hasText(instanceId)) {
            throw new BusinessException(400,
                    "conversation " + conversationId + " is not bound to an instance, so it has no rmqctl config");
        }
        return Result.ok(AiRmqctlConfigVO.builder()
                .snippet(rmqctlWorkspace.externalMcpSnippet(instanceId))
                .instanceId(instanceId.trim())
                .server(rmqctlWorkspace.serverUrl())
                .build());
    }

    /**
     * The message of the first violated constraint, or {@value #INVALID_REQUEST_MESSAGE} when one
     * carries none. The wording and the "first error only" rule are the ones
     * {@code GlobalExceptionHandler} applies, so a rejected body reads the same whether it arrived at a
     * JSON endpoint or at the streaming one.
     */
    private static String firstViolation(BindingResult bindingResult) {
        return bindingResult.getFieldErrors().stream()
                .findFirst()
                .map(error -> StringUtils.hasText(error.getDefaultMessage())
                        ? error.getDefaultMessage()
                        : INVALID_REQUEST_MESSAGE)
                .orElse(INVALID_REQUEST_MESSAGE);
    }

    /**
     * The four headers every streaming response carries.
     *
     * <p>{@code X-Accel-Buffering: no} because nginx buffers a proxied response otherwise;
     * {@code no-transform} because a proxy that rewrites or gzips the body breaks the frame boundaries
     * the client splits on; {@code Connection: keep-alive} because a stream is the one response that
     * must not be closed early. The content type is set explicitly as well as through {@code produces}:
     * the client refuses a response that is not {@code text/event-stream}, since a buffering gateway
     * answering 200 with {@code text/html} is the most common production failure of an SSE feature and
     * is otherwise invisible.
     *
     * <p>The heartbeat is not here — {@link AgentStreamSession} sends a {@code comment("hb")} every
     * 15 s from a shared scheduler, and the client's idle watchdog is twice that. Lengthening one
     * without the other makes every stream look dead.
     */
    private static void streamingHeaders(HttpServletResponse response) {
        response.setContentType(MediaType.TEXT_EVENT_STREAM_VALUE);
        response.setHeader(ACCEL_BUFFERING_HEADER, "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-transform");
        response.setHeader(HttpHeaders.CONNECTION, "keep-alive");
    }

    /** One turn as the streaming endpoint received it, in the shape {@link AiRunService} admits. */
    private static AiRunService.RunRequest runRequest(AiMessageDTO request) {
        return new AiRunService.RunRequest(request.getMessage(), request.getModel(), request.getEngine(),
                request.getMode(), request.isEnhance(), request.getResume());
    }

    /** The operator every id in this controller is scoped to. */
    private static String owner() {
        return AuthenticatedUserContext.currentUsernameOrSystem();
    }

    /** A blank filter is no filter: a search for whitespace would otherwise match titles containing it. */
    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
