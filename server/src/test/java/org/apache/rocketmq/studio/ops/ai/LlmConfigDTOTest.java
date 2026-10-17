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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the LLM config of {@link LlmConfigDTO}: the provider triple (provider, engine, model)
 * with the connection settings and the cloud-vendor extras, and the clear flag that lets an
 * update drop a stored key.
 */
class LlmConfigDTOTest {

    @Test
    void everyFieldRoundTrips() {
        LlmConfigDTO config = new LlmConfigDTO();
        config.setProvider("openai");
        config.setEngine("compatible");
        config.setApiKey("sk-1");
        config.setClearApiKey(true);
        config.setApiBase("https://api.example.com/v1");
        config.setModel("gpt-4o");
        config.setMaxTokens(4096);
        config.setTemperature(0.7);
        config.setEnabled(true);
        config.setDeploymentName("dep-1");
        config.setApiVersion("2024-01-01");
        config.setAwsRegion("cn-hangzhou");

        assertThat(config.getProvider()).isEqualTo("openai");
        assertThat(config.getEngine()).isEqualTo("compatible");
        assertThat(config.getApiKey()).isEqualTo("sk-1");
        assertThat(config.isClearApiKey()).isTrue();
        assertThat(config.getApiBase()).isEqualTo("https://api.example.com/v1");
        assertThat(config.getModel()).isEqualTo("gpt-4o");
        assertThat(config.getMaxTokens()).isEqualTo(4096);
        assertThat(config.getTemperature()).isEqualTo(0.7);
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getDeploymentName()).isEqualTo("dep-1");
        assertThat(config.getApiVersion()).isEqualTo("2024-01-01");
        assertThat(config.getAwsRegion()).isEqualTo("cn-hangzhou");
    }

    @Test
    void aFreshConfigIsDisabledWithNoKey() {
        LlmConfigDTO config = new LlmConfigDTO();
        assertThat(config.isEnabled()).isFalse();
        assertThat(config.isClearApiKey()).isFalse();
        assertThat(config.getApiKey()).isNull();
        assertThat(config.getMaxTokens()).isZero();
        assertThat(config.getTemperature()).isZero();
    }
}
