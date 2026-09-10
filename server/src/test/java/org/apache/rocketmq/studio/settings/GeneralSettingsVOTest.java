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
package org.apache.rocketmq.studio.settings;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeneralSettingsVOTest {

    @Test
    void builderDefaultsDescribeFreshSettings() {
        GeneralSettingsVO vo = GeneralSettingsVO.builder().build();

        assertNull(vo.getTheme());
        assertFalse(vo.isCompact());
        assertFalse(vo.isDesktopNotify());
        assertFalse(vo.isNotifySound());
        assertEquals(0, vo.getSessionTimeout());
        assertFalse(vo.isRequireLogin());
        assertNull(vo.getApiKey());
        assertNull(vo.getDingtalkWebhook());
    }

    @Test
    void secretsAreExcludedFromToString() {
        GeneralSettingsVO vo = GeneralSettingsVO.builder()
            .apiKey("sk-secret")
            .dingtalkSigningSecret("sign-secret")
            .build();

        String rendered = vo.toString();

        assertFalse(rendered.contains("sk-secret"));
        assertFalse(rendered.contains("sign-secret"));
    }

    @Test
    void apiKeyConfiguredReflectsPresence() {
        GeneralSettingsVO empty = GeneralSettingsVO.builder().build();
        assertFalse(empty.isApiKeyConfigured());

        GeneralSettingsVO configured = GeneralSettingsVO.builder().apiKey("sk-1").build();
        assertTrue(configured.isApiKeyConfigured());
    }

    @Test
    void allArgsCarrySettingsState() {
        GeneralSettingsVO vo = GeneralSettingsVO.builder()
            .theme("dark")
            .compact(true)
            .desktopNotify(true)
            .notifySound(false)
            .sessionTimeout(3600)
            .requireLogin(true)
            .llmProvider("openai")
            .llmEngine("http")
            .clearApiKey(true)
            .model("gpt-4o")
            .maxTokens(4096)
            .temperature(0.7)
            .emailRecipients("ops@example.com")
            .build();

        assertEquals("dark", vo.getTheme());
        assertTrue(vo.isCompact());
        assertTrue(vo.isDesktopNotify());
        assertEquals(3600, vo.getSessionTimeout());
        assertTrue(vo.isRequireLogin());
        assertTrue(vo.isClearApiKey());
        assertEquals("gpt-4o", vo.getModel());
        assertEquals(4096, vo.getMaxTokens());
        assertEquals(0.7, vo.getTemperature());
    }
}
