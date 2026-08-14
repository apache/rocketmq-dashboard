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
