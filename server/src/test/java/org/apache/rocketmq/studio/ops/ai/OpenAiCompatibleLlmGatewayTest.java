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
import org.mockito.ArgumentCaptor;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAiCompatibleLlmGatewayTest {

    private final LlmConfigService configService = mock(LlmConfigService.class);
    private final OpenAiCompatibleLlmClient llmClient = mock(OpenAiCompatibleLlmClient.class);
    private final LlmGatewayStub fallbackGateway = mock(LlmGatewayStub.class);
    private final OpenAiCompatibleLlmGateway gateway = new OpenAiCompatibleLlmGateway(
            configService, llmClient, fallbackGateway, new ObjectMapper());

    @Test
    void chatShouldFallbackToStubWhenConfigIsIncomplete() {
        ChatDTO request = ChatDTO.builder().message("hello").build();
        SseEmitter fallbackEmitter = new SseEmitter();
        when(configService.getConfig()).thenReturn(config("openai", ""));
        when(llmClient.supports(any(LlmConfigVO.class))).thenReturn(true);
        when(fallbackGateway.chat(request)).thenReturn(fallbackEmitter);

        SseEmitter result = gateway.chat(request);

        assertThat(result).isSameAs(fallbackEmitter);
        verify(fallbackGateway).chat(request);
    }

    @Test
    void executeShouldUseRealClientWhenConfigIsComplete() {
        LlmConfigVO config = config("openai", "sk-test");
        AiCommandDTO command = AiCommandDTO.builder()
                .command("list topics")
                .prompt("diagnose")
                .model("gpt-4o-mini")
                .context(Map.of("cluster", "prod"))
                .build();
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(true);
        when(llmClient.complete(eq(config), any(String.class), eq("gpt-4o-mini"))).thenReturn("done");

        String result = gateway.execute(command);

        assertThat(result).isEqualTo("done");
        ArgumentCaptor<String> promptCaptor = ArgumentCaptor.forClass(String.class);
        verify(llmClient).complete(eq(config), promptCaptor.capture(), eq("gpt-4o-mini"));
        assertThat(promptCaptor.getValue()).contains("diagnose");
        assertThat(promptCaptor.getValue()).contains("\"cluster\":\"prod\"");
    }

    @Test
    void executeShouldRejectConfiguredUnsupportedProvider() {
        LlmConfigVO config = config("bedrock", "aws-key");
        when(configService.getConfig()).thenReturn(config);
        when(llmClient.supports(config)).thenReturn(false);

        assertThatThrownBy(() -> gateway.execute(AiCommandDTO.builder().command("hello").build()))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM provider is not supported by the OpenAI-compatible gateway")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(400);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.config.unsupported_provider");
                    assertThat(gatewayException.getHint()).contains("openai");
                });
    }

    private LlmConfigVO config(String provider, String apiKey) {
        return LlmConfigVO.builder()
                .provider(provider)
                .apiKey(apiKey)
                .apiBase("https://example.com/v1")
                .model("gpt-test")
                .maxTokens(256)
                .temperature(0.2)
                .enabled(true)
                .build();
    }
}
