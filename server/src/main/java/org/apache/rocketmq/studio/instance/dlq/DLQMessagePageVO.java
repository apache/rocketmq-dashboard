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
package org.apache.rocketmq.studio.instance.dlq;

import java.util.Collections;
import java.util.List;
import lombok.Getter;
import org.apache.rocketmq.studio.common.domain.PageResult;

/**
 * One page of the DLQ message drawer, plus the scan-boundary signals the group row cannot carry:
 * the server scan stops at a hard cap, so {@code total} may be far below the group's dead-letter
 * count, and unscannable queues hide their rows entirely. Without these flags the drawer silently
 * presents a capped window as if it were the whole DLQ.
 *
 * <p>The page fields mirror {@link PageResult} one for one so the JSON stays additive for existing
 * clients.
 */
@Getter
public class DLQMessagePageVO {
    private final List<DLQMessageVO> items;
    private final long total;
    private final int page;
    private final int size;
    private final boolean truncated;
    private final int failedQueueCount;

    private DLQMessagePageVO(List<DLQMessageVO> items, long total, int page, int size,
                             boolean truncated, int failedQueueCount) {
        this.items = items == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(items);
        this.total = total;
        this.page = page;
        this.size = size;
        this.truncated = truncated;
        this.failedQueueCount = failedQueueCount;
    }

    public static DLQMessagePageVO of(PageResult<DLQMessageVO> page, boolean truncated,
                                      int failedQueueCount) {
        return new DLQMessagePageVO(page.getItems(), page.getTotal(), page.getPage(), page.getSize(),
                truncated, failedQueueCount);
    }

    public static DLQMessagePageVO empty(int page, int size) {
        return new DLQMessagePageVO(Collections.emptyList(), 0, page, size, false, 0);
    }
}
