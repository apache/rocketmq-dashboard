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

import java.util.Collections;
import java.util.List;

public class PageResult<T> {
    private List<T> items;
    private long total;
    private int page;
    private int size;

    private PageResult() {}

    public static <T> PageResult<T> of(List<T> items, long total, int page, int size) {
        PageResult<T> result = new PageResult<>();
        result.items = items;
        result.total = total;
        result.page = page;
        result.size = size;
        return result;
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return of(Collections.emptyList(), 0, page, size);
    }

    public List<T> getItems() { return items; }
    public long getTotal() { return total; }
    public int getPage() { return page; }
    public int getSize() { return size; }
}
