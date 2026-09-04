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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.rocketmq.studio.common.domain.PageResult;

/** Shared pagination helpers for list-style AI tool handlers. */
final class ToolListPagination {

    static final int DEFAULT_PAGE = 1;
    static final int DEFAULT_PAGE_SIZE = 20;

    private ToolListPagination() {}

    static Map<String, Object> pagedResult(PageResult<?> page, List<Map<String, Object>> items) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("items", items);
        result.put("total", page.getTotal());
        result.put("page", page.getPage());
        result.put("size", page.getSize());
        return result;
    }

    static int page(Map<String, Object> input) {
        return optionalPositiveInteger(input.get("page"), DEFAULT_PAGE, "page");
    }

    static int pageSize(Map<String, Object> input) {
        return optionalPositiveInteger(input.get("pageSize"), DEFAULT_PAGE_SIZE, "pageSize");
    }

    private static int optionalPositiveInteger(Object value, int defaultValue, String field) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            int result = number.intValue();
            if (result > 0) {
                return result;
            }
        }
        throw new IllegalArgumentException(field + " must be positive");
    }
}
