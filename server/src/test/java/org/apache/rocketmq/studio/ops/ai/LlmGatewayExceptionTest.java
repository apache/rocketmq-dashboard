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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class LlmGatewayExceptionTest {

    @Test
    void messageOnlyConstructorDefaultsToBadGateway() {
        LlmGatewayException ex = new LlmGatewayException("upstream failed");

        assertEquals(502, ex.getStatusCode());
        assertEquals("llm.gateway_error", ex.getCode());
        assertEquals("upstream failed", ex.getMessage());
        assertNull(ex.getHint());
        assertNull(ex.getCause());
    }

    @Test
    void messageAndCauseConstructorPreservesCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        LlmGatewayException ex = new LlmGatewayException("upstream failed", cause);

        assertEquals(502, ex.getStatusCode());
        assertSame(cause, ex.getCause());
    }

    @Test
    void fullConstructorCarriesStatusCodeCodeAndHint() {
        LlmGatewayException ex = new LlmGatewayException(429, "llm.rate_limited",
                "rate limit reached", "Retry after 30s");

        assertEquals(429, ex.getStatusCode());
        assertEquals("llm.rate_limited", ex.getCode());
        assertEquals("rate limit reached", ex.getMessage());
        assertEquals("Retry after 30s", ex.getHint());
    }

    @Test
    void fullConstructorWithCausePreservesBothHintAndCause() {
        IllegalStateException cause = new IllegalStateException("boom");
        LlmGatewayException ex = new LlmGatewayException(503, "llm.unavailable",
                "service unavailable", "try later", cause);

        assertEquals(503, ex.getStatusCode());
        assertEquals("llm.unavailable", ex.getCode());
        assertEquals("try later", ex.getHint());
        assertSame(cause, ex.getCause());
    }
}
