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
package org.apache.rocketmq.studio.ops.ai;

import org.apache.rocketmq.studio.ops.ai.conversation.agent.AgentStreamOptions;
import org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent;

import java.util.function.Consumer;

/**
 * Gateway abstraction for agent runtimes. Implementations spawn the vendor CLI
 * (claude code / qoder) as a subprocess and pass credentials through the child
 * process environment — never through argv or persisted storage.
 *
 * <h2>Two streaming entry points, and why both exist</h2>
 * {@link #streamEvents(LlmConfigVO, AgentStreamOptions, Consumer)} is what a hosted agent run uses:
 * it yields the typed {@link AgentEvent} vocabulary (reasoning, tool calls, tool results, session
 * metadata) that the projector turns into live SSE frames and persisted timeline rows.
 * {@link #stream(LlmConfigVO, String, String, Consumer)} predates it and only carries text.
 *
 * <p>A {@code Consumer<String>} cannot express a tool call, so rather than reshape the text channel
 * the typed one is layered on top of it: the default {@code streamEvents} implementation adapts
 * every token into {@link AgentEvent.TextDelta}. That keeps a provider which implements neither
 * method (qoder) behaving exactly as before, and keeps the prompt-enhancement path — which genuinely
 * wants plain text and nothing else — on the narrow channel.
 */
public interface AgentProvider {

    String engine();

    boolean available();

    String complete(LlmConfigVO config, String prompt, String modelOverride);

    /**
     * Streams completion tokens as plain text.
     *
     * @deprecated kept only for callers that want text and nothing else, i.e. the prompt-enhancement
     *     rewrite in {@code PromptEnhancer}. Agent runs must use
     *     {@link #streamEvents(LlmConfigVO, AgentStreamOptions, Consumer)}, which carries reasoning,
     *     tool calls and session metadata instead of dropping everything but the prose.
     */
    @Deprecated
    default void stream(LlmConfigVO config, String prompt, String modelOverride,
                        Consumer<String> tokenConsumer) {
        tokenConsumer.accept(complete(config, prompt, modelOverride));
    }

    /**
     * Streams one agent run as typed events. The default implementation degrades to the deprecated
     * text channel, so a provider that only knows how to produce prose still works end to end; it
     * simply never reports reasoning, tool calls or a resumable session id.
     *
     * <p>Implementations that override this own the whole run: spawning the subprocess, attaching it
     * to {@link AgentStreamOptions#getProcessSink()} so a stop can kill the real process tree,
     * honouring {@link AgentStreamOptions#timeoutOr(java.time.Duration)} and translating every
     * upstream frame they do not model into {@link AgentEvent.UnhandledUpstream} rather than dropping
     * it silently.
     */
    default void streamEvents(LlmConfigVO config, AgentStreamOptions options,
                              Consumer<AgentEvent> sink) {
        stream(config, options.getPrompt(), options.getModel(),
                token -> sink.accept(new AgentEvent.TextDelta(token)));
    }
}
