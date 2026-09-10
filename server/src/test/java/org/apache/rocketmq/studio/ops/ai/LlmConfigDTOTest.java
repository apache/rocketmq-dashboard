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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmConfigDTOTest {

    private LlmConfigDTO sample() {
        LlmConfigDTO dto = new LlmConfigDTO();
        dto.setProvider("openai");
        dto.setEngine("http");
        dto.setApiKey("sk-test-secret");
        dto.setClearApiKey(false);
        dto.setApiBase("https://api.example.com/v1");
        dto.setModel("gpt-4o-mini");
        dto.setMaxTokens(2048);
        dto.setTemperature(0.7);
        dto.setEnabled(true);
        dto.setDeploymentName("deploy-1");
        dto.setApiVersion("2024-02-01");
        dto.setAwsRegion("us-east-1");
        return dto;
    }

    @Test
    void mapsEveryFieldToVo() {
        LlmConfigVO vo = sample().toLlmConfigVO();

        assertEquals("openai", vo.getProvider());
        assertEquals("http", vo.getEngine());
        assertEquals("sk-test-secret", vo.getApiKey());
        assertFalse(vo.isClearApiKey());
        assertEquals("https://api.example.com/v1", vo.getApiBase());
        assertEquals("gpt-4o-mini", vo.getModel());
        assertEquals(2048, vo.getMaxTokens());
        assertEquals(0.7, vo.getTemperature());
        assertTrue(vo.isEnabled());
        assertEquals("deploy-1", vo.getDeploymentName());
        assertEquals("2024-02-01", vo.getApiVersion());
        assertEquals("us-east-1", vo.getAwsRegion());
    }

    @Test
    void apiKeyIsExcludedFromToString() {
        LlmConfigDTO dto = sample();

        String rendered = dto.toString();

        assertFalse(rendered.contains("sk-test-secret"));
    }

    @Test
    void mapsEmptyDtoWithoutNpe() {
        LlmConfigVO vo = new LlmConfigDTO().toLlmConfigVO();

        assertEquals(null, vo.getProvider());
        assertEquals(0, vo.getMaxTokens());
        assertEquals(0.0, vo.getTemperature());
        assertFalse(vo.isEnabled());
    }
}
