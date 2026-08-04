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

package org.apache.rocketmq.studio.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.studio.persistence.entity.RmqSettings;
import org.apache.rocketmq.studio.persistence.mapper.RmqDataSourceMapper;
import org.apache.rocketmq.studio.persistence.mapper.RmqSettingsMapper;
import org.apache.rocketmq.studio.settings.GeneralSettingsVO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MybatisPlusSettingsRepositoryTest {

    @Mock
    private RmqSettingsMapper settingsMapper;

    @Mock
    private RmqDataSourceMapper dataSourceMapper;

    @InjectMocks
    private MybatisPlusSettingsRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void saveAndLoadShouldRetainApiKeyWithoutExposingItThroughDefaultSerialization() throws Exception {
        repository = new MybatisPlusSettingsRepository(settingsMapper, dataSourceMapper, objectMapper);
        GeneralSettingsVO settings = GeneralSettingsVO.builder()
                .theme("system")
                .llmProvider("openai")
                .apiKey("sk-persisted")
                .model("gpt-4o")
                .baseUrl("https://api.openai.com/v1")
                .build();
        when(settingsMapper.selectById("singleton")).thenReturn(null);

        repository.saveGeneralSettings(settings);

        ArgumentCaptor<RmqSettings> captor = ArgumentCaptor.forClass(RmqSettings.class);
        verify(settingsMapper).insert(captor.capture());
        RmqSettings stored = captor.getValue();
        assertThat(stored.getJson()).contains("apiKey").contains("sk-persisted");
        assertThat(objectMapper.writeValueAsString(settings)).doesNotContain("sk-persisted");

        when(settingsMapper.selectById("singleton")).thenReturn(stored);

        assertThat(repository.loadGeneralSettings().getApiKey()).isEqualTo("sk-persisted");
    }
}
