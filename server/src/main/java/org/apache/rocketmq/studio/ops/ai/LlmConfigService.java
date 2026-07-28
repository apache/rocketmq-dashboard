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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmConfigService {

    private static final String OPENAI = "openai";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int MAX_TOKENS_LIMIT = 200_000;
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
    private final OpenAiCompatibleLlmClient llmClient;
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
        LlmOperationResultVO validation = validate(effective);
        if (validation.getStatus() != 0) {
            throw new LlmGatewayException(400, validation.getCode(), validation.getErrMsg(), validation.getHint());
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
        LlmOperationResultVO validation = validate(normalized);
        if (validation.getStatus() != 0) {
            return validation;
        }
        if (llmClient.supports(normalized)) {
            try {
                llmClient.listModels(normalized);
            } catch (LlmGatewayException exception) {
                return LlmOperationResultVO.failure(exception.getCode(), exception.getMessage(), exception.getHint());
            }
        }
        return LlmOperationResultVO.success("Connection successful");
    }

    private LlmOperationResultVO validate(LlmConfigVO normalized) {
        String provider = normalized.getProvider();
        if (!isValidApiBase(normalized.getApiBase())) {
            return LlmOperationResultVO.failure(
                    "llm.config.invalid_api_base",
                    "LLM API base URL is invalid",
                    "Use an http or https base URL such as https://api.openai.com/v1.");
        }
        if (normalized.getMaxTokens() > MAX_TOKENS_LIMIT) {
            return LlmOperationResultVO.failure(
                    "llm.config.invalid_max_tokens",
                    "LLM max tokens is out of range",
                    "Set maxTokens to a value no greater than " + MAX_TOKENS_LIMIT + ".");
        }
        if (!Double.isFinite(normalized.getTemperature()) || normalized.getTemperature() < 0
                || normalized.getTemperature() > 2) {
            return LlmOperationResultVO.failure(
                    "llm.config.invalid_temperature",
                    "LLM temperature is out of range",
                    "Set temperature to a value between 0 and 2.");
        }
        boolean keyRequired = !"ollama".equals(provider);
        if (keyRequired && isBlank(normalized.getApiKey())) {
            return LlmOperationResultVO.failure(
                    "llm.config.missing_api_key",
                    "LLM API key is required",
                    "Configure an API key for provider " + provider + ", or select ollama for a local provider.");
        }
        if ("azure".equals(provider) && isBlank(normalized.getDeploymentName())) {
            return LlmOperationResultVO.failure(
                    "llm.config.missing_deployment",
                    "Azure OpenAI deployment name is required",
                    "Set the Azure deployment name that maps to the selected model.");
        }
        if (isBlank(normalized.getModel())) {
            return LlmOperationResultVO.failure(
                    "llm.config.missing_model",
                    "LLM model is required",
                    "Select or enter a model before testing the connection.");
        }
        return LlmOperationResultVO.success("Configuration accepted");
    }

    public synchronized LlmModelsResultVO listModels() {
        LlmConfigVO config = getConfig();
        String provider = config.getProvider();
        if (config.isEnabled() && llmClient.supports(config)) {
            try {
                List<LlmModelItemVO> models = llmClient.listModels(config);
                if (!models.isEmpty()) {
                    return new LlmModelsResultVO(0, models, LlmModelsResultVO.SOURCE_PROVIDER,
                            null, null, null);
                }
            } catch (LlmGatewayException exception) {
                log.debug("Falling back to built-in LLM model list for provider {}: {}", provider,
                        exception.getMessage());
                return fallbackModels(provider, exception);
            }
        }
        List<LlmModelItemVO> models = PROVIDER_MODELS.getOrDefault(provider, PROVIDER_MODELS.get(OPENAI));
        return new LlmModelsResultVO(0, models, LlmModelsResultVO.SOURCE_BUILTIN, null, null, null);
    }

    private LlmConfigVO fromGeneralSettings(GeneralSettingsVO settings) {
        String provider = defaultString(settings.getLlmProvider(), OPENAI);
        return LlmConfigVO.builder()
                .provider(provider)
                .apiKey(defaultString(settings.getApiKey(), ""))
                .apiBase(normalizeApiBase(defaultString(settings.getBaseUrl(), defaultApiBase(provider))))
                .model(defaultString(settings.getModel(), defaultModel(provider)))
                .maxTokens(DEFAULT_MAX_TOKENS)
                .temperature(DEFAULT_TEMPERATURE)
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
                .apiBase(normalizeApiBase(defaultString(config == null ? null : config.getApiBase(),
                        defaultApiBase(provider))))
                .model(defaultString(config == null ? null : config.getModel(), defaultModel(provider)))
                .maxTokens(config == null || config.getMaxTokens() <= 0 ? DEFAULT_MAX_TOKENS : config.getMaxTokens())
                .temperature(config == null ? DEFAULT_TEMPERATURE : config.getTemperature())
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

    private LlmConfigVO normalizeWithStoredApiKey(LlmConfigVO config) {
        LlmConfigVO normalized = normalize(config);
        if (!requiresApiKey(normalized.getProvider())) {
            normalized.setApiKey("");
            return normalized;
        }
        if (isBlank(normalized.getApiKey())) {
            normalized.setApiKey(defaultString(getConfig().getApiKey(), ""));
        }
        return normalized;
    }

    private LlmModelsResultVO fallbackModels(String provider, LlmGatewayException exception) {
        List<LlmModelItemVO> models = PROVIDER_MODELS.getOrDefault(provider, PROVIDER_MODELS.get(OPENAI));
        return new LlmModelsResultVO(0, models, LlmModelsResultVO.SOURCE_FALLBACK,
                exception.getMessage(), exception.getCode(), exception.getHint());
    }

    private boolean requiresApiKey(String provider) {
        return !"ollama".equals(provider);
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

    private String normalizeApiBase(String apiBase) {
        String normalized = apiBase.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith(CHAT_COMPLETIONS_PATH)) {
            normalized = normalized.substring(0, normalized.length() - CHAT_COMPLETIONS_PATH.length());
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
        }
        return normalized;
    }

    private boolean isValidApiBase(String apiBase) {
        try {
            URI uri = new URI(apiBase);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            return ("http".equals(scheme) || "https".equals(scheme)) && !isBlank(uri.getHost())
                    && !apiBase.endsWith(CHAT_COMPLETIONS_PATH);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String defaultString(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
