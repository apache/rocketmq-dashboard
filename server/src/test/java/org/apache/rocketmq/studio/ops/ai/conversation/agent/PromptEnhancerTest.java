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

import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class PromptEnhancerTest {

    @ParameterizedTest
    @ValueSource(strings = {"json5", "plain-text", "markdown", "js", ""})
    void cleanStripsTheWholeOpeningFenceLineTest(String info) {
        String wrapped = "```" + info + "\nrewrite of the prompt\n```\n";
        // A letters-only pattern used to leave the tail of the info string
        // ("5", "-text") as the first line of the enhanced prompt.
        assertThat(PromptEnhancer.clean(wrapped)).isEqualTo("rewrite of the prompt");
    }

    @Test
    void cleanKeepsUnclosedFencesTest() {
        assertThat(PromptEnhancer.clean("```\nrewrite")).isEqualTo("```\nrewrite");
    }

    @Test
    void cleanKeepsFencesEmbeddedInTheBodyTest() {
        String body = "before\n```sql\nSELECT 1\n```\nafter";
        assertThat(PromptEnhancer.clean(body)).isEqualTo(body);
    }

    @Test
    void cleanHandlesMultiLineRewritesTest() {
        String wrapped = "```json5\nfirst\n\nsecond\n```";
        assertThat(PromptEnhancer.clean(wrapped)).isEqualTo("first\n\nsecond");
    }

    @Test
    void cleanReturnsNullForNullInputTest() {
        assertThat(PromptEnhancer.clean(null)).isNull();
    }

    @Test
    void cleanPreservesSeparateCodeExamplesTest() {
        String response = "```json\n{\"key\":1}\n```\nCompare with this command:\n```bash\nprintf ok\n```";
        assertThat(PromptEnhancer.clean(response)).isEqualTo(response);
    }

    @ParameterizedTest
    @ValueSource(strings = {"```", "`````"})
    void cleanPreservesAnEarlyClosingFenceTest(String closing) {
        String response = "```json\n{}\n" + closing + "\nInstructions\n```";
        assertThat(PromptEnhancer.clean(response)).isEqualTo(response);
    }

    @Test
    void cleanPreservesWrapperWithShorterClosingFenceTest() {
        String response = "````markdown\nrewrite\n```";
        assertThat(PromptEnhancer.clean(response)).isEqualTo(response);
    }

    @Test
    void cleanStripsLongerOuterFenceAroundCodeExamplesTest() {
        String body = "```json\n{}\n```\nCompare:\n```bash\nprintf ok\n```";
        assertThat(PromptEnhancer.clean("````markdown\n" + body + "\n`````"))
                .isEqualTo(body);
    }

    @Test
    void cleanPreservesInvalidOpeningInfoStringTest() {
        String response = "```markdown `example`\nrewrite\n```";
        assertThat(PromptEnhancer.clean(response)).isEqualTo(response);
    }

    @Test
    void cleanIgnoresFenceLikeContentWithInfoStringTest() {
        String body = "first\n```not-a-close\nsecond";
        assertThat(PromptEnhancer.clean("```markdown\n" + body + "\n```"))
                .isEqualTo(body);
    }

    @Test
    void cleanRecognizesIndentedClosingFenceWithCrLfTest() {
        assertThat(PromptEnhancer.clean("```plain-text\r\nrewrite\r\n  ```\t\r\n"))
                .isEqualTo("rewrite");
    }

    @Test
    void enhancePreservesSeparateCodeExamplesFromStreamTest() {
        OpenAiCompatibleLlmClient client = mock(OpenAiCompatibleLlmClient.class);
        PromptEnhancer enhancer = new PromptEnhancer(client, mock(AgentProviderRegistry.class));
        String first = "```json\n{\"key\":1}\n```\nCompare with this command:\n";
        String second = "```bash\nprintf ok\n```";
        doAnswer(invocation -> {
            Consumer<String> collect = invocation.getArgument(3);
            collect.accept(first);
            collect.accept(second);
            return null;
        }).when(client).stream(any(LlmConfigVO.class), anyString(), isNull(), any());
        StringBuilder streamed = new StringBuilder();
        assertThat(enhancer.enhance(new LlmConfigVO(), "http", "Compare examples", streamed::append))
                .isEqualTo(first + second);
        assertThat(streamed.toString()).isEqualTo(first + second);
    }
}
