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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.WebMvcAuthTestSupport;
import org.apache.rocketmq.studio.auth.LoginVO;
import org.apache.rocketmq.studio.common.config.LegacyJackson2Config;
import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentCapabilityProbe;
import org.apache.rocketmq.studio.ops.ai.conversation.agent.RmqctlWorkspace;
import org.apache.rocketmq.studio.ops.ai.conversation.dto.AiMessageDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The two SSE endpoints of {@link AiConversationController}: {@code POST …/messages}, which opens a
 * run, and {@code GET …/runs/{runId}/stream}, which attaches to one that is already generating.
 *
 * <p>What is asserted here is the part of the contract a JSON test cannot see:
 * <ul>
 *   <li>{@code text/event-stream}, because the client <em>refuses</em> a response with any other
 *       content type — a buffering gateway answering 200 with {@code text/html} is the most common
 *       production failure of an SSE feature and is invisible without that guard;</li>
 *   <li>the three headers that keep a proxy from swallowing the stream
 *       ({@code X-Accel-Buffering: no}, {@code Cache-Control: no-cache, no-transform},
 *       {@code Connection: keep-alive});</li>
 *   <li>that the response really is asynchronous, i.e. an emitter was handed to the container rather
 *       than a buffered body;</li>
 *   <li>that a refusal — a second concurrent run, somebody else's conversation, an unconfigured
 *       provider, a body that failed validation — is converted into a stream the client can read
 *       instead of escaping as an exception. This is not a nicety: the request asked for
 *       {@code text/event-stream} and the mapping declares the same as producible, so no JSON converter
 *       can write the {@code Result} envelope {@code GlobalExceptionHandler} returns, the advice fails,
 *       and the browser is left with a 500 error page where the reason should have been. The transport
 *       error frame is the one shape that gets through, and it is the shape the TypeScript client turns
 *       into a thrown {@code AiStreamError};</li>
 *   <li>the role gate: a reader may attach to a run but may not start one.</li>
 * </ul>
 *
 * <p>The emitter is {@link AiRunTestSupport.RecordingSseEmitter}, handed out by the mocked service the
 * way {@code OpenAiCompatibleLlmGatewayTest} hands it out through the gateway's package-private
 * constructor seam: nothing is written to a socket, and the stream budget chosen for the emitter is
 * observable on the recorded instance.
 */
@WebMvcTest(AiConversationController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(LegacyJackson2Config.class)
class AiStreamControllerTest extends WebMvcAuthTestSupport {

    private static final String OWNER = "terrance";
    private static final long CONVERSATION_ID = 7L;
    private static final long RUN_ID = 11L;

    /** The two roles {@code AuthInterceptor} distinguishes; only a writer may start or stop a run. */
    private static final boolean WRITER = true;
    private static final boolean READER = false;

    /** {@code AiRunExecutor.CLI_STREAM_TIMEOUT_MILLIS}: the budget a CLI run gets. */
    private static final long CLI_TIMEOUT_MILLIS = 300_000L;

    private static final String ACCEL_BUFFERING_HEADER = "X-Accel-Buffering";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private AiConversationService conversationService;

    @MockitoBean
    private AiRunService runService;

    @MockitoBean
    private RmqctlWorkspace rmqctlWorkspace;

    @MockitoBean
    private AgentCapabilityProbe capabilityProbe;

    @BeforeEach
    @Override
    protected void configureControllerSliceAuth() {
        super.configureControllerSliceAuth();
        // AuthWebConfig is a WebMvcConfigurer, so this slice registers AuthInterceptor: its preHandle
        // clears AuthenticatedUserContext before the handler runs, which is why the operator is signed in
        // through the interceptor rather than by setting the thread-local directly. Login has to be
        // required for it to do that at all.
        when(authProperties.isLoginRequired()).thenReturn(true);
        signIn(WRITER);
    }

    /** Signs in an operator of one role or the other; the write endpoints of this controller need a writer. */
    private void signIn(boolean admin) {
        when(authService.getAuthenticatedUser(any())).thenReturn(Optional.of(LoginVO.UserInfo.builder()
                .userId(1L)
                .username(OWNER)
                .admin(admin)
                .build()));
    }

    // --- 7. POST /api/ai/conversations/{id}/messages ---------------------------

    @Test
    void sendMessageShouldStreamAsEventStreamWithTheBufferingHeadersTest() throws Exception {
        AiRunTestSupport.RecordingSseEmitter emitter =
                new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS);
        when(runService.sendMessage(eq(CONVERSATION_ID), any())).thenReturn(emitter);

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"why is the lag growing\"}"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"))
                .andExpect(header().string("Cache-Control", "no-cache, no-transform"))
                .andExpect(header().string("Connection", "keep-alive"));

        // The budget captured through the package-private constructor seam, the OpenAiCompatible-
        // LlmGatewayTest idiom: the emitter the service built is the one the container received, and
        // the controller handed it over without completing it — generation outlives this request.
        assertThat(emitter.timeoutMillis()).isEqualTo(CLI_TIMEOUT_MILLIS);
        assertThat(emitter.completed()).isFalse();
    }

    @Test
    void sendMessageShouldHandTheTurnToTheRunServiceTest() throws Exception {
        when(runService.sendMessage(eq(CONVERSATION_ID), any()))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(objectMapper.writeValueAsString(AiMessageDTO.builder()
                                .message("why is the lag growing")
                                .model("claude-sonnet-4-5")
                                .engine("claude-code")
                                .mode("diagnose")
                                .enhance(true)
                                .build())))
                .andExpect(status().isOk());

        ArgumentCaptor<AiRunService.RunRequest> captor = ArgumentCaptor.forClass(AiRunService.RunRequest.class);
        verify(runService).sendMessage(eq(CONVERSATION_ID), captor.capture());
        AiRunService.RunRequest request = captor.getValue();
        assertThat(request.message()).isEqualTo("why is the lag growing");
        assertThat(request.model()).isEqualTo("claude-sonnet-4-5");
        assertThat(request.engine()).isEqualTo("claude-code");
        assertThat(request.mode()).isEqualTo("diagnose");
        assertThat(request.enhance()).isTrue();
        // Omitted by the client, so it must arrive as null and not as false: false means "start a fresh
        // provider session", which would silently end multi-turn --resume on every single turn.
        assertThat(request.resume()).isNull();
    }

    @Test
    void sendMessageShouldPassAnExplicitResumeFlagThroughTest() throws Exception {
        when(runService.sendMessage(eq(CONVERSATION_ID), any()))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"start over\",\"resume\":false}"))
                .andExpect(status().isOk());

        ArgumentCaptor<AiRunService.RunRequest> captor = ArgumentCaptor.forClass(AiRunService.RunRequest.class);
        verify(runService).sendMessage(eq(CONVERSATION_ID), captor.capture());
        assertThat(captor.getValue().resume()).isFalse();
    }

    @Test
    void sendMessageShouldAnswerARefusedAdmissionInsideTheStreamTest() throws Exception {
        // A second run for one conversation is refused. The refusal still has to reach the client as a
        // stream: an HTTP 409 with a JSON body is not writable to a request that asked for
        // text/event-stream, so without this the browser would get a 500 error page instead of the reason.
        BusinessException busy = new BusinessException(409, AiRunService.BUSY_MESSAGE);
        when(runService.sendMessage(eq(CONVERSATION_ID), any())).thenThrow(busy);
        when(runService.refusalStream(any(BusinessException.class)))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"and again\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"));

        ArgumentCaptor<BusinessException> captor = ArgumentCaptor.forClass(BusinessException.class);
        verify(runService).refusalStream(captor.capture());
        assertThat(captor.getValue()).isSameAs(busy);
        assertThat(captor.getValue().getCode()).isEqualTo(409);
    }

    @Test
    void sendMessageShouldAnswerAnotherOperatorsConversationInsideTheStreamTest() throws Exception {
        when(runService.sendMessage(eq(CONVERSATION_ID), any()))
                .thenThrow(new BusinessException(404, "AI conversation not found"));
        when(runService.refusalStream(any(BusinessException.class)))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        ArgumentCaptor<BusinessException> captor = ArgumentCaptor.forClass(BusinessException.class);
        verify(runService).refusalStream(captor.capture());
        // 404 and not 403, so the id of somebody else's conversation cannot be enumerated.
        assertThat(captor.getValue().getCode()).isEqualTo(404);
    }

    @Test
    void sendMessageShouldAnswerAnUnconfiguredProviderInsideTheStreamTest() throws Exception {
        LlmGatewayException unconfigured = new LlmGatewayException(400, "llm.config.incomplete",
                "LLM provider is not configured or enabled", "Configure an LLM provider in Studio settings.");
        when(runService.sendMessage(eq(CONVERSATION_ID), any())).thenThrow(unconfigured);
        when(runService.refusalStream(any(LlmGatewayException.class)))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"hello\"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        // A gateway exception carries its own stable code and hint, which is what the UI's error block
        // shows, so it must be handed on as itself rather than flattened into a status.
        verify(runService).refusalStream(unconfigured);
    }

    @Test
    void sendMessageShouldRejectAMissingBodyInsideTheStreamTest() throws Exception {
        when(runService.refusalStream(eq(400), eq(AiRunService.REFUSED_INVALID_CODE),
                eq("message is required"), isNull()))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"));

        verify(runService).refusalStream(400, AiRunService.REFUSED_INVALID_CODE, "message is required", null);
        verify(runService, never()).sendMessage(any(), any());
    }

    @Test
    void sendMessageShouldRejectABlankMessageInsideTheStreamTest() throws Exception {
        // The endpoint takes a BindingResult next to the @Valid body, so a rejected body is answered in
        // the same shape as every other refusal instead of as an HTTP status: an @ExceptionHandler cannot
        // return an SseEmitter, and no JSON Result is writable to a request that asked for a stream.
        when(runService.refusalStream(eq(400), eq(AiRunService.REFUSED_INVALID_CODE),
                eq("message is required"), isNull()))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"));

        verify(runService).refusalStream(400, AiRunService.REFUSED_INVALID_CODE, "message is required", null);
        verify(runService, never()).sendMessage(any(), any());
    }

    @Test
    void sendMessageShouldRejectAnOversizedMessageInsideTheStreamTest() throws Exception {
        // Only the message constraint is violated: the handler reports the first field error, so a
        // second violation would make the reported message arbitrary.
        when(runService.refusalStream(eq(400), eq(AiRunService.REFUSED_INVALID_CODE),
                eq("message must not exceed 8192 characters"), isNull()))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content(objectMapper.writeValueAsString(Map.of("message", "m".repeat(8193)))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(runService, never()).sendMessage(any(), any());
    }

    // --- 8. GET /api/ai/runs/{runId}/stream ------------------------------------

    @Test
    void attachRunStreamShouldStreamAsEventStreamWithTheBufferingHeadersTest() throws Exception {
        when(runService.attach(RUN_ID, 42))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(get("/api/ai/runs/{runId}/stream", RUN_ID)
                        .param("after", "42")
                        .accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"))
                .andExpect(header().string("Cache-Control", "no-cache, no-transform"))
                .andExpect(header().string("Connection", "keep-alive"));

        // The cursor is what makes a reconnect lossless: everything the client already holds is skipped,
        // and the session then drops live frames at or below the highest seq it replayed.
        verify(runService).attach(RUN_ID, 42);
    }

    @Test
    void attachRunStreamShouldReplayFromTheBeginningByDefaultTest() throws Exception {
        when(runService.attach(RUN_ID, 0))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(get("/api/ai/runs/{runId}/stream", RUN_ID).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));

        verify(runService).attach(RUN_ID, 0);
    }

    @Test
    void attachRunStreamShouldAnswerAnUnknownRunInsideTheStreamTest() throws Exception {
        when(runService.attach(RUN_ID, 0)).thenThrow(new BusinessException(404, "AI run not found"));
        when(runService.refusalStream(any(BusinessException.class)))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(get("/api/ai/runs/{runId}/stream", RUN_ID).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"));

        ArgumentCaptor<BusinessException> captor = ArgumentCaptor.forClass(BusinessException.class);
        verify(runService).refusalStream(captor.capture());
        assertThat(captor.getValue().getCode()).isEqualTo(404);
    }

    // --- the role gate ----------------------------------------------------------

    @Test
    void sendMessageShouldRefuseAReaderTest() throws Exception {
        // A hosted agent's tool calls are signed with the instance credential the SERVER resolves, not
        // with the caller's identity. Opening this endpoint to a reader would let a reader perform the L2
        // mutations that AuthInterceptor.isReaderAccessibleToolPath refuses that reader in the Tool
        // Playground. The old POST /api/ai/chat was reader-accessible because it ran a CLI with every
        // tool disabled; this endpoint is not equivalent to it.
        signIn(READER);

        mockMvc.perform(post("/api/ai/conversations/{id}/messages", CONVERSATION_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.TEXT_EVENT_STREAM)
                        .content("{\"message\":\"delete that topic\"}"))
                .andExpect(status().isForbidden());

        verify(runService, never()).sendMessage(any(), any());
    }

    @Test
    void attachRunStreamShouldStayOpenToAReaderTest() throws Exception {
        // Attaching drives no tool: it replays persisted rows and tails frames a run is already
        // producing, so a reader keeps it — which is also what lets a reader watch an answer a writer
        // started on a conversation they both can see.
        signIn(READER);
        when(runService.attach(RUN_ID, 0))
                .thenReturn(new AiRunTestSupport.RecordingSseEmitter(CLI_TIMEOUT_MILLIS));

        mockMvc.perform(get("/api/ai/runs/{runId}/stream", RUN_ID).accept(MediaType.TEXT_EVENT_STREAM))
                .andExpect(status().isOk())
                .andExpect(header().string(ACCEL_BUFFERING_HEADER, "no"));

        verify(runService).attach(RUN_ID, 0);
    }
}
