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

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the update contract of {@link GeneralSettingsUpdateDTO}: every field maps into
 * {@code toSettings()}, the session timeout is bounded between 5 and 1440 minutes, and the
 * Lombok-generated toString never exposes the API key or the DingTalk signing secret.
 */
class GeneralSettingsUpdateDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private GeneralSettingsUpdateDTO.GeneralSettingsUpdateDTOBuilder valid() {
        return GeneralSettingsUpdateDTO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(false)
                .notifySound(true)
                .sessionTimeout(30)
                .requireLogin(true)
                .llmProvider("openai")
                .model("gpt-4o")
                .baseUrl("https://api.example.com");
    }

    private Set<String> violationsOf(GeneralSettingsUpdateDTO request) {
        Set<ConstraintViolation<GeneralSettingsUpdateDTO>> violations = validator.validate(request);
        return violations.stream().map(ConstraintViolation::getMessage).collect(Collectors.toSet());
    }

    @Test
    void toSettingsMapsEveryField() {
        GeneralSettingsUpdateDTO request = valid()
                .llmEngine("compatible")
                .apiKey("sk-test-key")
                .clearApiKey(true)
                .deploymentName("dep-1")
                .apiVersion("2024-01-01")
                .awsRegion("cn-hangzhou")
                .dingtalkWebhook("https://oapi.dingtalk.example/hook")
                .dingtalkSigningSecret("ding-secret")
                .clearDingtalkSigningSecret(true)
                .emailRecipients("ops@example.com")
                .smsWebhook("https://sms.example/hook")
                .build();

        GeneralSettingsVO settings = request.toSettings();

        assertThat(settings.getTheme()).isEqualTo("dark");
        assertThat(settings.isCompact()).isTrue();
        assertThat(settings.isDesktopNotify()).isFalse();
        assertThat(settings.isNotifySound()).isTrue();
        assertThat(settings.getSessionTimeout()).isEqualTo(30);
        assertThat(settings.isRequireLogin()).isTrue();
        assertThat(settings.getLlmProvider()).isEqualTo("openai");
        assertThat(settings.getLlmEngine()).isEqualTo("compatible");
        assertThat(settings.getApiKey()).isEqualTo("sk-test-key");
        assertThat(settings.isClearApiKey()).isTrue();
        assertThat(settings.getModel()).isEqualTo("gpt-4o");
        assertThat(settings.getBaseUrl()).isEqualTo("https://api.example.com");
        assertThat(settings.getDeploymentName()).isEqualTo("dep-1");
        assertThat(settings.getApiVersion()).isEqualTo("2024-01-01");
        assertThat(settings.getAwsRegion()).isEqualTo("cn-hangzhou");
        assertThat(settings.getDingtalkWebhook()).isEqualTo("https://oapi.dingtalk.example/hook");
        assertThat(settings.getDingtalkSigningSecret()).isEqualTo("ding-secret");
        assertThat(settings.isClearDingtalkSigningSecret()).isTrue();
        assertThat(settings.getEmailRecipients()).isEqualTo("ops@example.com");
        assertThat(settings.getSmsWebhook()).isEqualTo("https://sms.example/hook");
    }

    @Test
    void toStringNeverExposesTheApiKeyOrTheDingtalkSigningSecret() {
        GeneralSettingsUpdateDTO request = valid()
                .apiKey("sk-plain-secret-123")
                .dingtalkSigningSecret("ding-plain-secret-456")
                .build();

        String value = request.toString();

        assertThat(value).contains("llmProvider=openai");
        assertThat(value).doesNotContain("sk-plain-secret-123");
        assertThat(value).doesNotContain("ding-plain-secret-456");
    }

    @Test
    void sessionTimeoutIsBoundedBetweenFiveMinutesAndOneDay() {
        GeneralSettingsUpdateDTO tooShort = valid().sessionTimeout(4).build();
        assertThat(violationsOf(tooShort)).isNotEmpty();

        GeneralSettingsUpdateDTO atFloor = valid().sessionTimeout(5).build();
        assertThat(violationsOf(atFloor)).isEmpty();

        GeneralSettingsUpdateDTO atCeiling = valid().sessionTimeout(1440).build();
        assertThat(violationsOf(atCeiling)).isEmpty();

        GeneralSettingsUpdateDTO tooLong = valid().sessionTimeout(1441).build();
        assertThat(violationsOf(tooLong)).isNotEmpty();
    }

    @Test
    void theCorePreferencesAreRequired() {
        GeneralSettingsUpdateDTO missing = GeneralSettingsUpdateDTO.builder().build();
        Set<String> violatedProperties = validator.validate(missing).stream()
                .map(violation -> violation.getPropertyPath().toString())
                .collect(Collectors.toSet());
        assertThat(violatedProperties).containsExactlyInAnyOrder(
                "theme", "compact", "desktopNotify", "notifySound", "sessionTimeout",
                "requireLogin", "llmProvider", "model", "baseUrl");
    }
}
