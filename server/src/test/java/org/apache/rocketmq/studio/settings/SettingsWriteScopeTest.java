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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import org.apache.rocketmq.studio.audit.OperationAuditService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigService;
import org.apache.rocketmq.studio.ops.ai.LlmConfigVO;
import org.apache.rocketmq.studio.ops.ai.LlmProperties;
import org.apache.rocketmq.studio.ops.ai.OpenAiCompatibleLlmClient;
import org.apache.rocketmq.studio.ops.ai.AgentProviderRegistry;
import org.apache.rocketmq.studio.ops.alert.NotificationOutboxService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.ArgumentMatchers.any;

class SettingsWriteScopeTest {
    private final AtomicReference<GeneralSettingsVO> stored = new AtomicReference<>(GeneralSettingsVO.builder()
            .theme("light").sessionTimeout(1440).llmProvider("openai").apiKey("synthetic-key")
            .model("old-model").baseUrl("https://api.openai.com/v1").build());
    private final SettingsRepository repository = mock(SettingsRepository.class);

    private SettingsService settings() {
        when(repository.loadGeneralSettings()).thenAnswer(inv -> stored.get().toBuilder().build());
        doAnswer(inv -> {
            stored.set(((GeneralSettingsVO) inv.getArgument(0)).toBuilder().build());
            return null;
        })
                .when(repository).saveGeneralSettings(any());
        return new SettingsService(repository, RestClient.builder(), new ObjectMapper(), mock(OperationAuditService.class));
    }

    private LlmConfigService llm(SettingsService settings) {
        return new LlmConfigService(settings, mock(OpenAiCompatibleLlmClient.class),
                mock(AgentProviderRegistry.class), new LlmProperties());
    }

    private LlmConfigVO config() {
        return LlmConfigVO.builder().provider("openai").apiKey("synthetic-key").model("new-model")
                .apiBase("https://api.openai.com/v1").maxTokens(4096).temperature(0.7).build();
    }

    @Test
    void scopedWriteShouldIgnoreStaleAiFieldsAndSecretClearIntentTest() {
        SettingsService service = settings();
        GeneralSettingsVO current = stored.get().toBuilder().llmEngine("http").apiVersion("version")
                .deploymentName("deployment").awsRegion("region").maxTokens(8192).temperature(0.3).build();
        stored.set(current);
        service.saveGeneralSettings(current.toBuilder().theme("dark").apiKey("stale-key").clearApiKey(true)
                .llmProvider("stale-provider").llmEngine("stale-engine").model("stale-model")
                .baseUrl("stale-invalid-url").deploymentName("stale-deployment").apiVersion("stale-version")
                .awsRegion("stale-region").maxTokens(1).temperature(1.9).build(), true);
        assertThat(stored.get()).usingRecursiveComparison().ignoringFields("theme").isEqualTo(current);
        assertThat(stored.get().getTheme()).isEqualTo("dark");
    }

    @Test
    void scopedGeneralRequestShouldPreserveAnInterveningAiSaveTest() throws Exception {
        SettingsService service = settings();
        ObjectMapper mapper = new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        GeneralSettingsUpdateDTO request = mapper.readValue("""
                {"theme":"dark","compact":false,"desktopNotify":false,"notifySound":false,
                 "sessionTimeout":1440,"requireLogin":false,"llmProvider":"openai",
                 "model":"old-model","baseUrl":"https://api.openai.com/v1","preserveLlmConfig":true}
                """, GeneralSettingsUpdateDTO.class);
        llm(service).saveConfig(config());
        new SettingsController(service, mock(NotificationOutboxService.class)).saveGeneralSettings(request);
        assertThat(stored.get().getTheme()).isEqualTo("dark");
        assertThat(stored.get().getModel()).isEqualTo("new-model");
    }

    @Test
    void aiSaveShouldReadAfterAnInFlightGeneralWriteTest() throws Exception {
        SettingsService service = settings();
        CountDownLatch persisting = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        doAnswer(inv -> {
            if (Thread.currentThread().getName().equals("general-writer")) {
                persisting.countDown();
                assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            }
            stored.set(((GeneralSettingsVO) inv.getArgument(0)).toBuilder().build());
            return null;
        }).when(repository).saveGeneralSettings(any());
        AtomicReference<Thread> aiThread = new AtomicReference<>();
        try (ExecutorService general = Executors.newSingleThreadExecutor(r -> new Thread(r, "general-writer"));
                ExecutorService ai = Executors.newSingleThreadExecutor(r -> {
                    Thread thread = new Thread(r, "ai-writer");
                    aiThread.set(thread);
                    return thread;
                })) {
            Future<?> generalWrite = general.submit(() -> service.saveGeneralSettings(stored.get().toBuilder()
                    .theme("dark").emailRecipients("new@example.com").build()));
            try {
                assertThat(persisting.await(5, TimeUnit.SECONDS)).isTrue();
                Future<?> aiWrite = ai.submit(() -> llm(service).saveConfig(config()));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (aiThread.get().getState() != Thread.State.BLOCKED && System.nanoTime() < deadline) {
                    Thread.yield();
                }
                assertThat(aiThread.get().getState()).isEqualTo(Thread.State.BLOCKED);
                release.countDown();
                generalWrite.get(5, TimeUnit.SECONDS);
                aiWrite.get(5, TimeUnit.SECONDS);
                assertThat(stored.get().getTheme()).isEqualTo("dark");
                assertThat(stored.get().getEmailRecipients()).isEqualTo("new@example.com");
                assertThat(stored.get().getModel()).isEqualTo("new-model");
            } finally { release.countDown(); }
        }
    }
}
