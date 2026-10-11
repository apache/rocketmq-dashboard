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
package org.apache.rocketmq.studio.ops.ai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link McpAuthenticationException}: the message is preserved verbatim and the cause
 * chain survives construction - an authentication failure must not swallow what caused it.
 */
class McpAuthenticationExceptionTest {

    @Test
    void theMessageIsPreservedVerbatim() {
        McpAuthenticationException exception = new McpAuthenticationException("MCP authentication is required");
        assertThat(exception.getMessage()).isEqualTo("MCP authentication is required");
        assertThat(exception.getCause()).isNull();
    }

    @Test
    void theCauseChainSurvivesConstruction() {
        IllegalStateException cause = new IllegalStateException("token expired");
        McpAuthenticationException exception = new McpAuthenticationException("MCP authentication failed", cause);

        assertThat(exception.getMessage()).isEqualTo("MCP authentication failed");
        assertThat(exception.getCause()).isSameAs(cause);
        assertThatThrownBy(() -> {
            throw exception;
        }).hasRootCauseMessage("token expired");
    }
}
