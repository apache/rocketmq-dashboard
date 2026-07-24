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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class LlmConfigService {

    private static final String OPENAI = "openai";
    private static final Map<String, List<LlmModelItemVO>> PROVIDER_MODELS = Map.of(
            OPENAI, List.of(
                    new LlmModelItemVO("gpt-4o", "GPT-4o"),
                    new LlmModelItemVO("gpt-4-turbo", "GPT-4 Turbo"),
                    new LlmModelItemVO("gpt-4", "GPT-4")),
            "azure", List.of(
                    new LlmModelItemVO("gpt-4o", "GPT-4o"),
                    new LlmModelItemVO("gpt-4", "GPT-4")),
            "deepseek", List.of(
                    new LlmModelItemVO("deepseek-chat", "DeepSeek Chat"),
                    new LlmModelItemVO("deepseek-reasoner", "DeepSeek Reasoner")),
            "tongyi", List.of(
                    new LlmModelItemVO("qwen-max", "Qwen Max"),
                    new LlmModelItemVO("qwen-plus", "Qwen Plus"),
                    new LlmModelItemVO("qwen-turbo", "Qwen Turbo")),
            "ollama", List.of(
                    new LlmModelItemVO("llama3", "Llama 3"),
                    new LlmModelItemVO("mistral", "Mistral"),
                    new LlmModelItemVO("qwen2.5", "Qwen 2.5")),
            "bedrock", List.of(
                    new LlmModelItemVO("anthropic.claude-3-sonnet", "Claude 3 Sonnet"),
                    new LlmModelItemVO("anthropic.claude-3-haiku", "Claude 3 Haiku"),
                    new LlmModelItemVO("meta.llama3-70b", "Llama 3 70B")));

    private final SettingsService settingsService;
    private LlmConfigVO overrides;

    public synchronized LlmConfigVO getConfig() {
        if (overrides != null) {
            return copy(overrides);
        }
        return fromGeneralSettings(settingsService.getGeneralSettings());
    }

    public synchronized void saveConfig(LlmConfigVO config) {
        LlmConfigVO normalized = normalize(config);
        GeneralSettingsVO current = settingsService.getGeneralSettings();
        boolean sameProvider = current != null
                && normalizeProvider(current.getLlmProvider()).equals(normalized.getProvider());
        boolean apiKeyOmitted = isBlank(normalized.getApiKey());
        LlmConfigVO effective = copy(normalized);
        if (apiKeyOmitted && sameProvider) {
            effective.setApiKey(defaultString(current.getApiKey(), ""));
        }
        overrides = effective;
        settingsService.saveGeneralSettings(GeneralSettingsVO.builder()
                .theme(current.getTheme())
                .compact(current.isCompact())
                .desktopNotify(current.isDesktopNotify())
                .notifySound(current.isNotifySound())
                .sessionTimeout(current.getSessionTimeout())
                .requireLogin(current.isRequireLogin())
                .llmProvider(normalized.getProvider())
                .apiKey(normalized.getApiKey())
                .clearApiKey(apiKeyOmitted && !sameProvider)
                .model(normalized.getModel())
                .baseUrl(normalized.getApiBase())
                .build());
    }

    public LlmOperationResultVO testConfig(LlmConfigVO config) {
        LlmConfigVO normalized = withStoredApiKeyIfSameProvider(normalize(config));
        String provider = normalized.getProvider();
        boolean keyRequired = !"ollama".equals(provider);
        if (keyRequired && isBlank(normalized.getApiKey())) {
            return LlmOperationResultVO.failure("API Key is required");
        }
        if ("azure".equals(provider) && isBlank(normalized.getDeploymentName())) {
            return LlmOperationResultVO.failure("Deployment name is required");
        }
        if (isBlank(normalized.getModel())) {
            return LlmOperationResultVO.failure("Model is required");
        }
        return LlmOperationResultVO.success("Configuration accepted");
    }

    public synchronized LlmModelsResultVO listModels() {
        String provider = getConfig().getProvider();
        List<LlmModelItemVO> models = PROVIDER_MODELS.getOrDefault(provider, PROVIDER_MODELS.get(OPENAI));
        return new LlmModelsResultVO(0, models);
    }

    private LlmConfigVO fromGeneralSettings(GeneralSettingsVO settings) {
        String provider = defaultString(settings.getLlmProvider(), OPENAI);
        return LlmConfigVO.builder()
                .provider(provider)
                .apiKey(defaultString(settings.getApiKey(), ""))
                .apiBase(defaultString(settings.getBaseUrl(), defaultApiBase(provider)))
                .model(defaultString(settings.getModel(), defaultModel(provider)))
                .maxTokens(4096)
                .temperature(0.7)
                .enabled(!isBlank(settings.getApiKey()))
                .apiVersion("2024-02-15-preview")
                .awsRegion("us-east-1")
                .build();
    }

    private LlmConfigVO normalize(LlmConfigVO config) {
        String provider = normalizeProvider(config == null ? null : config.getProvider());
        return LlmConfigVO.builder()
                .provider(provider)
                .apiKey(defaultString(config == null ? null : config.getApiKey(), ""))
                .apiBase(defaultString(config == null ? null : config.getApiBase(), defaultApiBase(provider)))
                .model(defaultString(config == null ? null : config.getModel(), defaultModel(provider)))
                .maxTokens(config == null || config.getMaxTokens() <= 0 ? 4096 : config.getMaxTokens())
                .temperature(config == null ? 0.7 : config.getTemperature())
                .enabled(config != null && config.isEnabled())
                .deploymentName(defaultString(config == null ? null : config.getDeploymentName(), ""))
                .apiVersion(defaultString(config == null ? null : config.getApiVersion(), "2024-02-15-preview"))
                .awsRegion(defaultString(config == null ? null : config.getAwsRegion(), "us-east-1"))
                .build();
    }

    private LlmConfigVO withStoredApiKeyIfSameProvider(LlmConfigVO config) {
        if (!isBlank(config.getApiKey())) {
            return config;
        }
        GeneralSettingsVO current = settingsService.getGeneralSettings();
        if (current == null || !normalizeProvider(current.getLlmProvider())
                .equals(config.getProvider())) {
            return config;
        }
        LlmConfigVO effective = copy(config);
        effective.setApiKey(defaultString(current.getApiKey(), ""));
        return effective;
    }

    private LlmConfigVO copy(LlmConfigVO config) {
        return normalize(config);
    }

    private String normalizeProvider(String provider) {
        String normalized = defaultString(provider, OPENAI).toLowerCase(Locale.ROOT);
        return PROVIDER_MODELS.containsKey(normalized) ? normalized : OPENAI;
    }

    private String defaultModel(String provider) {
        return PROVIDER_MODELS.getOrDefault(provider, PROVIDER_MODELS.get(OPENAI)).get(0).getId();
    }

    private String defaultApiBase(String provider) {
        return switch (provider) {
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "tongyi" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case "ollama" -> "http://localhost:11434/v1";
            default -> "https://api.openai.com/v1";
        };
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
