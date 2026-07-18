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
package com.rocketmq.studio.common.domain;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ResultTest {

    @Test
    void okWithData_shouldReturnSuccessResultWithData() {
        String data = "hello";
        Result<String> result = Result.ok(data);

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertEquals("hello", result.getData());
    }

    @Test
    void okWithoutData_shouldReturnSuccessResultWithNullData() {
        Result<Void> result = Result.ok();

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void okWithNullData_shouldReturnSuccessResultWithNullData() {
        Result<String> result = Result.ok(null);

        assertEquals(200, result.getCode());
        assertEquals("success", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void error_shouldReturnErrorResultWithCodeAndMessage() {
        Result<?> result = Result.error(400, "Bad Request");

        assertEquals(400, result.getCode());
        assertEquals("Bad Request", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void errorWithDifferentCodes_shouldReturnCorrectCode() {
        Result<?> notFound = Result.error(404, "Not Found");
        Result<?> internal = Result.error(500, "Internal Server Error");

        assertEquals(404, notFound.getCode());
        assertEquals("Not Found", notFound.getMessage());
        assertEquals(500, internal.getCode());
        assertEquals("Internal Server Error", internal.getMessage());
    }

    @Test
    void okWithComplexObject_shouldReturnSuccessResultWithObject() {
        Map<String, Object> data = Map.of("key", "value", "count", 42);
        Result<Map<String, Object>> result = Result.ok(data);

        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("value", result.getData().get("key"));
        assertEquals(42, result.getData().get("count"));
    }
}