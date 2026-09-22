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

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * One page of dead-letter messages plus the completeness metadata of the scan that produced it.
 *
 * <p>The page is sliced out of a scan that stops at {@link #limit} messages and tolerates queues it
 * could not read, so {@code total} is the size of a bounded snapshot rather than the size of the dead
 * letter queue. Without {@link #truncated} and {@link #failedQueueCount} a caller cannot tell a group
 * with exactly {@code total} messages from one whose remaining messages were never read.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DLQMessagePageVO {

    private List<DLQMessageVO> items;
    private long total;
    private int page;
    private int size;
    private boolean truncated;
    private int failedQueueCount;
    private int limit;
}
