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
package org.apache.rocketmq.studio.instance.dlq;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the test-only fallback provider: every operation must stay loud (501)
 * instead of silently degrading into empty results when no real DLQ provider
 * is wired up.
 */
class DLQProviderStubTest {

    private final DLQProviderStub stub = new DLQProviderStub();

    private BusinessException unsupportedFrom(org.junit.jupiter.api.function.Executable action) {
        return assertThrows(BusinessException.class, action);
    }

    @Test
    void listDLQGroupsThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(() -> stub.listDLQGroups("inst-1"));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("DLQ provider is not configured"));
    }

    @Test
    void pagedListDLQGroupsThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.listDLQGroups("inst-1", "cg", 1, 20));

        assertEquals(501, ex.getCode());
    }

    @Test
    void resendByRangeThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.resendMessages("inst-1", "cg", 1L, 100L, "dlq-topic"));

        assertEquals(501, ex.getCode());
    }

    @Test
    void exportMessagesThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.exportMessages("inst-1", "cg", 1L, 100L, 1000));

        assertEquals(501, ex.getCode());
    }

    @Test
    void listMessagesThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.listMessages("inst-1", "cg", 1L, 100L, 1, 20));

        assertEquals(501, ex.getCode());
    }

    @Test
    void resendSelectedThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.resendMessages("inst-1", "cg", List.of("m1"), "dlq-topic"));

        assertEquals(501, ex.getCode());
    }

    @Test
    void exportExcelThrowsUnsupported() {
        BusinessException ex = unsupportedFrom(
                () -> stub.exportExcel("inst-1", "cg", 1L, 100L, List.of("m1")));

        assertEquals(501, ex.getCode());
    }
}
