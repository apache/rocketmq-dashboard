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
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * Unit tests for {@link ToolListPagination}, the shared page/pageSize parsing and result
 * envelope used by list-style AI tool handlers.
 */
class ToolListPaginationTest {

    @Test
    void defaultsPageAndPageSizeWhenAbsent() {
        assertThat(ToolListPagination.page(Map.of())).isEqualTo(1);
        assertThat(ToolListPagination.pageSize(Map.of())).isEqualTo(20);
        assertThat(ToolListPagination.page(Map.of("search", "x"))).isEqualTo(1);
        assertThat(ToolListPagination.pageSize(Map.of("search", "x"))).isEqualTo(20);
    }

    @Test
    void readsPositiveIntegerValues() {
        assertThat(ToolListPagination.page(Map.of("page", 3))).isEqualTo(3);
        assertThat(ToolListPagination.page(Map.of("page", 7L))).isEqualTo(7);
        assertThat(ToolListPagination.pageSize(Map.of("pageSize", 50))).isEqualTo(50);
        assertThat(ToolListPagination.page(Map.of("page", 1, "pageSize", 20))).isEqualTo(1);
    }

    @Test
    void rejectsNonPositiveAndNonNumericValues() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ToolListPagination.page(Map.of("page", 0)))
                .withMessage("page must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ToolListPagination.page(Map.of("page", -5)))
                .withMessage("page must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ToolListPagination.pageSize(Map.of("pageSize", 0)))
                .withMessage("pageSize must be positive");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> ToolListPagination.page(Map.of("page", "abc")))
                .withMessage("page must be positive");
    }

    @Test
    void assemblesThePagedResultEnvelope() {
        List<Map<String, Object>> items = List.of(Map.of("id", "1"));
        PageResult<?> page = PageResult.of(items, 42L, 2, 20);

        Map<String, Object> result = ToolListPagination.pagedResult(page, items);

        assertThat(result.get("items")).isSameAs(items);
        assertThat(result.get("total")).isEqualTo(42L);
        assertThat(result.get("page")).isEqualTo(2);
        assertThat(result.get("size")).isEqualTo(20);
    }
}
