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

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void cleanKeepsSeparateCodeExamplesTest(String eol) {
        String input = String.join(eol,
                "```json", "{\"key\":1}", "```", "Compare with this command:",
                "```bash", "printf ok", "```");
        assertThat(PromptEnhancer.clean(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void cleanKeepsAWrapperWithoutALegalClosingFenceTest(String eol) {
        String input = String.join(eol, "````markdown", "Keep this example:", "```");
        assertThat(PromptEnhancer.clean(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"```not-a-close", "    ```", "~~~", "```\ttext"})
    void cleanKeepsInvalidClosingLinesTest(String closing) {
        String input = "```text\nbody\n" + closing;
        assertThat(PromptEnhancer.clean(input)).isEqualTo(input);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void cleanRemovesACompleteLongWrapperAroundShorterCodeFencesTest(String eol) {
        String body = String.join(eol, "Example:", "```sql", "SELECT 1", "```");
        String input = "````markdown" + eol + body + eol + "  `````\t";
        assertThat(PromptEnhancer.clean(input)).isEqualTo(body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\r\n"})
    void cleanAcceptsIndentedClosingFenceAndRetainsBodyLineEndingsTest(String eol) {
        String body = "first" + eol + "second";
        assertThat(PromptEnhancer.clean("```plain-text" + eol + body + eol + "   ```"))
                .isEqualTo(body);
    }

    @Test
    void cleanDoesNotTreatABacktickInTheInfoStringAsAWrapperTest() {
        String input = "```invalid`info\nbody\n```";
        assertThat(PromptEnhancer.clean(input)).isEqualTo(input);
    }

    @Test
    void cleanHandlesEmptyWrappedContentTest() {
        assertThat(PromptEnhancer.clean("```\n``` ")).isEmpty();
        assertThat(PromptEnhancer.clean("  ")).isEmpty();
    }

    @Test
    void enhancePreservesSeparateExamplesInTheReturnedPromptTest() {
        String input = "```text\nread only\n```\nKeep both examples.\n```sql\nSELECT 1\n```";
        OpenAiCompatibleLlmClient client = mock(OpenAiCompatibleLlmClient.class);
        doAnswer(call -> {
            Consumer<String> sink = call.getArgument(3);
            sink.accept(input);
            return null;
        }).when(client).stream(any(), anyString(), isNull(), any());
        PromptEnhancer enhancer = new PromptEnhancer(client, null);
        assertThat(enhancer.enhance(null, "http", "Explain the query", null)).isEqualTo(input);
    }

}
