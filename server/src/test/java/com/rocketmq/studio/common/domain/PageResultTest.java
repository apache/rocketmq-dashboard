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

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PageResultTest {

    @Test
    void of_shouldCreatePageResultWithAllFields() {
        List<String> items = Arrays.asList("a", "b", "c");
        PageResult<String> result = PageResult.of(items, 100, 1, 10);

        assertEquals(3, result.getItems().size());
        assertEquals(100, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    void empty_shouldCreateEmptyPageResult() {
        PageResult<String> result = PageResult.empty(1, 10);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotal());
        assertEquals(1, result.getPage());
        assertEquals(10, result.getSize());
    }

    @Test
    void of_withEmptyList_shouldCreatePageResultWithEmptyItems() {
        PageResult<String> result = PageResult.of(List.of(), 0, 1, 10);

        assertTrue(result.getItems().isEmpty());
        assertEquals(0, result.getTotal());
    }

    @Test
    void of_withZeroPageAndSize_shouldAcceptBoundaryValues() {
        List<Integer> items = List.of(1);
        PageResult<Integer> result = PageResult.of(items, 1, 0, 0);

        assertEquals(1, result.getItems().size());
        assertEquals(1, result.getTotal());
        assertEquals(0, result.getPage());
        assertEquals(0, result.getSize());
    }

    @Test
    void of_withLargeTotal_shouldHandleLargeValues() {
        PageResult<String> result = PageResult.of(List.of("x"), Long.MAX_VALUE, 1, 1);

        assertEquals(Long.MAX_VALUE, result.getTotal());
    }
}