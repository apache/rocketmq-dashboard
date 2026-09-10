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

import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigVOTest {

    @Test
    void shouldNormalizeEngineIndependentlyOfDefaultLocale() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            LlmConfigVO config = LlmConfigVO.builder().engine(" CLI ").build();

            assertThat(config.normalizeEngine()).isEqualTo("cli");
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void normalizeEngineShouldDefaultToHttpWhenBlank() {
        assertThat(LlmConfigVO.builder().engine(null).build().normalizeEngine())
                .isEqualTo("http");
        assertThat(LlmConfigVO.builder().engine("  ").build().normalizeEngine())
                .isEqualTo("http");
    }

    @Test
    void shouldNotBeReadyWhenDisabled() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine("http")
                .provider("openai")
                .model("gpt-4o")
                .apiBase("https://api.example.com")
                .apiKey("secret")
                .enabled(false)
                .build();

        assertThat(config.isReady()).isFalse();
    }

    @Test
    void shouldNotBeReadyWithoutModel() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine("http")
                .provider("openai")
                .apiBase("https://api.example.com")
                .apiKey("secret")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isFalse();
    }

    @Test
    void cliEngineShouldBeReadyWithoutHttpCredentials() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine("claude-code")
                .model("sonnet")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isTrue();
    }

    @Test
    void ollamaHttpEngineShouldNotRequireApiKey() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine("http")
                .provider("ollama")
                .model("llama3")
                .apiBase("http://localhost:11434")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isTrue();
    }

    @Test
    void httpEngineShouldRequireApiBase() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine("http")
                .provider("openai")
                .model("gpt-4o")
                .apiKey("secret")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isFalse();
    }

    @Test
    void httpEngineShouldRequireApiKeyForNonOllamaProviders() {
        LlmConfigVO config = LlmConfigVO.builder()
                .engine("http")
                .provider("openai")
                .model("gpt-4o")
                .apiBase("https://api.example.com")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isFalse();
    }

    @Test
    void apiKeyConfiguredShouldReflectKeyPresence() {
        assertThat(LlmConfigVO.builder().apiKey("secret").build().isApiKeyConfigured()).isTrue();
        assertThat(LlmConfigVO.builder().apiKey(" ").build().isApiKeyConfigured()).isFalse();
        assertThat(LlmConfigVO.builder().build().isApiKeyConfigured()).isFalse();
    }
}
