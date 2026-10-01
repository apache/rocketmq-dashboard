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

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueProgressVO {

    /**
     * Sentinel for an offset the provider cannot report, e.g. the per-topic lag rows of the cloud
     * providers, which carry no per-queue offsets at all. It matches the {@code -1} the broker uses
     * for an undeterminable lag, and the console renders a negative offset as unavailable instead
     * of a number that would read like a measurement.
     */
    public static final long UNKNOWN_OFFSET = -1L;

    private String topic;
    private String broker;
    private int queueId;
    private long brokerOffset;
    private long consumerOffset;
    private long diffTotal;
}
