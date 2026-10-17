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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the model listing result of {@link LlmModelsResultVO}: the status with the model list,
 * the source it came from and the degradation triple (warning, warningCode, hint) that tells
 * the UI why the list may be stale or partial.
 */
class LlmModelsResultVOTest {

    @Test
    void aSuccessfulListingRoundTrips() {
        LlmModelItemVO model = new LlmModelItemVO();
        LlmModelsResultVO result = new LlmModelsResultVO();
        result.setStatus(200);
        result.setData(List.of(model));
        result.setSource("openai");

        assertThat(result.getStatus()).isEqualTo(200);
        assertThat(result.getData()).containsExactly(model);
        assertThat(result.getSource()).isEqualTo("openai");
        assertThat(result.getWarning()).isNull();
    }

    @Test
    void aDegradedListingCarriesTheFullWarningTriple() {
        LlmModelsResultVO result = new LlmModelsResultVO();
        result.setStatus(401);
        result.setData(List.of());
        result.setSource("stub");
        result.setWarning("provider rejected the key");
        result.setWarningCode("AUTH_FAILED");
        result.setHint("Check the configured API key");

        assertThat(result.getStatus()).isEqualTo(401);
        assertThat(result.getWarning()).isEqualTo("provider rejected the key");
        assertThat(result.getWarningCode()).isEqualTo("AUTH_FAILED");
        assertThat(result.getHint()).isEqualTo("Check the configured API key");
    }
}
