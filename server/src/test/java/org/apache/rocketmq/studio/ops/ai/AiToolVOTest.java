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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiToolVOTest {

    @Test
    void builderDefaultsDescribeFreshTool() {
        AiToolVO vo = AiToolVO.builder().build();

        assertNull(vo.getName());
        assertNull(vo.getDescription());
        assertNull(vo.getParameters());
        assertNull(vo.getRiskLevel());
        assertNull(vo.getPermission());
        assertNull(vo.getRequiredCapabilities());
        assertFalse(vo.isDeprecated());
        assertNull(vo.getReplacement());
    }

    @Test
    void allArgsCarryToolMetadata() {
        AiToolVO vo = AiToolVO.builder()
            .name("rmq.cluster.list")
            .description("list clusters")
            .parameters("{\"type\":\"object\"}")
            .riskLevel("low")
            .permission("read")
            .requiredCapabilities(List.of("cluster:list"))
            .outputSchema("{\"type\":\"array\"}")
            .viewHint("table")
            .deprecated(true)
            .replacement("rmq.cluster.search")
            .build();

        assertEquals("rmq.cluster.list", vo.getName());
        assertEquals("{\"type\":\"object\"}", vo.getParameters());
        assertEquals("read", vo.getPermission());
        assertEquals(List.of("cluster:list"), vo.getRequiredCapabilities());
        assertTrue(vo.isDeprecated());
        assertEquals("rmq.cluster.search", vo.getReplacement());
    }
}
