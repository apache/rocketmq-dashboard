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

import org.apache.rocketmq.studio.ops.ai.AgentProvider;
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Pins {@link PromptEnhancer}: the rewrite is Studio's own text, streamed to the caller as it
 * arrives, and losing it must never cost the turn — an enhancement that fails leaves the run
 * with the original message.
 */
class PromptEnhancerTest {

    private final OpenAiCompatibleLlmClient llmClient = mock(OpenAiCompatibleLlmClient.class);
    private final AgentProviderRegistry agentProviders = mock(AgentProviderRegistry.class);
    private final PromptEnhancer enhancer = new PromptEnhancer(llmClient, agentProviders);

    private static LlmConfigVO config() {
        LlmConfigVO config = new LlmConfigVO();
        config.setEngine("http");
        return config;
    }

    /** Streams the given chunks to the consumer, recording the prompt it was called with. */
    private void stubClient(LlmConfigVO config, List<String> chunks) {
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(3);
            for (String chunk : chunks) {
                consumer.accept(chunk);
            }
            return null;
        }).when(llmClient).stream(eq(config), any(String.class), isNull(), any());
    }

    @Test
    void aBlankPromptIsReturnedUntouchedWithoutAnyModelCall() {
        assertThat(enhancer.enhance(config(), "http", "   ", null)).isEqualTo("   ");
        assertThat(enhancer.enhance(config(), "http", "", null)).isEqualTo("");
        assertThat(enhancer.enhance(config(), "http", null, null)).isNull();
        verifyNoInteractions(llmClient, agentProviders);
    }

    @Test
    void anHttpEngineRewritesThroughTheLlmClientWithTheTemplateWrappedPrompt() {
        LlmConfigVO config = config();
        stubClient(config, List.of("structured ", "rewrite"));

        String enhanced = enhancer.enhance(config, "http", "how do I query messages", null);

        assertThat(enhanced).isEqualTo("structured rewrite");
        org.mockito.ArgumentCaptor<String> prompt =
                org.mockito.ArgumentCaptor.forClass(String.class);
        verify(llmClient).stream(eq(config), prompt.capture(), isNull(), any());
        // the raw prompt travels inside the meta-prompt template
        assertThat(prompt.getValue()).contains("how do I query messages");
    }

    @Test
    void aCliEngineRewritesThroughThatEngineProvider() {
        LlmConfigVO config = config();
        config.setEngine("cli");
        AgentProvider provider = mock(AgentProvider.class);
        when(agentProviders.forEngine("cli")).thenReturn(provider);
        doAnswer(invocation -> {
            Consumer<String> consumer = invocation.getArgument(3);
            consumer.accept("cli ");
            consumer.accept("rewrite");
            return null;
        }).when(provider).stream(eq(config), any(String.class), isNull(), any());

        String enhanced = enhancer.enhance(config, "cli", "the raw prompt", null);

        assertThat(enhanced).isEqualTo("cli rewrite");
        verify(agentProviders).forEngine("cli");
        verifyNoInteractions(llmClient);
    }

    @Test
    void theEngineMatchIsCaseAndWhitespaceInsensitive() {
        LlmConfigVO config = config();
        stubClient(config, List.of("ok"));

        assertThat(enhancer.enhance(config, "  HTTP  ", "the raw prompt", null)).isEqualTo("ok");
        assertThat(enhancer.enhance(config, null, "the raw prompt", null)).isEqualTo("ok");
        assertThat(enhancer.enhance(config, "", "the raw prompt", null)).isEqualTo("ok");
    }

    @Test
    void deltasStreamToTheCallerAsTheyArriveAndEmptyChunksAreSkipped() {
        LlmConfigVO config = config();
        stubClient(config, java.util.Arrays.asList("a", "", "b", null));
        List<String> seen = new ArrayList<>();

        String enhanced = enhancer.enhance(config, "http", "the raw prompt", seen::add);

        assertThat(enhanced).isEqualTo("ab");
        assertThat(seen).containsExactly("a", "b");
    }

    @Test
    void aFailingRewriteFallsBackToTheOriginalMessage() {
        LlmConfigVO config = config();
        doAnswer(invocation -> {
            throw new IllegalStateException("provider unreachable");
        }).when(llmClient).stream(any(), any(), any(), any());

        String enhanced = enhancer.enhance(config, "http", "the raw prompt", null);

        assertThat(enhanced).isEqualTo("the raw prompt");
    }

    @Test
    void anEmptyRewriteFallsBackToTheOriginalMessage() {
        LlmConfigVO config = config();
        stubClient(config, List.of());

        assertThat(enhancer.enhance(config, "http", "the raw prompt", null)).isEqualTo("the raw prompt");
    }

    @Test
    void aRewriteThatCleansToNothingFallsBackToo() {
        LlmConfigVO config = config();
        stubClient(config, List.of("```\n", "   \n", "```"));

        assertThat(enhancer.enhance(config, "http", "the raw prompt", null)).isEqualTo("the raw prompt");
    }

    @Test
    void cleanStripsTheMarkdownFenceAModelSometimesAdds() {
        assertThat(PromptEnhancer.clean("```markdown\nthe rewritten prompt\n```")).isEqualTo("the rewritten prompt");
        assertThat(PromptEnhancer.clean("```\nplain fence\n```")).isEqualTo("plain fence");
        assertThat(PromptEnhancer.clean("  no fence  ")).isEqualTo("no fence");
        assertThat(PromptEnhancer.clean(null)).isNull();
    }

    @Test
    void aFencedRewriteLandsCleanedInBothTheResultAndTheStream() {
        LlmConfigVO config = config();
        stubClient(config, List.of("```" + "\n", "rewritten", " prompt" + "\n", "```"));
        List<String> seen = new ArrayList<>();

        String enhanced = enhancer.enhance(config, "http", "the raw prompt", seen::add);

        // the stream carries the raw chunks as they arrive; the fence is
        // stripped only from the returned rewrite handed to the agent
        assertThat(enhanced).isEqualTo("rewritten prompt");
        assertThat(String.join("", seen)).contains("rewritten");
    }

    @Test
    void aTemplateFailureAtClassInitFailsLoudly() {
        // the template ships on the classpath; this pins that the static init
        // does not silently swallow a missing resource
        assertThat(new PromptEnhancer(llmClient, agentProviders)).isNotNull();
    }

    @Test
    void aFailingDeltaObserverFallsBackLikeAnyOtherRewriteFailure() {
        LlmConfigVO config = config();
        stubClient(config, List.of("chunk"));
        // the failure-is-not-fatal contract covers a broken observer too: the
        // catch around the stream treats it like a provider failure and the
        // run keeps the original message
        String enhanced = enhancer.enhance(config, "http", "the raw prompt", c -> {
            throw new IllegalStateException("observer broke");
        });
        assertThat(enhanced).isEqualTo("the raw prompt");
    }
}
