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

import java.util.List;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiEngineLocaleTest {

    @Test
    void engineIdentifiersShouldIgnoreTheJvmDefaultLocale() {
        Locale previous = Locale.getDefault();
        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
        try {
            LlmConfigVO config = LlmConfigVO.builder().engine(" CUSTOM-CLI ").build();
            AgentProvider provider = new StubProvider("custom-cli");
            AgentProviderRegistry registry = new AgentProviderRegistry(List.of(provider));

            assertThat(config.normalizeEngine()).isEqualTo("custom-cli");
            assertThat(registry.forEngine(" CUSTOM-CLI ")).isSameAs(provider);
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void normalizeEngineShouldDefaultToHttpWhenBlank() {
        assertThat(LlmConfigVO.builder().build().normalizeEngine()).isEqualTo("http");
        assertThat(LlmConfigVO.builder().engine("   ").build().normalizeEngine())
                .isEqualTo("http");
    }

    @Test
    void readyShouldRequireEnabledAndModel() {
        assertThat(LlmConfigVO.builder().enabled(false).model("gpt-5").build().isReady())
                .isFalse();
        assertThat(LlmConfigVO.builder().enabled(true).model("  ").build().isReady())
                .isFalse();
    }

    @Test
    void readyShouldTrustCliEnginesWithoutGatewayCredentials() {
        LlmConfigVO config = LlmConfigVO.builder()
                .enabled(true)
                .model("gpt-5")
                .engine("claude-code")
                .build();

        assertThat(config.isReady()).isTrue();
    }

    @Test
    void readyShouldRequireAnApiBaseForHttpGateways() {
        LlmConfigVO config = LlmConfigVO.builder()
                .enabled(true)
                .model("qwen")
                .engine("http")
                .provider("ollama")
                .build();

        assertThat(config.isReady()).isFalse();

        config.setApiBase("http://localhost:11434/v1");
        assertThat(config.isReady()).isTrue();
    }

    @Test
    void registryShouldRejectUnsupportedEnginesWithAnActionableHint() {
        AgentProviderRegistry registry = new AgentProviderRegistry(List.of(new StubProvider("qoder")));

        assertThatThrownBy(() -> registry.forEngine("unknown-cli"))
                .isInstanceOfSatisfying(LlmGatewayException.class,
                        error -> {
                            assertThat(error.getStatusCode()).isEqualTo(400);
                            assertThat(error.getCode()).isEqualTo("llm.config.unsupported_engine");
                        });
        assertThatThrownBy(() -> registry.forEngine(null))
                .isInstanceOfSatisfying(LlmGatewayException.class,
                        error -> assertThat(error.getStatusCode()).isEqualTo(400));
    }

    private record StubProvider(String engine) implements AgentProvider {
        @Override
        public boolean available() {
            return true;
        }

        @Override
        public String complete(LlmConfigVO config, String prompt, String modelOverride) {
            return "ok";
        }
    }
}
