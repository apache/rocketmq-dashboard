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
package org.apache.rocketmq.studio.common.util;

/** Pagination arithmetic shared by in-memory page slicing across providers. */
public final class Pagination {

    private Pagination() {
    }

    /**
     * Computes the zero-based offset of the requested page with long arithmetic, so very
     * large page numbers never wrap to a negative index. Products that would overflow a
     * {@code long} are capped at {@link Long#MAX_VALUE}; callers treat any offset at or
     * beyond their total element count as an empty page.
     */
    public static long pageOffset(long page, int pageSize) {
        long safePage = Math.max(page, 1L);
        long size = Math.max(pageSize, 0);
        if (size == 0) {
            return 0L;
        }
        if (safePage - 1 > Long.MAX_VALUE / size) {
            return Long.MAX_VALUE;
        }
        return (safePage - 1) * size;
    }
}
