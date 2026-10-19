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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ToolExecutionResult}: it carries both the business output and the final context
 * after a filter chain completes, and the factory is a plain constructor alias.
 */
class ToolExecutionResultTest {

    @Test
    void theOutputAndContextRoundTrip() {
        ToolExecutionContext context = new ToolExecutionContext("instance-1", null, Map.of(), null);
        ToolExecutionResult result = ToolExecutionResult.of("the output", context);

        assertThat(result.output()).isEqualTo("the output");
        assertThat(result.context()).isSameAs(context);
    }

    @Test
    void aNullOutputIsCarriedVerbatim() {
        ToolExecutionResult result = ToolExecutionResult.of(null, new ToolExecutionContext("instance-1", null, Map.of(), null));
        assertThat(result.output()).isNull();
    }
}
