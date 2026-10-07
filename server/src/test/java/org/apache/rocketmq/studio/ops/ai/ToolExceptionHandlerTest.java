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

import org.apache.rocketmq.studio.ops.ai.tool.core.ToolError;
import org.apache.rocketmq.studio.ops.ai.tool.core.ToolExecutionException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the exception-to-response mapping of {@link ToolExceptionHandler}, the advice that keeps
 * every tool entry point answering the same structured error shape - machine code, human message,
 * recovery hint - instead of a raw stack trace.
 */
class ToolExceptionHandlerTest {

    private final ToolExceptionHandler handler = new ToolExceptionHandler();

    @Test
    void aClientToolFailureKeepsItsOwnStatusAndCarriesCodeMessageAndHint() {
        ToolExecutionException exception =
                ToolError.INPUT_VALIDATION_FAILED.exception("rmq.cluster.list", "[/instanceId]");

        ResponseEntity<Map<String, String>> response = handler.handleToolExecutionException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().get("message"))
                .contains("Tool input validation failed for rmq.cluster.list");
        assertThat(response.getBody().get("hint")).isEqualTo(
                "Correct the reported fields according to the tool input schema and retry.");
    }

    @Test
    void aServerToolFailureMapsToItsOwnStatus() {
        ToolExecutionException exception = ToolError.UNEXPECTED_EXECUTION_FAILURE.exception();

        ResponseEntity<Map<String, String>> response = handler.handleToolExecutionException(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("INTERNAL_ERROR");
    }

    @Test
    void anUnreadableBodyBecomesTheStructuredInvalidBodyError() {
        ResponseEntity<Map<String, String>> response = handler.handleUnreadableBody();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("code")).isEqualTo("INVALID_ARGUMENT");
        assertThat(response.getBody().get("message")).isEqualTo("Tool request body is invalid.");
        assertThat(response.getBody().get("hint")).isEqualTo("Correct the tool request and retry.");
    }

    @Test
    void aMissingParameterNamesTheParameterItWants() {
        MissingServletRequestParameterException exception =
                new MissingServletRequestParameterException("instanceId", "String");

        ResponseEntity<Map<String, String>> response = handler.handleMissingParameter(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message"))
                .isEqualTo("Missing required tool parameter: instanceId");
    }

    @Test
    void aTypeMismatchNamesTheParameterItCouldNotConvert() {
        MethodArgumentTypeMismatchException exception =
                new MethodArgumentTypeMismatchException(null, null, "page", null, null);

        ResponseEntity<Map<String, String>> response = handler.handleInvalidParameter(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().get("message")).isEqualTo("Invalid tool parameter: page");
    }
}
