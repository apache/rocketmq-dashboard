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
package org.apache.rocketmq.studio.ops.ai.tool.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the error contract of {@link ToolError}: every scenario carries a non-blank public code and
 * recovery hint, message templates format their arguments, {@code exception()} packages all four
 * parts, and the status/code pairs follow the MCP error conventions.
 */
class ToolErrorTest {

    @Test
    void messageTemplatesFormatTheirArguments() {
        assertThat(ToolError.TOOL_NOT_FOUND.message("rmq.topic.list")).isEqualTo("Tool not found: rmq.topic.list");
        assertThat(ToolError.PROXY_NOT_FOUND.message("cluster-1", "proxy-1:8080"))
                .isEqualTo("Proxy not found in cluster cluster-1: proxy-1:8080");
        assertThat(ToolError.TOOL_CALL_REQUIRED.message()).isEqualTo("Tool call request is required");
    }

    @Test
    void exceptionCarriesTheStatusTheCodeTheMessageAndTheHint() {
        ToolExecutionException exception = ToolError.TOOL_NOT_FOUND.exception("rmq.topic.list");
        assertThat(exception.getCode()).isEqualTo(404);
        assertThat(exception.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(exception.getMessage()).isEqualTo("Tool not found: rmq.topic.list");
        assertThat(exception.getHint()).isEqualTo("Call tools/list to discover the available tool names.");
    }

    @Test
    void authenticationFailuresAreUnauthenticated() {
        assertThat(ToolError.MCP_AUTHENTICATION_REQUIRED.httpStatus().value()).isEqualTo(401);
        assertThat(ToolError.MCP_AUTHENTICATION_REQUIRED.code()).isEqualTo("UNAUTHENTICATED");
        assertThat(ToolError.MCP_AUTHENTICATION_FAILED.httpStatus().value()).isEqualTo(401);
        assertThat(ToolError.MCP_AUTHENTICATION_FAILED.code()).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void permissionDenialsAreForbidden() {
        assertThat(ToolError.L3_DISABLED.httpStatus().value()).isEqualTo(403);
        assertThat(ToolError.L3_DISABLED.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(ToolError.ADMIN_REQUIRED.httpStatus().value()).isEqualTo(403);
        assertThat(ToolError.ADMIN_REQUIRED.code()).isEqualTo("PERMISSION_DENIED");
        assertThat(ToolError.TOOL_TARGET_MISMATCH.httpStatus().value()).isEqualTo(403);
        assertThat(ToolError.TOOL_TARGET_MISMATCH.code()).isEqualTo("PERMISSION_DENIED");
    }

    @Test
    void unexpectedFailuresAreInternalErrors() {
        assertThat(ToolError.UNEXPECTED_EXECUTION_FAILURE.httpStatus().value()).isEqualTo(500);
        assertThat(ToolError.UNEXPECTED_EXECUTION_FAILURE.code()).isEqualTo("INTERNAL_ERROR");
        assertThat(ToolError.EXECUTION_FAILED.httpStatus().value()).isEqualTo(500);
        assertThat(ToolError.EXECUTION_FAILED.code()).isEqualTo("TOOL_EXECUTION_FAILED");
    }

    @Test
    void everyScenarioCarriesACodeAndARecoveryHint() {
        for (ToolError error : ToolError.values()) {
            assertThat(error.code()).as("code of %s", error).isNotBlank();
            assertThat(error.hint()).as("hint of %s", error).isNotBlank();
        }
    }
}
