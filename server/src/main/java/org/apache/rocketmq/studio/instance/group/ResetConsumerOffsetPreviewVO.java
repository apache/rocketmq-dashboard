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
package org.apache.rocketmq.studio.instance.group;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetConsumerOffsetPreviewVO {
    private String instanceId;
    private String groupName;
    private String topic;
    private long timestamp;
    private boolean complete;
    private boolean allowReset;
    private int queueCount;
    private int warningCount;
    private int rewindQueueCount;
    private int fastForwardQueueCount;
    private long currentTotalLag;
    private long projectedTotalLag;
    private long totalOffsetDelta;
    private List<String> warnings;
    private List<ResetConsumerOffsetQueuePreviewVO> queues;
}
