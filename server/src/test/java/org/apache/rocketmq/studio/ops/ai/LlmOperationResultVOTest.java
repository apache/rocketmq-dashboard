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
 * Pins {@link LlmOperationResultVO}: the answer shape of the LLM config operations. The status
 * 0/1 split and the msg-vs-errMsg channel discipline are what the settings UI branches on - a
 * failure in the success channel (or vice versa) renders as a blank toast.
 */
class LlmOperationResultVOTest {

    @Test
    void successCarriesTheMessageOnTheSuccessChannelOnly() {
        LlmOperationResultVO result = LlmOperationResultVO.success("saved");

        assertThat(result.getStatus()).isZero();
        assertThat(result.getMsg()).isEqualTo("saved");
        assertThat(result.getErrMsg()).isNull();
        assertThat(result.getCode()).isNull();
        assertThat(result.getHint()).isNull();
        assertThat(result.getModels()).isNull();
    }

    @Test
    void successWithModelsCarriesTheModelList() {
        LlmModelItemVO model = new LlmModelItemVO();
        LlmOperationResultVO result =
                LlmOperationResultVO.successWithModels("loaded", List.of(model));

        assertThat(result.getStatus()).isZero();
        assertThat(result.getMsg()).isEqualTo("loaded");
        assertThat(result.getModels()).containsExactly(model);
        assertThat(result.getErrMsg()).isNull();
    }

    @Test
    void failureDefaultsToTheConfigInvalidCode() {
        LlmOperationResultVO result = LlmOperationResultVO.failure("bad api key");

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getErrMsg()).isEqualTo("bad api key");
        assertThat(result.getMsg()).isNull();
        assertThat(result.getCode()).isEqualTo("llm.config.invalid");
        assertThat(result.getHint()).isNull();
        assertThat(result.getModels()).isNull();
    }

    @Test
    void aTypedFailureCarriesItsCodeAndHint() {
        LlmOperationResultVO result =
                LlmOperationResultVO.failure("llm.provider.unreachable", "gateway down", "check the url");

        assertThat(result.getStatus()).isEqualTo(1);
        assertThat(result.getErrMsg()).isEqualTo("gateway down");
        assertThat(result.getCode()).isEqualTo("llm.provider.unreachable");
        assertThat(result.getHint()).isEqualTo("check the url");
    }

    @Test
    void theChannelsAreMutuallyExclusive() {
        // success never sets the error channel; failure never sets the
        // success channel or the model list
        LlmOperationResultVO success = LlmOperationResultVO.success("ok");
        LlmOperationResultVO failure = LlmOperationResultVO.failure("no");

        assertThat(success.getErrMsg()).isNull();
        assertThat(success.getCode()).isNull();
        assertThat(failure.getMsg()).isNull();
        assertThat(failure.getModels()).isNull();
    }
}
