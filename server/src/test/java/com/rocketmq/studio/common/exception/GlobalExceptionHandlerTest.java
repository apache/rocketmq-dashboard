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
package com.rocketmq.studio.common.exception;

import com.rocketmq.studio.common.domain.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void handleBusinessException_shouldReturnErrorResultWithCodeAndMessage() {
        BusinessException ex = new BusinessException(400, "Invalid parameter");

        Result<?> result = handler.handleBusinessException(ex);

        assertEquals(400, result.getCode());
        assertEquals("Invalid parameter", result.getMessage());
        assertNull(result.getData());
    }

    @Test
    void handleBusinessException_withDifferentCodes_shouldReturnCorrectCode() {
        BusinessException ex404 = new BusinessException(404, "Not Found");
        BusinessException ex500 = new BusinessException(500, "Server Error");

        Result<?> result404 = handler.handleBusinessException(ex404);
        Result<?> result500 = handler.handleBusinessException(ex500);

        assertEquals(404, result404.getCode());
        assertEquals("Not Found", result404.getMessage());
        assertEquals(500, result500.getCode());
        assertEquals("Server Error", result500.getMessage());
    }

    @Test
    void handleGenericException_shouldReturn500Error() {
        Exception ex = new RuntimeException("Something went wrong");

        Result<?> result = handler.handleException(ex);

        assertEquals(500, result.getCode());
        assertEquals("Internal Server Error", result.getMessage());
        assertNull(result.getData());
    }
}