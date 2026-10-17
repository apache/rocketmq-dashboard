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
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The model field's column contract. Kept in its own class because the general DTO contract
 * (field mapping, session-timeout bounds, toString redaction) is pinned separately, and a merge
 * conflict between the two would otherwise have to be resolved by hand.
 */
class GeneralSettingsModelBoundTest {

    @Test
    void modelShouldBeBoundedToTheAiRecordColumnsTest() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            GeneralSettingsUpdateDTO accepted = validSettings();
            accepted.setModel("m".repeat(128));
            assertThat(validatorFactory.getValidator().validate(accepted)).isEmpty();

            GeneralSettingsUpdateDTO rejected = validSettings();
            rejected.setModel("m".repeat(129));
            assertThat(validatorFactory.getValidator().validate(rejected))
                    .anyMatch(violation -> "model".equals(violation.getPropertyPath().toString())
                            && "model must not exceed 128 characters".equals(violation.getMessage()));
        }
    }

    private static GeneralSettingsUpdateDTO validSettings() {
        return GeneralSettingsUpdateDTO.builder()
                .theme("dark")
                .compact(true)
                .desktopNotify(true)
                .notifySound(false)
                .sessionTimeout(45)
                .requireLogin(true)
                .llmProvider("openai")
                .model("gpt-4o")
                .baseUrl("https://api.openai.com/v1")
                .build();
    }
}
