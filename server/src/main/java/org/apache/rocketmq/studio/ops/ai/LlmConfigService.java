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

import org.springframework.util.StringUtils;

import org.apache.rocketmq.studio.common.util.UrlHostGuard;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.apache.rocketmq.studio.settings.SettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@RequiredArgsConstructor
@EnableConfigurationProperties(LlmProperties.class)
@Slf4j
public class LlmConfigService {

    private static final String OPENAI = "openai";
    private static final String ANTHROPIC = "anthropic";
    private static final String DEFAULT_PROVIDER = "tongyi";
    private static final String CHAT_COMPLETIONS_PATH = "/chat/completions";
    private static final int DEFAULT_MAX_TOKENS = 4096;
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int MAX_TOKENS_LIMIT = 200_000;
    private static final Map<String, List<LlmModelItemVO>> PROVIDER_MODELS = Map.of(
            OPENAI, List.of(
                    new LlmModelItemVO("gpt-5.6-sol", "GPT-5.6 Sol"),
                    new LlmModelItemVO("gpt-5.6-terra", "GPT-5.6 Terra"),
                    new LlmModelItemVO("gpt-5.6-luna", "GPT-5.6 Luna")),
            "azure", List.of(
                    new LlmModelItemVO("gpt-5.6-sol", "GPT-5.6 Sol"),
                    new LlmModelItemVO("gpt-5.6-terra", "GPT-5.6 Terra"),
                    new LlmModelItemVO("gpt-5.6-luna", "GPT-5.6 Luna")),
            ANTHROPIC, List.of(
                    new LlmModelItemVO("claude-fable-5", "Claude Fable 5"),
                    new LlmModelItemVO("claude-opus-5", "Claude Opus 5"),
                    new LlmModelItemVO("claude-opus-4-8", "Claude Opus 4.8"),
                    new LlmModelItemVO("claude-opus-4-7", "Claude Opus 4.7"),
                    new LlmModelItemVO("claude-sonnet-5", "Claude Sonnet 5"),
                    new LlmModelItemVO("claude-sonnet-4-6", "Claude Sonnet 4.6"),
                    new LlmModelItemVO("claude-haiku-4-5", "Claude Haiku 4.5")),
            "deepseek", List.of(
                    new LlmModelItemVO("deepseek-chat", "DeepSeek Chat"),
                    new LlmModelItemVO("deepseek-reasoner", "DeepSeek Reasoner")),
            "tongyi", List.of(
                    new LlmModelItemVO("qwen3.8-max", "qwen3.8-max"),
                    new LlmModelItemVO("qwen3.7-max", "qwen3.7-max"),
                    new LlmModelItemVO("qwen3.7-plus", "qwen3.7-plus"),
                    new LlmModelItemVO("deepseek-v4-pro", "deepseek-v4-pro"),
                    new LlmModelItemVO("deepseek-v4-flash", "deepseek-v4-flash"),
                    new LlmModelItemVO("MiniMax-M2.5", "MiniMax-M2.5"),
                    new LlmModelItemVO("glm-5.2", "glm-5.2")),
            "ollama", List.of(
                    new LlmModelItemVO("llama3", "Llama 3"),
                    new LlmModelItemVO("mistral", "Mistral"),
                    new LlmModelItemVO("qwen2.5", "Qwen 2.5")),
            "bedrock", List.of(
                    new LlmModelItemVO("anthropic.claude-fable-5", "Claude Fable 5"),
                    new LlmModelItemVO("anthropic.claude-opus-5", "Claude Opus 5"),
                    new LlmModelItemVO("anthropic.claude-opus-4-8", "Claude Opus 4.8"),
                    new LlmModelItemVO("anthropic.claude-sonnet-5", "Claude Sonnet 5"),
                    new LlmModelItemVO("anthropic.claude-haiku-4-5", "Claude Haiku 4.5"),
                    new LlmModelItemVO("meta.llama3-70b", "Llama 3 70B")));

    private final SettingsService settingsService;
    private final OpenAiCompatibleLlmClient llmClient;
    private final AgentProviderRegistry agentProviders;
    private final LlmProperties llmProperties;
    private LlmConfigVO overrides;

    public synchronized LlmConfigVO getConfig() {
        LlmConfigVO config = overrides != null
                ? copy(overrides)
                : fromGeneralSettings(settingsService.getGeneralSettings());
        String token = envToken();
        if (StringUtils.hasText(token)) {
            config.setApiKey(token.trim());
            config.setEnabled(true);
        }
        return config;
    }

    public synchronized void saveConfig(LlmConfigVO config) {
        LlmConfigVO normalized = normalizeWithStoredApiKey(config);
        LlmOperationResultVO validation = validate(normalized);
        if (validation.getStatus() != 0) {
            throw new LlmGatewayException(400, validation.getCode(), validation.getErrMsg(), validation.getHint());
        }
        GeneralSettingsVO current = settingsService.getGeneralSettings();
        // The env-injected token is authoritative at runtime but must never be persisted.
        String persistedApiKey = !StringUtils.hasText(envToken())
                ? normalized.getApiKey()
                : defaultString(current.getApiKey(), "");
        GeneralSettingsVO updated = GeneralSettingsVO.builder()
                .theme(current.getTheme())
                .compact(current.isCompact())
                .desktopNotify(current.isDesktopNotify())
                .notifySound(current.isNotifySound())
                .sessionTimeout(current.getSessionTimeout())
                .requireLogin(current.isRequireLogin())
                .llmProvider(normalized.getProvider())
                .llmEngine(normalized.getEngine())
                .apiKey(persistedApiKey)
                .model(normalized.getModel())
                .baseUrl(normalized.getApiBase())
                .deploymentName(normalized.getDeploymentName())
                .apiVersion(normalized.getApiVersion())
                .awsRegion(normalized.getAwsRegion())
                .maxTokens(normalized.getMaxTokens())
                .temperature(normalized.getTemperature())
                .build();
        LlmConfigVO nextOverrides = copy(normalized);
        settingsService.saveGeneralSettings(updated);
        overrides = nextOverrides;
    }

    public LlmOperationResultVO testConfig(LlmConfigVO config) {
        LlmConfigVO normalized = normalizeWithStoredApiKey(config);
        LlmOperationResultVO validation = validate(normalized);
        if (validation.getStatus() != 0) {
            return validation;
        }
        if (!LlmConfigVO.ENGINE_HTTP.equalsIgnoreCase(normalized.normalizeEngine())) {
            return testCliEngine(normalized.normalizeEngine());
        }
        if (!llmClient.supports(normalized)) {
            return LlmOperationResultVO.failure(
                    "llm.config.unsupported_provider",
                    "LLM provider is not supported by the OpenAI-compatible gateway",
                    "Use one of: openai, deepseek, tongyi, ollama.");
        }
        try {
            List<LlmModelItemVO> models = llmClient.listModels(normalized);
            return LlmOperationResultVO.successWithModels("Connection successful", models);
        } catch (LlmGatewayException exception) {
            return LlmOperationResultVO.failure(exception.getCode(), exception.getMessage(), exception.getHint());
        }
    }

    private LlmOperationResultVO testCliEngine(String engine) {
        try {
            AgentProvider provider = agentProviders.forEngine(engine);
            if (provider.available()) {
                return LlmOperationResultVO.success("CLI is available");
            }
            return LlmOperationResultVO.failure(
                    "llm.provider.cli_missing",
                    "Agent CLI is not available for engine: " + engine,
                    "Install the selected CLI in the server runtime or select the HTTP engine.");
        } catch (LlmGatewayException exception) {
            return LlmOperationResultVO.failure(
                    exception.getCode(), exception.getMessage(), exception.getHint());
        }
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
        boolean keyRequired = !"ollama".equals(provider)
                && LlmConfigVO.ENGINE_HTTP.equalsIgnoreCase(normalized.normalizeEngine());
        if (keyRequired && !StringUtils.hasText(normalized.getApiKey())) {
            return LlmOperationResultVO.failure(
                    "llm.config.missing_api_key",
                    "LLM API key is required",
                    "Configure an API key for provider " + provider + ", or select ollama for a local provider.");
        }
        if ("azure".equals(provider) && !StringUtils.hasText(normalized.getDeploymentName())) {
            return LlmOperationResultVO.failure(
                    "llm.config.missing_deployment",
                    "Azure OpenAI deployment name is required",
                    "Set the Azure deployment name that maps to the selected model.");
        }
        if (!StringUtils.hasText(normalized.getModel())) {
            return LlmOperationResultVO.failure(
                    "llm.config.missing_model",
                    "LLM model is required",
                    "Select or enter a model before testing the connection.");
        }
        return LlmOperationResultVO.success("Configuration accepted");
    }

    public LlmModelsResultVO listModels() {
        return listModels(getConfig());
    }

    private LlmModelsResultVO listModels(LlmConfigVO config) {
        String provider = config.getProvider();
        // The token-plan gateway model set is curated locally; do not query the gateway.
        if (DEFAULT_PROVIDER.equals(provider)) {
            return new LlmModelsResultVO(0, PROVIDER_MODELS.get(DEFAULT_PROVIDER),
                    LlmModelsResultVO.SOURCE_BUILTIN, null, null, null);
        }
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
        String provider = normalizeProvider(settings.getLlmProvider());
        // Token injected via RMQ_LLM_TOKEN takes precedence over the key saved in settings.
        String apiKey = defaultString(envToken(), defaultString(settings.getApiKey(), ""));
        String apiBase = normalizeApiBase(defaultString(settings.getBaseUrl(), defaultApiBase(provider)));
        String model = defaultString(settings.getModel(), defaultModel(provider));
        return LlmConfigVO.builder()
                .provider(provider)
                .engine(normalizeEngine(settings.getLlmEngine()))
                .apiKey(apiKey)
                .apiBase(apiBase)
                .model(model)
                .maxTokens(settings.getMaxTokens() != null ? settings.getMaxTokens() : DEFAULT_MAX_TOKENS)
                .temperature(settings.getTemperature() != null ? settings.getTemperature() : DEFAULT_TEMPERATURE)
                .enabled(!requiresApiKey(provider) || !!StringUtils.hasText(apiKey))
                .deploymentName(defaultString(settings.getDeploymentName(), ""))
                .apiVersion(defaultString(settings.getApiVersion(), "2024-02-15-preview"))
                .awsRegion(defaultString(settings.getAwsRegion(), "us-east-1"))
                .build();
    }

    private String envToken() {
        return llmProperties == null ? null : llmProperties.getToken();
    }

    private LlmConfigVO normalize(LlmConfigVO config) {
        String provider = normalizeProvider(config == null ? null : config.getProvider());
        return LlmConfigVO.builder()
                .provider(provider)
                .engine(normalizeEngine(config == null ? null : config.getEngine()))
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

    private LlmConfigVO copy(LlmConfigVO config) {
        return normalize(config);
    }

    private LlmConfigVO normalizeWithStoredApiKey(LlmConfigVO config) {
        LlmConfigVO normalized = normalize(config);
        if (!requiresApiKey(normalized.getProvider())) {
            normalized.setApiKey("");
            return normalized;
        }
        if (!StringUtils.hasText(normalized.getApiKey())) {
            // The env-injected token is authoritative at runtime but must never be persisted;
            // otherwise fall back to the key stored in settings.
            String effectiveKey = envToken();
            if (!StringUtils.hasText(effectiveKey)) {
                effectiveKey = settingsService.getGeneralSettings().getApiKey();
            }
            normalized.setApiKey(defaultString(effectiveKey, ""));
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
        String normalized = defaultString(provider, DEFAULT_PROVIDER).toLowerCase(Locale.ROOT);
        return PROVIDER_MODELS.containsKey(normalized) ? normalized : DEFAULT_PROVIDER;
    }

    private String normalizeEngine(String engine) {
        String normalized = defaultString(engine, LlmConfigVO.ENGINE_CLAUDE_CODE).toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case LlmConfigVO.ENGINE_HTTP, LlmConfigVO.ENGINE_CLAUDE_CODE, LlmConfigVO.ENGINE_QODER -> normalized;
            default -> LlmConfigVO.ENGINE_HTTP;
        };
    }

    private String defaultModel(String provider) {
        return PROVIDER_MODELS.getOrDefault(provider, PROVIDER_MODELS.get(OPENAI)).get(0).getId();
    }

    private String defaultApiBase(String provider) {
        return switch (provider) {
            case "deepseek" -> "https://api.deepseek.com/v1";
            case "tongyi" -> "https://dashscope.aliyuncs.com/compatible-mode/v1";
            case ANTHROPIC -> "https://api.anthropic.com";
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
            if (!("http".equals(scheme) || "https".equals(scheme)) || !StringUtils.hasText(uri.getHost())
                    || apiBase.endsWith(CHAT_COMPLETIONS_PATH)) {
                return false;
            }
            // SSRF guard on both the save and test paths (validate() runs for each). Loopback is
            // allowed because a local ollama gateway is a supported provider, but link-local and
            // cloud-metadata addresses (169.254.x.x) are rejected.
            return UrlHostGuard.isAllowedHost(uri.getHost(), true);
        } catch (URISyntaxException exception) {
            return false;
        }
    }

    private String defaultString(String value, String fallback) {
        return !StringUtils.hasText(value) ? fallback : value.trim();
    }

}
