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
package org.apache.rocketmq.dashboard.skill;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkillResultTest {

    @Test
    public void testSuccessWithDataAndReturnType() {
        SkillResult result = SkillResult.success("payload", "TEXT");
        assertTrue(result.isSuccess());
        assertEquals("payload", result.getData());
        assertEquals("TEXT", result.getReturnType());
        assertNull(result.getErrorMessage());
    }

    @Test
    public void testSuccessList() {
        List<String> data = Arrays.asList("a", "b");
        SkillResult result = SkillResult.successList(data);
        assertTrue(result.isSuccess());
        assertEquals(data, result.getData());
        assertEquals("LIST", result.getReturnType());
    }

    @Test
    public void testSuccessObject() {
        Map<String, Object> data = Collections.singletonMap("k", "v");
        SkillResult result = SkillResult.successObject(data);
        assertTrue(result.isSuccess());
        assertEquals(data, result.getData());
        assertEquals("OBJECT", result.getReturnType());
    }

    @Test
    public void testSuccessText() {
        SkillResult result = SkillResult.successText("hello");
        assertTrue(result.isSuccess());
        assertEquals("hello", result.getData());
        assertEquals("TEXT", result.getReturnType());
    }

    @Test
    public void testSuccessVoid() {
        SkillResult result = SkillResult.successVoid();
        assertTrue(result.isSuccess());
        assertNull(result.getData());
        assertEquals("VOID", result.getReturnType());
    }

    @Test
    public void testFailure() {
        SkillResult result = SkillResult.failure("boom");
        assertFalse(result.isSuccess());
        assertEquals("boom", result.getErrorMessage());
        assertEquals("VOID", result.getReturnType());
        assertNull(result.getData());
    }

    @Test
    public void testBuilderWithMetadata() {
        Map<String, Object> metadata = Collections.singletonMap("elapsed", 12L);
        SkillResult result = SkillResult.builder()
                .success(true)
                .data("x")
                .returnType("TEXT")
                .metadata(metadata)
                .build();
        assertEquals(metadata, result.getMetadata());
        assertTrue(result.isSuccess());
    }
}
