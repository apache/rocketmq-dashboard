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
package org.apache.rocketmq.studio.ops.ai.conversation.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Per-run statistic denormalised into the timeline envelope so a replayed transcript can show the
 * generation speed the client reported for each run. Mirrors the inline {@code runs} entry of the
 * TS {@code AiTimelineVO} in {@code web/src/api/aiEvents.ts}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiRunStatsVO {

    private Long id;

    /** Client-reported generation speed in tokens per second, or null when never reported. */
    private Double tokensPerSecond;
}
