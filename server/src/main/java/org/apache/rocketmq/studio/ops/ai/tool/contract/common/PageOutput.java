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
package org.apache.rocketmq.studio.ops.ai.tool.contract.common;

import org.apache.rocketmq.studio.common.domain.PageResult;

import java.util.List;
import java.util.function.Function;

public record PageOutput<T>(
        int page,
        int pageSize,
        long total,
        List<T> items) {

    public static <S, T> PageOutput<T> from(PageResult<S> page, Function<S, T> mapper) {
        return new PageOutput<>(
                page.getPage(),
                page.getSize(),
                page.getTotal(),
                page.getItems().stream().map(mapper).toList());
    }
}
