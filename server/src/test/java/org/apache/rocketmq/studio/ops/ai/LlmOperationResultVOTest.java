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

class LlmOperationResultVOTest {

    @Test
    void successFactoryReturnsZeroStatusWithMessage() {
        LlmOperationResultVO vo = LlmOperationResultVO.success("saved");

        assertEquals(0, vo.getStatus());
        assertEquals("saved", vo.getMsg());
        assertNull(vo.getErrMsg());
        assertNull(vo.getCode());
        assertNull(vo.getModels());
    }

    @Test
    void successWithModelsFactoryCarriesModelList() {
        LlmOperationResultVO vo = LlmOperationResultVO.successWithModels("ok", List.of());

        assertEquals(0, vo.getStatus());
        assertEquals("ok", vo.getMsg());
        assertEquals(List.of(), vo.getModels());
    }

    @Test
    void failureFactoryDefaultsToInvalidConfigCode() {
        LlmOperationResultVO vo = LlmOperationResultVO.failure("bad api key");

        assertEquals(1, vo.getStatus());
        assertEquals("llm.config.invalid", vo.getCode());
        assertEquals("bad api key", vo.getErrMsg());
        assertNull(vo.getMsg());
        assertNull(vo.getHint());
    }

    @Test
    void failureFactoryCarriesCodeAndHint() {
        LlmOperationResultVO vo = LlmOperationResultVO.failure("llm.rate_limited",
                "rate limit reached", "retry later");

        assertEquals(1, vo.getStatus());
        assertEquals("llm.rate_limited", vo.getCode());
        assertEquals("rate limit reached", vo.getErrMsg());
        assertEquals("retry later", vo.getHint());
    }
}
