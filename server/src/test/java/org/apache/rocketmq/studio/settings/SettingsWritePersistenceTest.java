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

import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:settings-write-scope;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
    "studio.llm.token="
})
@ActiveProfiles("dev")
class SettingsWritePersistenceTest {
    @Autowired
    private SettingsService settings;
    @Autowired
    private LlmConfigService llm;
    @Autowired
    private SettingsRepository repository;
    @Autowired
    private RmqSettingsMapper mapper;

    @BeforeEach
    void initializeTest() {
        settings.saveGeneralSettings(GeneralSettingsVO.builder().theme("light").sessionTimeout(1440)
                .llmProvider("openai").model("old-model").apiKey("synthetic-key")
                .baseUrl("https://api.openai.com/v1").emailRecipients("old@example.com").build());
    }

    @Test
    void scopedSaveShouldPersistBothFormsWithoutPersistingTheIntentFlagTest() {
        GeneralSettingsVO pending = repository.loadGeneralSettings().toBuilder()
                .theme("dark").emailRecipients("new@example.com").build();
        llm.saveConfig(LlmConfigVO.builder().provider("openai").model("new-model")
                .apiBase("https://api.openai.com/v1").maxTokens(8192).temperature(0.3).build());
        settings.saveGeneralSettings(pending, true);
        GeneralSettingsVO result = repository.loadGeneralSettings();
        assertThat(result.getTheme()).isEqualTo("dark");
        assertThat(result.getEmailRecipients()).isEqualTo("new@example.com");
        assertThat(result.getModel()).isEqualTo("new-model");
        assertThat(result.getApiKey()).isEqualTo("synthetic-key");
        assertThat(result.getMaxTokens()).isEqualTo(8192);
        assertThat(result.getTemperature()).isEqualTo(0.3);
        assertThat(mapper.selectList(null)).hasSize(1);
        assertThat(mapper.selectList(null).getFirst().getJson()).doesNotContain("preserveLlmConfig");
    }

    @Test
    void legacyFullSaveShouldStillUpdateAiFieldsTest() {
        settings.saveGeneralSettings(repository.loadGeneralSettings().toBuilder().model("legacy-model")
                .apiKey("legacy-key").build());
        GeneralSettingsVO result = repository.loadGeneralSettings();
        assertThat(result.getModel()).isEqualTo("legacy-model");
        assertThat(result.getApiKey()).isEqualTo("legacy-key");
    }

    @Test
    void aiKeyClearShouldRetainNotificationFieldsTest() {
        llm.saveConfig(LlmConfigVO.builder().provider("ollama").model("local-model")
                .apiBase("http://localhost:11434/v1").clearApiKey(true).build());
        GeneralSettingsVO result = repository.loadGeneralSettings();
        assertThat(result.getApiKey()).isEmpty();
        assertThat(result.getEmailRecipients()).isEqualTo("old@example.com");
        assertThat(result.getTheme()).isEqualTo("light");
    }
}
