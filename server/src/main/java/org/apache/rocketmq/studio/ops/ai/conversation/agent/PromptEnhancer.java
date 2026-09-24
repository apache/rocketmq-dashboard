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
package org.apache.rocketmq.studio.ops.ai.conversation.agent;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Rewrites a user's message into a structured prompt before the agent run, streaming the rewrite as it
 * arrives.
 *
 * <h2>Why this is its own class</h2>
 * The rewrite is not reasoning. It is Studio's own text, produced by a separate model call, and the bug
 * this whole redesign fixes was presenting it to the user under a heading that said chain of thought.
 * Keeping it separate from the run means the caller streams every chunk as
 * {@link org.apache.rocketmq.studio.ops.ai.conversation.event.AgentEvent.ThinkingDelta} with
 * {@link org.apache.rocketmq.studio.ops.ai.conversation.event.ThinkingSource#ENHANCE}, and the
 * persisted timeline keeps it in a block that can never merge with the model's own reasoning — the
 * projector cuts a thinking buffer when its source changes.
 *
 * <p>This is the only copy of the rewrite left. The one that used to sit in
 * {@code OpenAiCompatibleLlmGateway} for the legacy {@code POST /api/ai/chat} endpoint went away with
 * it, which is also the sole reason {@link org.apache.rocketmq.studio.ops.ai.AgentProvider#stream} is
 * still on the interface: this class wants text and nothing else, so it uses the narrow channel
 * rather than the typed {@code streamEvents} one.
 *
 * <h2>Failure is not fatal</h2>
 * An enhancement that fails leaves the run with the original message. Losing a rewrite costs prompt
 * quality; failing the turn would cost the answer, and the user never asked for a rewrite they cannot
 * do without.
 */
@Slf4j
@Component
public class PromptEnhancer {

    private static final String TEMPLATE_RESOURCE = "/prompts/enhance-prompt.txt";
    private static final String TEMPLATE = loadTemplate();

    private final OpenAiCompatibleLlmClient llmClient;
    private final AgentProviderRegistry agentProviders;

    public PromptEnhancer(OpenAiCompatibleLlmClient llmClient, AgentProviderRegistry agentProviders) {
        this.llmClient = llmClient;
        this.agentProviders = agentProviders;
    }

    /**
     * Streams the rewrite of {@code rawPrompt} and returns it.
     *
     * @param engine the run's engine; the rewrite goes through the same one so a deployment that only
     *     has a CLI agent does not need an HTTP provider configured as well
     * @param onDelta receives each chunk as it arrives, for the caller to emit as
     *     {@code ThinkingSource.ENHANCE}
     * @return the cleaned rewrite, or {@code rawPrompt} when the rewrite was empty or failed
     */
    public String enhance(LlmConfigVO config, String engine, String rawPrompt, Consumer<String> onDelta) {
        if (!StringUtils.hasText(rawPrompt)) {
            return rawPrompt;
        }
        String metaPrompt = TEMPLATE.formatted(rawPrompt);
        StringBuilder accumulated = new StringBuilder();
        Consumer<String> collect = chunk -> {
            if (!StringUtils.hasLength(chunk)) {
                return;
            }
            accumulated.append(chunk);
            if (onDelta != null) {
                onDelta.accept(chunk);
            }
        };
        try {
            if (isHttpEngine(engine)) {
                llmClient.stream(config, metaPrompt, null, collect);
            } else {
                agentProviders.forEngine(engine).stream(config, metaPrompt, null, collect);
            }
        } catch (RuntimeException exception) {
            log.warn("prompt enhancement failed, running the original message: {}", exception.toString());
            return rawPrompt;
        }
        String enhanced = clean(accumulated.toString());
        return StringUtils.hasText(enhanced) ? enhanced : rawPrompt;
    }

    /**
     * Strips the markdown fence a model sometimes wraps the rewrite in despite being told not to. The
     * fence is decoration in a chat answer but becomes part of the prompt handed to the agent, where it
     * reads as an instruction.
     */
    static String clean(String enhanced) {
        if (enhanced == null) {
            return null;
        }
        String cleaned = enhanced.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceAll("(?s)^```[a-zA-Z]*\\s*", "").replaceAll("(?s)```\\s*$", "").trim();
        }
        return cleaned;
    }

    private static boolean isHttpEngine(String engine) {
        return !StringUtils.hasText(engine)
                || LlmConfigVO.ENGINE_HTTP.equalsIgnoreCase(engine.trim().toLowerCase(Locale.ROOT));
    }

    private static String loadTemplate() {
        try (InputStream input = PromptEnhancer.class.getResourceAsStream(TEMPLATE_RESOURCE)) {
            if (input == null) {
                throw new IllegalStateException(TEMPLATE_RESOURCE.substring(1) + " is missing on classpath");
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load " + TEMPLATE_RESOURCE, exception);
        }
    }
}
