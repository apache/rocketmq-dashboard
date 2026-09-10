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

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmConfigDTOTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();


    @Test
    void toStringShouldNeverExposeTheApiKey() {
        LlmConfigDTO request = new LlmConfigDTO();
        request.setProvider("openai");
        request.setApiKey("sk-secret");

        String value = request.toString();

        assertThat(value).contains("provider=openai");
        assertThat(value).doesNotContain("sk-secret");
    }

    @Test
    void toLlmConfigVoShouldRoundTripTheConfiguration() {
        LlmConfigDTO request = new LlmConfigDTO();
        request.setProvider("openai");
        request.setEngine("claude-code");
        request.setApiKey("sk-secret");
        request.setClearApiKey(true);
        request.setModel("gpt-5");
        request.setMaxTokens(4096);
        request.setTemperature(0.7);
        request.setEnabled(true);

        LlmConfigVO vo = request.toLlmConfigVO();

        assertThat(vo.getProvider()).isEqualTo("openai");
        assertThat(vo.getEngine()).isEqualTo("claude-code");
        assertThat(vo.isClearApiKey()).isTrue();
        assertThat(vo.getModel()).isEqualTo("gpt-5");
        assertThat(vo.getMaxTokens()).isEqualTo(4096);
        assertThat(vo.getTemperature()).isEqualTo(0.7);
        assertThat(vo.isEnabled()).isTrue();
    }
}
