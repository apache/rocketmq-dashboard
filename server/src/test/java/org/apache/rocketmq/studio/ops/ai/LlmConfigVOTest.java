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

import com.fasterxml.jackson.databind.ObjectMapper;
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
    void exposesApiKeyConfigurationStateTest() {
        LlmConfigVO withKey = LlmConfigVO.builder().apiKey("sk-live").build();
        LlmConfigVO withoutKey = LlmConfigVO.builder().apiKey("  ").build();

        assertThat(withKey.isApiKeyConfigured()).isTrue();
        assertThat(withoutKey.isApiKeyConfigured()).isFalse();
    }

    @Test
    void readinessDependsOnEngineProviderAndCredentialsTest() {
        assertThat(LlmConfigVO.builder().enabled(false).model("m").build().isReady()).isFalse();
        assertThat(LlmConfigVO.builder().enabled(true).build().isReady()).isFalse();

        // CLI engines authenticate through the subprocess environment.
        assertThat(LlmConfigVO.builder().enabled(true).model("m").engine("claude-code")
                .build().isReady()).isTrue();

        // HTTP + ollama needs an api base but no key.
        assertThat(LlmConfigVO.builder().enabled(true).model("m").engine("http")
                .provider("ollama").apiBase("http://localhost:11434").build().isReady()).isTrue();
        assertThat(LlmConfigVO.builder().enabled(true).model("m").engine("http")
                .provider("ollama").build().isReady()).isFalse();

        // HTTP + other providers need both an api base and a key.
        LlmConfigVO openAi = LlmConfigVO.builder().enabled(true).model("m").engine("http")
                .provider("openai").apiBase("https://api.openai.com").build();
        assertThat(openAi.isReady()).isFalse();
        openAi.setApiKey("sk-live");
        assertThat(openAi.isReady()).isTrue();
    }

    @Test
    void serializationHidesSecretsAndExposesDerivedFlagsTest() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("openai")
                .engine("http")
                .apiKey("sk-secret")
                .apiBase("https://api.openai.com")
                .model("gpt-4o")
                .build();

        String json = mapper.writeValueAsString(config);

        assertThat(json).doesNotContain("sk-secret").doesNotContain("apiKey\"");
        assertThat(json).contains("\"apiKeyConfigured\":true");

        LlmConfigVO readBack = mapper.readValue(
                "{\"provider\":\"openai\",\"apiKey\":\"sk-2\"}", LlmConfigVO.class);
        assertThat(readBack.getApiKey()).isEqualTo("sk-2");
    }
}
