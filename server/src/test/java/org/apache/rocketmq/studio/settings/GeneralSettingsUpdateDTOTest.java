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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GeneralSettingsUpdateDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void missingRequiredFieldsAreReportedTest() {
        GeneralSettingsUpdateDTO request = new GeneralSettingsUpdateDTO();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("theme", "compact", "desktopNotify", "notifySound", "sessionTimeout",
                        "requireLogin", "llmProvider", "model", "baseUrl");
    }

    @Test
    void sessionTimeoutOutsideRangeIsRejectedTest() {
        GeneralSettingsUpdateDTO request = valid();
        request.setSessionTimeout(1441);

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("sessionTimeout");
    }

    @Test
    void completeRequestPassesValidationTest() {
        assertThat(validator.validate(valid())).isEmpty();
    }

    @Test
    void toSettingsCarriesTheConfiguredValuesTest() {
        GeneralSettingsUpdateDTO request = valid();
        request.setLlmEngine("claude-code");
        request.setApiKey("secret");
        request.setClearApiKey(true);
        request.setDingtalkSigningSecret("signing");
        request.setSmsWebhook("https://sms.example/hook");

        GeneralSettingsVO settings = request.toSettings();

        assertThat(settings.getTheme()).isEqualTo("dark");
        assertThat(settings.getSessionTimeout()).isEqualTo(30);
        assertThat(settings.getLlmEngine()).isEqualTo("claude-code");
        assertThat(settings.getApiKey()).isEqualTo("secret");
        assertThat(settings.isClearApiKey()).isTrue();
        assertThat(settings.getDingtalkSigningSecret()).isEqualTo("signing");
        assertThat(settings.getSmsWebhook()).isEqualTo("https://sms.example/hook");
    }

    private static GeneralSettingsUpdateDTO valid() {
        GeneralSettingsUpdateDTO request = new GeneralSettingsUpdateDTO();
        request.setTheme("dark");
        request.setCompact(false);
        request.setDesktopNotify(true);
        request.setNotifySound(false);
        request.setSessionTimeout(30);
        request.setRequireLogin(true);
        request.setLlmProvider("openai");
        request.setModel("gpt-4o");
        request.setBaseUrl("");
        return request;
    }
}
