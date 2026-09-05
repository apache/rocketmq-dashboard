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

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlmModelsResultVOTest {

    @Test
    void exposesSourceConstants() {
        assertEquals("provider", LlmModelsResultVO.SOURCE_PROVIDER);
        assertEquals("builtin", LlmModelsResultVO.SOURCE_BUILTIN);
        assertEquals("fallback", LlmModelsResultVO.SOURCE_FALLBACK);
    }

    @Test
    void shortConstructorDefaultsToBuiltinSource() {
        LlmModelsResultVO vo = new LlmModelsResultVO(0, List.of());

        assertEquals(0, vo.getStatus());
        assertEquals(List.of(), vo.getData());
        assertEquals(LlmModelsResultVO.SOURCE_BUILTIN, vo.getSource());
        assertNull(vo.getWarning());
        assertNull(vo.getWarningCode());
        assertNull(vo.getHint());
    }

    @Test
    void fullConstructorCarriesWarningState() {
        LlmModelsResultVO vo = new LlmModelsResultVO(1, List.of(), LlmModelsResultVO.SOURCE_FALLBACK,
                "models unavailable", "llm.models_unavailable", "retry later");

        assertEquals(1, vo.getStatus());
        assertEquals(LlmModelsResultVO.SOURCE_FALLBACK, vo.getSource());
        assertEquals("models unavailable", vo.getWarning());
        assertEquals("llm.models_unavailable", vo.getWarningCode());
        assertEquals("retry later", vo.getHint());
    }
}
