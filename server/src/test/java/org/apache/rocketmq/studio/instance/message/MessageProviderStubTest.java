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
package org.apache.rocketmq.studio.instance.message;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the test-only message provider fallback: every operation must stay loud (501)
 * instead of silently degrading when no real message provider is wired up.
 */
class MessageProviderStubTest {

    private final MessageProviderStub stub = new MessageProviderStub();

    private BusinessException unsupportedFrom(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(BusinessException.class, action);
    }

    @Test
    void queryMessagesThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.queryMessages("inst-1", "orders", null, null, null, null, null));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Message query provider is not configured"));
    }

    @Test
    void getMessageTraceThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.getMessageTrace("inst-1", "msg-1", "orders"));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Message query provider is not configured"));
    }

    @Test
    void getQueueOffsetsThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(() -> stub.getQueueOffsets("inst-1", "orders"));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Message query provider is not configured"));
    }

    @Test
    void pullMessageAtOffsetThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.pullMessageAtOffset("inst-1", "orders", "broker-a", 3, 100L));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Message query provider is not configured"));
    }
}
