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
package org.apache.rocketmq.studio.common.exception;

import org.apache.rocketmq.studio.common.domain.Result;
import org.apache.rocketmq.studio.ops.ai.LlmGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new FailingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void preservesNotFoundBusinessStatusAndEnvelope() throws Exception {
        mockMvc.perform(get("/test/business/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.message").value("failure-404"));
    }

    @Test
    void preservesBadRequestBusinessStatusAndEnvelope() throws Exception {
        mockMvc.perform(get("/test/business/400"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("failure-400"));
    }

    @Test
    void preservesLlmGatewayStatusAndEnvelope() throws Exception {
        mockMvc.perform(get("/test/llm-timeout"))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value(504))
                .andExpect(jsonPath("$.message").value("LLM provider request timed out"));
    }

    @Test
    void unmappedRouteReturns404WithEnvelopeInsteadOf500() {
        // Standalone MockMvc does not synthesize NoResourceFoundException for unmapped
        // routes (only a full Spring MVC resource resolver does), so verify the handler
        // mapping directly: NoResourceFoundException must map to a 404 Result envelope
        // rather than falling through to the generic 500 catch-all.
        GlobalExceptionHandler handler = new GlobalExceptionHandler();
        org.springframework.web.servlet.resource.NoResourceFoundException ex =
                new org.springframework.web.servlet.resource.NoResourceFoundException(
                        org.springframework.http.HttpMethod.GET, "missing-resource");
        org.apache.rocketmq.studio.common.domain.Result<?> result =
                handler.handleNoResourceFoundException(ex);
        org.assertj.core.api.Assertions.assertThat(result.getCode()).isEqualTo(404);
    }

    @Test
    void wrongHttpMethodReturns405WithEnvelopeInsteadOf500() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/test/business/400"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value(405));
    }

    @RestController
    static class FailingController {

        @GetMapping("/test/business/{code}")
        Result<Void> fail(@PathVariable int code) {
            throw new BusinessException(code, "failure-" + code);
        }

        @GetMapping("/test/llm-timeout")
        Result<Void> failLlm() {
            throw new LlmGatewayException(504, "llm.provider.timeout",
                    "LLM provider request timed out", "Retry later.");
        }
    }
}
