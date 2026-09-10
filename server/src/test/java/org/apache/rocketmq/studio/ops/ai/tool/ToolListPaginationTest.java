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
package org.apache.rocketmq.studio.ops.ai.tool;

import org.apache.rocketmq.studio.common.domain.PageResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolListPaginationTest {

    @Test
    void pageShouldDefaultToOneWhenAbsent() {
        assertThat(ToolListPagination.page(Map.of())).isEqualTo(1);
        java.util.Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("page", null);
        assertThat(ToolListPagination.page(withNull)).isEqualTo(1);
    }

    @Test
    void pageSizeShouldDefaultToTwentyWhenAbsent() {
        assertThat(ToolListPagination.pageSize(Map.of())).isEqualTo(20);
        java.util.Map<String, Object> withNull = new java.util.HashMap<>();
        withNull.put("pageSize", null);
        assertThat(ToolListPagination.pageSize(withNull)).isEqualTo(20);
    }

    @Test
    void pageShouldAcceptPositiveNumbersOnly() {
        assertThat(ToolListPagination.page(Map.of("page", 3))).isEqualTo(3);
        assertThat(ToolListPagination.pageSize(Map.of("pageSize", 50))).isEqualTo(50);
    }

    @Test
    void pageShouldRejectNonPositiveValues() {
        assertThatThrownBy(() -> ToolListPagination.page(Map.of("page", 0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be positive");
        assertThatThrownBy(() -> ToolListPagination.page(Map.of("page", -2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("page must be positive");
        assertThatThrownBy(() -> ToolListPagination.pageSize(Map.of("pageSize", "abc")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pageSize must be positive");
    }

    @Test
    void pagedResultShouldExposeThePagingEnvelope() {
        PageResult<String> page = PageResult.of(List.of("a"), 31, 2, 10);
        List<Map<String, Object>> items = List.of(Map.of("name", "a"));

        Map<String, Object> result = ToolListPagination.pagedResult(page, items);

        assertThat(result).containsEntry("items", items)
                .containsEntry("total", 31L)
                .containsEntry("page", 2)
                .containsEntry("size", 10);
    }
}
