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

package com.rocketmq.studio.ops.ai;

import com.rocketmq.studio.settings.GeneralSettingsVO;
import com.rocketmq.studio.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LlmConfigServiceTest {

    private SettingsService settingsService;
    private LlmConfigService llmConfigService;

    @BeforeEach
    void setUp() {
        settingsService = mock(SettingsService.class);
        when(settingsService.getGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(45)
                .requireLogin(true)
                .llmProvider("openai")
                .apiKey("sk-test")
                .model("gpt-4o")
                .baseUrl("https://api.openai.com/v1")
                .build());
        llmConfigService = new LlmConfigService(settingsService);
    }

    @Test
    void getConfigShouldMapGeneralSettingsToLlmConfig() {
        LlmConfigVO config = llmConfigService.getConfig();

        assertThat(config.getProvider()).isEqualTo("openai");
        assertThat(config.getApiKey()).isEqualTo("sk-test");
        assertThat(config.getApiBase()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.getModel()).isEqualTo("gpt-4o");
        assertThat(config.isEnabled()).isTrue();
    }

    @Test
    void saveConfigShouldPreserveGeneralSettingsAndStoreLlmFields() {
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("deepseek")
                .apiKey("sk-deepseek")
                .apiBase("https://api.deepseek.com/v1")
                .model("deepseek-chat")
                .maxTokens(8192)
                .temperature(0.2)
                .enabled(true)
                .build();

        llmConfigService.saveConfig(config);

        ArgumentCaptor<GeneralSettingsVO> captor = ArgumentCaptor.forClass(GeneralSettingsVO.class);
        verify(settingsService).saveGeneralSettings(captor.capture());
        GeneralSettingsVO saved = captor.getValue();
        assertThat(saved.getTheme()).isEqualTo("dark");
        assertThat(saved.isCompact()).isTrue();
        assertThat(saved.getLlmProvider()).isEqualTo("deepseek");
        assertThat(saved.getApiKey()).isEqualTo("sk-deepseek");
        assertThat(saved.getModel()).isEqualTo("deepseek-chat");
        assertThat(saved.getBaseUrl()).isEqualTo("https://api.deepseek.com/v1");
        assertThat(llmConfigService.getConfig().getProvider()).isEqualTo("deepseek");
    }

    @Test
    void testConfigShouldRejectMissingRequiredApiKey() {
        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("")
                .model("gpt-4o")
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getErrMsg()).isEqualTo("API Key is required");
    }

    @Test
    void testConfigShouldAllowOllamaWithoutApiKey() {
        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("ollama")
                .apiBase("http://localhost:11434/v1")
                .model("llama3")
                .build());

        assertThat(result.getStatus()).isZero();
        assertThat(result.getMsg()).isEqualTo("Configuration accepted");
    }

    @Test
    void listModelsShouldUseSavedProvider() {
        llmConfigService.saveConfig(LlmConfigVO.builder()
                .provider("tongyi")
                .apiKey("dashscope-key")
                .model("qwen-plus")
                .enabled(true)
                .build());

        LlmModelsResultVO result = llmConfigService.listModels();

        assertThat(result.getStatus()).isZero();
        assertThat(result.getData()).extracting("id").contains("qwen-max", "qwen-plus");
    }
}
