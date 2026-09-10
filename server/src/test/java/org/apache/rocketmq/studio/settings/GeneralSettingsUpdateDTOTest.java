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
    void requiredFieldsShouldBePresent() {
        GeneralSettingsUpdateDTO request = GeneralSettingsUpdateDTO.builder().build();

        assertThat(validator.validate(request))
                .extracting(violation -> violation.getPropertyPath().toString())
                .contains("theme", "compact", "desktopNotify", "notifySound",
                        "sessionTimeout", "requireLogin", "llmProvider", "model", "baseUrl");
    }

    @Test
    void sessionTimeoutShouldStayWithinBounds() {
        GeneralSettingsUpdateDTO shortOne = validSettings();
        shortOne.setSessionTimeout(4);

        GeneralSettingsUpdateDTO longOne = validSettings();
        longOne.setSessionTimeout(1441);

        assertThat(validator.validate(shortOne))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("sessionTimeout");
        assertThat(validator.validate(longOne))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("sessionTimeout");
    }

    @Test
    void validSettingsShouldPassValidation() {
        assertThat(validator.validate(validSettings())).isEmpty();
    }

    @Test
    void toStringShouldNeverExposeSecrets() {
        GeneralSettingsUpdateDTO request = validSettings();
        request.setApiKey("sk-secret");
        request.setDingtalkSigningSecret("ding-sign-secret");

        String value = request.toString();

        assertThat(value).contains("theme=dark");
        assertThat(value).doesNotContain("sk-secret");
        assertThat(value).doesNotContain("ding-sign-secret");
    }

    @Test
    void toSettingsShouldRoundTripThePayload() {
        GeneralSettingsUpdateDTO request = validSettings();
        request.setClearApiKey(true);
        request.setClearDingtalkSigningSecret(true);

        GeneralSettingsVO vo = request.toSettings();

        assertThat(vo.getTheme()).isEqualTo("dark");
        assertThat(vo.getSessionTimeout()).isEqualTo(60);
        assertThat(vo.isClearApiKey()).isTrue();
        assertThat(vo.isClearDingtalkSigningSecret()).isTrue();
    }

    private static GeneralSettingsUpdateDTO validSettings() {
        return GeneralSettingsUpdateDTO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(60)
                .requireLogin(true)
                .llmProvider("openai")
                .model("gpt-5")
                .baseUrl("https://api.example.com/v1")
                .build();
    }
}
