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
import org.springframework.stereotype.Component;

/**
 * Resolves the effective LLM configuration from persisted settings, optionally
 * overridden by an explicit (possibly unsaved) configuration such as the one sent
 * by the connection-test endpoint.
 */
@Component
public class LlmSettingsResolver {

    private final SettingsService settingsService;

    public LlmSettingsResolver(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    LlmConfig resolve(AiLlmConfigDTO override) {
        GeneralSettingsVO s = settingsService.getGeneralSettings();
        LlmConfig cfg = new LlmConfig();
        cfg.provider = firstNonBlank(override != null ? override.getProvider() : null, s.getLlmProvider(), "openai");
        cfg.apiKey = firstNonBlank(override != null ? override.getApiKey() : null, s.getApiKey(), "");
        cfg.model = firstNonBlank(override != null ? override.getModel() : null, s.getModel(), "gpt-4");
        cfg.baseUrl = firstNonBlank(override != null ? override.getBaseUrl() : null, s.getBaseUrl(), "");
        cfg.configured = cfg.apiKey != null && !cfg.apiKey.isBlank();
        return cfg;
    }

    private String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return "";
    }
}
