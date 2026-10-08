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

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ToolExecutionException}: the typed failure a tool call surfaces. The translation
 * rule is the load-bearing part - a foreign BusinessException must arrive at the MCP caller as a
 * structured TOOL_EXECUTION_FAILED with its cause chained, never as a bare runtime exception.
 */
class ToolExecutionExceptionTest {

    @Test
    void carriesStatusCodeMessageAndHint() {
        ToolExecutionException exception = new ToolExecutionException(
                HttpStatus.BAD_REQUEST, "INVALID_ARGUMENT", "bad input", "fix the input");

        assertThat(exception.getCode()).isEqualTo(400);
        assertThat(exception.getMessage()).isEqualTo("bad input");
        assertThat(exception.getErrorCode()).isEqualTo("INVALID_ARGUMENT");
        assertThat(exception.getHint()).isEqualTo("fix the input");
    }

    @Test
    void aToolExecutionExceptionPassesThroughUntranslated() {
        ToolExecutionException original = new ToolExecutionException(
                HttpStatus.NOT_FOUND, "NOT_FOUND", "instance missing", "pick another");

        ToolExecutionException translated = ToolExecutionException.from(original);

        assertThat(translated).isSameAs(original);
        assertThat(translated.getErrorCode()).isEqualTo("NOT_FOUND");
        assertThat(translated.getHint()).isEqualTo("pick another");
    }

    @Test
    void aForeignBusinessExceptionIsTranslatedToTheStructuredExecutionFailure() {
        BusinessException foreign = new BusinessException(422, "instance has no credential");

        ToolExecutionException translated = ToolExecutionException.from(foreign);

        assertThat(translated.getCode()).isEqualTo(422);
        assertThat(translated.getMessage()).isEqualTo("instance has no credential");
        assertThat(translated.getErrorCode()).isEqualTo("TOOL_EXECUTION_FAILED");
        assertThat(translated.getHint())
                .isEqualTo("Review the failure message; no specific recovery action is available.");
        assertThat(translated.getCause()).isSameAs(foreign);
    }

    @Test
    void anUnresolvableStatusFallsBackTo500() {
        BusinessException oddStatus = new BusinessException(999, "weird status");

        ToolExecutionException translated = ToolExecutionException.from(oddStatus);

        assertThat(translated.getCode()).isEqualTo(500);
        assertThat(translated.getCause()).isSameAs(oddStatus);
    }
}
