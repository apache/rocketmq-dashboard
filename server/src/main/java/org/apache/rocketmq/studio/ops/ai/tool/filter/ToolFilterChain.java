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
package org.apache.rocketmq.studio.ops.ai.tool.filter;

import org.apache.rocketmq.studio.ops.ai.tool.core.ToolInvocation;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public final class ToolFilterChain {

    private final ToolExecutionFilter.Chain chain;

    public ToolFilterChain(List<ToolExecutionFilter> filters) {
        List<ToolExecutionFilter> ordered = filters.stream()
                .sorted(Comparator.comparing(ToolExecutionFilter::type))
                .toList();
        ToolExecutionFilter.Chain action = ToolInvocation::execute;
        for (ToolExecutionFilter filter : ordered.reversed()) {
            ToolExecutionFilter.Chain next = action;
            action = invocation -> filter.filter(invocation, next);
        }
        this.chain = action;
    }

    public Object execute(ToolInvocation invocation) {
        return chain.proceed(invocation);
    }
}
