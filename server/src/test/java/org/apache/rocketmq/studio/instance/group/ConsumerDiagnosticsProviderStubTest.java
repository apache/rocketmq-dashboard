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
package org.apache.rocketmq.studio.instance.group;

import org.apache.rocketmq.studio.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the diagnostics fallback provider: it must stay loud (501) instead of
 * silently degrading when no real consumer diagnostics provider is wired up.
 */
class ConsumerDiagnosticsProviderStubTest {

    private final ConsumerDiagnosticsProviderStub stub = new ConsumerDiagnosticsProviderStub();

    @Test
    void getConsumerStackThrowsUnsupported() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> stub.getConsumerStack("inst-1", "cg-1", "client-1"));

        assertEquals(501, ex.getCode());
        assertTrue(ex.getMessage().contains("Consumer diagnostics provider is not configured"));
    }
}
