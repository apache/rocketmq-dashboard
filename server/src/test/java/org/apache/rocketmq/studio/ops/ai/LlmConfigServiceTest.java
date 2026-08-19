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

import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class LlmConfigServiceTest {

    private SettingsService settingsService;
    private OpenAiCompatibleLlmClient llmClient;
    private AgentProviderRegistry agentProviders;
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
        llmClient = mock(OpenAiCompatibleLlmClient.class);
        agentProviders = mock(AgentProviderRegistry.class);
        llmConfigService = new LlmConfigService(
                settingsService, llmClient, agentProviders, new LlmProperties());
    }

    @Test
    void getConfigShouldMapGeneralSettingsToLlmConfig() {
        LlmConfigVO config = llmConfigService.getConfig();

        assertThat(config.getProvider()).isEqualTo("openai");
        assertThat(config.getApiKey()).isEqualTo("sk-test");
        assertThat(config.getApiBase()).isEqualTo("https://api.openai.com/v1");
        assertThat(config.getModel()).isEqualTo("gpt-4o");
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isReady()).isTrue();
    }

    @Test
    void envTokenShouldOverrideApiKeyAtRuntimeButNeverBePersisted() {
        LlmProperties properties = new LlmProperties();
        properties.setToken("env-token");
        LlmConfigService service = new LlmConfigService(settingsService, llmClient, agentProviders, properties);

        LlmConfigVO config = service.getConfig();
        assertThat(config.getApiKey()).isEqualTo("env-token");
        assertThat(config.isEnabled()).isTrue();

        service.saveConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .build());

        ArgumentCaptor<GeneralSettingsVO> captor = ArgumentCaptor.forClass(GeneralSettingsVO.class);
        verify(settingsService).saveGeneralSettings(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-test");

        assertThat(service.getConfig().getApiKey()).isEqualTo("env-token");
    }

    @Test
    void configToStringShouldNotExposeApiKey() {
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-secret")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .build();

        assertThat(config.toString()).doesNotContain("sk-secret");
        assertThat(config.toString()).doesNotContain("apiKey");
        assertThat(config.isApiKeyConfigured()).isTrue();
    }

    @Test
    void readyShouldRequireApiKeyForRemoteProviders() {
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("openai")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isFalse();
    }

    @Test
    void readyShouldAllowOllamaWithoutApiKey() {
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("ollama")
                .apiBase("http://localhost:11434/v1")
                .model("llama3")
                .enabled(true)
                .build();

        assertThat(config.isReady()).isTrue();
    }

    @Test
    void getConfigShouldTreatSavedOllamaAsEnabledWithoutApiKey() {
        when(settingsService.getGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(45)
                .requireLogin(true)
                .llmProvider("ollama")
                .apiKey("")
                .model("llama3")
                .baseUrl("http://localhost:11434/v1")
                .build());

        LlmConfigVO config = llmConfigService.getConfig();

        assertThat(config.isEnabled()).isTrue();
        assertThat(config.isReady()).isTrue();
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
        assertThat(saved.getMaxTokens()).isEqualTo(8192);
        assertThat(saved.getTemperature()).isEqualTo(0.2);
        assertThat(llmConfigService.getConfig().getProvider()).isEqualTo("deepseek");
        assertThat(llmConfigService.getConfig().getMaxTokens()).isEqualTo(8192);
        assertThat(llmConfigService.getConfig().getTemperature()).isEqualTo(0.2);
    }

    @Test
    void saveConfigShouldPersistProviderSpecificFieldsAcrossServiceRestart() {
        llmConfigService.saveConfig(LlmConfigVO.builder()
                .provider("azure")
                .apiKey("azure-key")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(4096)
                .temperature(0.7)
                .enabled(true)
                .deploymentName("production-gpt")
                .apiVersion("2024-06-01")
                .awsRegion("eu-west-1")
                .build());

        ArgumentCaptor<GeneralSettingsVO> captor = ArgumentCaptor.forClass(GeneralSettingsVO.class);
        verify(settingsService).saveGeneralSettings(captor.capture());
        GeneralSettingsVO persisted = captor.getValue();
        assertThat(persisted.getDeploymentName()).isEqualTo("production-gpt");
        assertThat(persisted.getApiVersion()).isEqualTo("2024-06-01");
        assertThat(persisted.getAwsRegion()).isEqualTo("eu-west-1");

        when(settingsService.getGeneralSettings()).thenReturn(persisted);
        LlmConfigVO reloaded = new LlmConfigService(settingsService, llmClient, agentProviders, new LlmProperties()).getConfig();
        assertThat(reloaded.getDeploymentName()).isEqualTo("production-gpt");
        assertThat(reloaded.getApiVersion()).isEqualTo("2024-06-01");
        assertThat(reloaded.getAwsRegion()).isEqualTo("eu-west-1");
    }

    @Test
    void saveConfigShouldKeepCurrentConfigWhenPersistenceFails() {
        doThrow(new IllegalStateException("persistence failed"))
                .when(settingsService).saveGeneralSettings(any(GeneralSettingsVO.class));

        assertThatThrownBy(() -> llmConfigService.saveConfig(LlmConfigVO.builder()
                .provider("deepseek")
                .apiKey("sk-deepseek")
                .apiBase("https://api.deepseek.com/v1")
                .model("deepseek-chat")
                .maxTokens(8192)
                .temperature(0.2)
                .enabled(true)
                .build()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("persistence failed");

        assertThat(llmConfigService.getConfig().getProvider()).isEqualTo("openai");
        assertThat(llmConfigService.getConfig().getModel()).isEqualTo("gpt-4o");
    }

    @Test
    void saveConfigShouldPreserveStoredApiKeyWhenApiKeyIsOmitted() {
        LlmConfigVO config = LlmConfigVO.builder()
                .provider("deepseek")
                .apiBase("https://api.deepseek.com/v1")
                .model("deepseek-chat")
                .maxTokens(8192)
                .temperature(0.2)
                .enabled(true)
                .build();

        llmConfigService.saveConfig(config);

        ArgumentCaptor<GeneralSettingsVO> captor = ArgumentCaptor.forClass(GeneralSettingsVO.class);
        verify(settingsService).saveGeneralSettings(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-test");
    }

    @Test
    void saveConfigShouldNormalizeChatCompletionsEndpointToApiBase() {
        llmConfigService.saveConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .apiBase(" https://api.openai.com/v1/chat/completions/ ")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(1.0)
                .enabled(true)
                .build());

        ArgumentCaptor<GeneralSettingsVO> captor = ArgumentCaptor.forClass(GeneralSettingsVO.class);
        verify(settingsService).saveGeneralSettings(captor.capture());
        assertThat(captor.getValue().getBaseUrl()).isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void saveConfigShouldRejectInvalidApiBase() {
        assertThatThrownBy(() -> llmConfigService.saveConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .apiBase("ftp://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(1.0)
                .enabled(true)
                .build()))
                .isInstanceOf(LlmGatewayException.class)
                .hasMessage("LLM API base URL is invalid")
                .satisfies(exception -> {
                    LlmGatewayException gatewayException = (LlmGatewayException) exception;
                    assertThat(gatewayException.getStatusCode()).isEqualTo(400);
                    assertThat(gatewayException.getCode()).isEqualTo("llm.config.invalid_api_base");
                });
    }

    @Test
    void testConfigShouldRejectMissingRequiredApiKey() {
        when(settingsService.getGeneralSettings()).thenReturn(GeneralSettingsVO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(45)
                .requireLogin(true)
                .llmProvider("openai")
                .apiKey("")
                .model("gpt-4o")
                .baseUrl("https://api.openai.com/v1")
                .build());

        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .engine("http")
                .apiKey("")
                .model("gpt-4o")
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getErrMsg()).isEqualTo("LLM API key is required");
        assertThat(result.getCode()).isEqualTo("llm.config.missing_api_key");
        assertThat(result.getHint()).contains("provider openai");
    }

    @Test
    void testConfigShouldAllowOllamaWithoutApiKey() {
        when(llmClient.supports(any())).thenReturn(true);

        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("ollama")
                .engine("http")
                .apiBase("http://localhost:11434/v1")
                .model("llama3")
                .build());

        assertThat(result.getStatus()).isZero();
        assertThat(result.getMsg()).isEqualTo("Connection successful");
        assertThat(result.getModels()).isEmpty();
    }

    @Test
    void testConfigShouldRejectHttpProvidersUnsupportedByRuntimeGateway() {
        LlmOperationResultVO azure = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("azure")
                .engine("http")
                .apiKey("azure-key")
                .apiBase("https://example.openai.azure.com")
                .deploymentName("production-gpt")
                .model("gpt-4o")
                .build());
        LlmOperationResultVO bedrock = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("bedrock")
                .engine("http")
                .apiKey("bedrock-key")
                .apiBase("https://bedrock-runtime.us-east-1.amazonaws.com")
                .model("anthropic.claude-3-sonnet")
                .build());

        assertThat(azure.getStatus()).isEqualTo(1);
        assertThat(azure.getCode()).isEqualTo("llm.config.unsupported_provider");
        assertThat(bedrock.getStatus()).isEqualTo(1);
        assertThat(bedrock.getCode()).isEqualTo("llm.config.unsupported_provider");
        verify(llmClient, never()).listModels(any());
    }

    @Test
    void testConfigShouldProbeProviderModelsWithStoredApiKey() {
        when(llmClient.supports(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(llmClient.listModels(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new LlmModelItemVO("gpt-4o", "GPT-4o")));

        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .engine("http")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isZero();
        assertThat(result.getMsg()).isEqualTo("Connection successful");
        ArgumentCaptor<LlmConfigVO> captor = ArgumentCaptor.forClass(LlmConfigVO.class);
        verify(llmClient).listModels(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("sk-test");
    }

    @Test
    void testConfigShouldPreferEnvironmentTokenOverStoredApiKey() {
        LlmProperties properties = new LlmProperties();
        properties.setToken("env-token");
        LlmConfigService service = new LlmConfigService(settingsService, llmClient, agentProviders, properties);
        when(llmClient.supports(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(llmClient.listModels(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new LlmModelItemVO("gpt-4o", "GPT-4o")));

        LlmOperationResultVO result = service.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .engine(LlmConfigVO.ENGINE_HTTP)
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isZero();
        ArgumentCaptor<LlmConfigVO> captor = ArgumentCaptor.forClass(LlmConfigVO.class);
        verify(llmClient).listModels(captor.capture());
        assertThat(captor.getValue().getApiKey()).isEqualTo("env-token");
        verify(settingsService, never()).getGeneralSettings();
    }

    @Test
    void testConfigShouldReturnProviderProbeFailure() {
        when(llmClient.supports(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(llmClient.listModels(org.mockito.ArgumentMatchers.any())).thenThrow(new LlmGatewayException(
                401,
                "llm.provider.upstream_error",
                "LLM provider request failed with status 401",
                "Check the provider credentials, model name, and account quota."));

        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .engine("http")
                .apiKey("sk-bad")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getCode()).isEqualTo("llm.provider.upstream_error");
        assertThat(result.getErrMsg()).contains("401");
        assertThat(result.getHint()).contains("credentials");
    }

    @Test
    void testConfigShouldNotProbeHttpModelsForCliEngine() {
        AgentProvider provider = mock(AgentProvider.class);
        when(agentProviders.forEngine("claude-code")).thenReturn(provider);
        when(provider.available()).thenReturn(true);

        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .engine("claude-code")
                .apiBase("https://api.openai.com/v1")
                .model("claude-sonnet-4")
                .maxTokens(2048)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isZero();
        assertThat(result.getMsg()).isEqualTo("CLI is available");
        verify(agentProviders).forEngine("claude-code");
        verifyNoInteractions(llmClient);
    }

    @Test
    void testConfigShouldReportMissingCliEngine() {
        AgentProvider provider = mock(AgentProvider.class);
        when(agentProviders.forEngine("qoder")).thenReturn(provider);
        when(provider.available()).thenReturn(false);

        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .engine("qoder")
                .apiBase("https://api.openai.com/v1")
                .model("qoder-model")
                .maxTokens(2048)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getCode()).isEqualTo("llm.provider.cli_missing");
        assertThat(result.getErrMsg()).contains("qoder");
        assertThat(result.getHint()).contains("Install").contains("HTTP engine");
        verifyNoInteractions(llmClient);
    }

    @Test
    void testConfigShouldRejectInvalidApiBase() {
        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .apiBase("openai.local/v1")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getCode()).isEqualTo("llm.config.invalid_api_base");
        assertThat(result.getHint()).contains("https://api.openai.com/v1");
    }

    @Test
    void testConfigShouldRejectOutOfRangeMaxTokens() {
        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(200_001)
                .temperature(1.0)
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getCode()).isEqualTo("llm.config.invalid_max_tokens");
        assertThat(result.getHint()).contains("200000");
    }

    @Test
    void testConfigShouldRejectOutOfRangeTemperature() {
        LlmOperationResultVO result = llmConfigService.testConfig(LlmConfigVO.builder()
                .provider("openai")
                .apiKey("sk-test")
                .apiBase("https://api.openai.com/v1")
                .model("gpt-4o")
                .maxTokens(2048)
                .temperature(2.1)
                .build());

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getCode()).isEqualTo("llm.config.invalid_temperature");
        assertThat(result.getHint()).contains("between 0 and 2");
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
        assertThat(result.getData()).extracting("id").contains("qwen3.8-max", "qwen3.7-plus");
    }

    @Test
    void listModelsShouldPreferProviderModelsWhenAvailable() {
        when(llmClient.supports(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(llmClient.listModels(org.mockito.ArgumentMatchers.any())).thenReturn(List.of(
                new LlmModelItemVO("provider-model-a", "Provider Model A"),
                new LlmModelItemVO("provider-model-b", "Provider Model B")));

        LlmModelsResultVO result = llmConfigService.listModels();

        assertThat(result.getStatus()).isZero();
        assertThat(result.getData()).extracting("id")
                .containsExactly("provider-model-a", "provider-model-b");
        assertThat(result.getSource()).isEqualTo(LlmModelsResultVO.SOURCE_PROVIDER);
    }

    @Test
    void listModelsShouldFallbackToBuiltInModelsWhenProviderModelListingFails() {
        when(llmClient.supports(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(llmClient.listModels(org.mockito.ArgumentMatchers.any())).thenThrow(new LlmGatewayException(
                502,
                "llm.provider.io_error",
                "Failed to list LLM provider models",
                "Check the provider endpoint."));

        LlmModelsResultVO result = llmConfigService.listModels();

        assertThat(result.getStatus()).isZero();
        assertThat(result.getData()).extracting("id").contains("gpt-4o", "gpt-4");
        assertThat(result.getSource()).isEqualTo(LlmModelsResultVO.SOURCE_FALLBACK);
        assertThat(result.getWarningCode()).isEqualTo("llm.provider.io_error");
        assertThat(result.getWarning()).contains("Failed to list LLM provider models");
        assertThat(result.getHint()).contains("provider endpoint");
    }

    @Test
    void listModelsShouldNotBlockConcurrentConfigReadsOrWrites() throws Exception {
        CountDownLatch listingStarted = new CountDownLatch(1);
        CountDownLatch releaseListing = new CountDownLatch(1);
        when(llmClient.supports(any())).thenReturn(true);
        when(llmClient.listModels(any())).thenAnswer(invocation -> {
            LlmConfigVO requestedConfig = invocation.getArgument(0);
            assertThat(requestedConfig.getProvider()).isEqualTo("openai");
            listingStarted.countDown();
            assertThat(releaseListing.await(5, TimeUnit.SECONDS)).isTrue();
            return List.of(new LlmModelItemVO("provider-model", "Provider Model"));
        });

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            Future<LlmModelsResultVO> listing = executor.submit(llmConfigService::listModels);
            assertThat(listingStarted.await(5, TimeUnit.SECONDS)).isTrue();
            Future<LlmConfigVO> configRead = executor.submit(llmConfigService::getConfig);

            try {
                assertThat(configRead.get(500, TimeUnit.MILLISECONDS).getProvider()).isEqualTo("openai");
                Future<?> configWrite = executor.submit(() -> llmConfigService.saveConfig(LlmConfigVO.builder()
                        .provider("deepseek")
                        .apiKey("sk-deepseek")
                        .apiBase("https://api.deepseek.com/v1")
                        .model("deepseek-chat")
                        .maxTokens(8192)
                        .temperature(0.2)
                        .enabled(true)
                        .build()));
                configWrite.get(500, TimeUnit.MILLISECONDS);
                assertThat(llmConfigService.getConfig().getProvider()).isEqualTo("deepseek");
                assertThat(listing.isDone()).isFalse();
            } finally {
                releaseListing.countDown();
            }
            assertThat(listing.get(5, TimeUnit.SECONDS).getSource())
                    .isEqualTo(LlmModelsResultVO.SOURCE_PROVIDER);
        }
    }
}
