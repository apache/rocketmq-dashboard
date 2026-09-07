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

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.StringUtils;

import java.util.Locale;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LlmConfigVO {
    public static final String ENGINE_HTTP = "http";
    public static final String ENGINE_CLAUDE_CODE = "claude-code";
    public static final String ENGINE_QODER = "qoder";

    private String provider;
    private String engine;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @ToString.Exclude
    private String apiKey;
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private boolean clearApiKey;
    private String apiBase;
    private String model;
    private int maxTokens;
    private double temperature;
    private boolean enabled;
    private String deploymentName;
    private String apiVersion;
    private String awsRegion;

    @JsonProperty(value = "apiKeyConfigured", access = JsonProperty.Access.READ_ONLY)
    public boolean isApiKeyConfigured() {
        return StringUtils.hasText(apiKey);
    }

    @JsonProperty(value = "ready", access = JsonProperty.Access.READ_ONLY)
    public boolean isReady() {
        if (!enabled || !StringUtils.hasText(model)) {
            return false;
        }
        if (!ENGINE_HTTP.equalsIgnoreCase(normalizeEngine())) {
            // CLI engines authenticate through the subprocess environment.
            return true;
        }
        boolean keyRequired = !"ollama".equalsIgnoreCase(provider);
        return StringUtils.hasText(apiBase) && (!keyRequired || StringUtils.hasText(apiKey));
    }

    public String normalizeEngine() {
        return StringUtils.hasText(engine) ? engine.trim().toLowerCase(Locale.ROOT) : ENGINE_HTTP;
    }
}
